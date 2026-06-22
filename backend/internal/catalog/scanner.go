package catalog

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"gorm.io/gorm"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
	"shuuen-backend/internal/storage"
	"shuuen-backend/internal/util"
)

type Scanner struct {
	db                   *gorm.DB
	root                 string
	folderMetadataFile   string
	melodyMetadataSuffix string
}

type Result struct {
	ScanID        string `json:"scan_id"`
	GroupsIndexed int    `json:"groups_indexed"`
	MelodiesFound int    `json:"melodies_found"`
	VariantsFound int    `json:"variants_found"`
}

type FolderMetadata struct {
	Name        string   `json:"name"`
	Description string   `json:"description"`
	Tags        []string `json:"tags"`
	SortOrder   int      `json:"sort_order"`
	IsActive    *bool    `json:"is_active"`
}

type MelodyMetadata struct {
	Title         string   `json:"title"`
	Description   string   `json:"description"`
	Composer      string   `json:"composer"`
	Difficulty    string   `json:"difficulty"`
	Tags          []string `json:"tags"`
	SortOrder     int      `json:"sort_order"`
	IsPublished   *bool    `json:"is_published"`
	PrimaryFormat string   `json:"primary_format"`
}

func NewScanner(db *gorm.DB, cfg config.CatalogConfig) (*Scanner, error) {
	root, err := filepath.Abs(cfg.Root)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(root, 0o755); err != nil {
		return nil, err
	}
	return &Scanner{
		db:                   db,
		root:                 root,
		folderMetadataFile:   cfg.FolderMetadataFile,
		melodyMetadataSuffix: cfg.MelodyMetadataSuffix,
	}, nil
}

func (s *Scanner) Scan(ctx context.Context) (Result, error) {
	scanID := time.Now().UTC().Format("20060102150405.000000000")
	result := Result{ScanID: scanID}
	seenMelodies := map[string]struct{}{}

	err := s.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		groupsByPath := map[string]model.LibraryGroup{}

		err := filepath.WalkDir(s.root, func(absPath string, entry fs.DirEntry, walkErr error) error {
			if walkErr != nil {
				return walkErr
			}
			if err := ctx.Err(); err != nil {
				return err
			}

			if entry.IsDir() {
				if entry.Name() != "." && strings.HasPrefix(entry.Name(), ".") && absPath != s.root {
					return filepath.SkipDir
				}
				group, err := s.indexGroup(tx, absPath, groupsByPath, scanID)
				if err != nil {
					return err
				}
				groupsByPath[group.Path] = group
				result.GroupsIndexed++
				return nil
			}

			if storage.InferFormat(entry.Name()) == "" {
				return nil
			}

			melody, _, err := s.indexMelodyForFile(tx, absPath, groupsByPath, scanID)
			if err != nil {
				return err
			}
			if _, ok := seenMelodies[melody.SourcePath]; !ok {
				result.MelodiesFound++
				seenMelodies[melody.SourcePath] = struct{}{}
			}
			if err := s.indexVariant(tx, absPath, melody, scanID); err != nil {
				return err
			}
			result.VariantsFound++
			return nil
		})
		if err != nil {
			return err
		}

		return cleanupStale(tx, scanID)
	})
	if err != nil {
		return Result{}, err
	}

	return result, nil
}

func (s *Scanner) indexGroup(tx *gorm.DB, absPath string, groupsByPath map[string]model.LibraryGroup, scanID string) (model.LibraryGroup, error) {
	relPath, err := relativeSlashPath(s.root, absPath)
	if err != nil {
		return model.LibraryGroup{}, err
	}

	meta := FolderMetadata{}
	_ = readJSON(filepath.Join(absPath, s.folderMetadataFile), &meta)

	name := strings.TrimSpace(meta.Name)
	if name == "" {
		if relPath == "" {
			name = "Library"
		} else {
			name = filepath.Base(absPath)
		}
	}
	isActive := true
	if meta.IsActive != nil {
		isActive = *meta.IsActive
	}

	var parentID *uint
	if relPath != "" {
		parentPath := filepath.ToSlash(path.Dir(relPath))
		if parentPath == "." {
			parentPath = ""
		}
		parent, ok := groupsByPath[parentPath]
		if !ok {
			return model.LibraryGroup{}, errors.New("parent group was not indexed")
		}
		parentID = &parent.ID
	}

	group := model.LibraryGroup{}
	err = tx.Where("path = ?", relPath).First(&group).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		group = model.LibraryGroup{Path: relPath}
	} else if err != nil {
		return model.LibraryGroup{}, err
	}

	group.ParentID = parentID
	group.Name = name
	group.Slug = util.Slugify(path.Base(relPath))
	if relPath == "" {
		group.Slug = "library"
	}
	group.Description = strings.TrimSpace(meta.Description)
	group.SortOrder = meta.SortOrder
	group.IsActive = isActive
	group.ScanID = scanID

	if group.ID == 0 {
		if err := tx.Create(&group).Error; err != nil {
			return model.LibraryGroup{}, err
		}
	} else {
		if err := tx.Save(&group).Error; err != nil {
			return model.LibraryGroup{}, err
		}
	}

	tags, err := loadOrCreateTags(tx, meta.Tags)
	if err != nil {
		return model.LibraryGroup{}, err
	}
	if err := tx.Model(&group).Association("Tags").Replace(tags); err != nil {
		return model.LibraryGroup{}, err
	}

	return group, nil
}

func (s *Scanner) indexMelodyForFile(tx *gorm.DB, absPath string, groupsByPath map[string]model.LibraryGroup, scanID string) (model.Melody, bool, error) {
	dir := filepath.Dir(absPath)
	groupPath, err := relativeSlashPath(s.root, dir)
	if err != nil {
		return model.Melody{}, false, err
	}
	group, ok := groupsByPath[groupPath]
	if !ok {
		return model.Melody{}, false, errors.New("file parent group was not indexed")
	}

	fileName := filepath.Base(absPath)
	ext := filepath.Ext(fileName)
	fileStem := strings.TrimSuffix(fileName, ext)
	sourcePath := path.Join(groupPath, fileStem)
	if groupPath == "" {
		sourcePath = fileStem
	}

	meta := MelodyMetadata{}
	_ = readJSON(filepath.Join(dir, fileStem+s.melodyMetadataSuffix), &meta)

	title := strings.TrimSpace(meta.Title)
	if title == "" {
		title = humanTitle(fileStem)
	}
	isPublished := true
	if meta.IsPublished != nil {
		isPublished = *meta.IsPublished
	}

	melody := model.Melody{}
	err = tx.Where("source_path = ?", sourcePath).First(&melody).Error
	created := false
	if errors.Is(err, gorm.ErrRecordNotFound) {
		melody = model.Melody{SourcePath: sourcePath}
		created = true
	} else if err != nil {
		return model.Melody{}, false, err
	}

	melody.GroupID = group.ID
	melody.FileStem = fileStem
	melody.Title = title
	melody.Slug = util.Slugify(fileStem)
	melody.Description = strings.TrimSpace(meta.Description)
	melody.Composer = strings.TrimSpace(meta.Composer)
	melody.Difficulty = strings.TrimSpace(meta.Difficulty)
	melody.SortOrder = meta.SortOrder
	melody.IsPublished = isPublished
	melody.ScanID = scanID

	if melody.ID == 0 {
		if err := tx.Create(&melody).Error; err != nil {
			return model.Melody{}, false, err
		}
	} else {
		if err := tx.Save(&melody).Error; err != nil {
			return model.Melody{}, false, err
		}
	}

	tags, err := loadOrCreateTags(tx, meta.Tags)
	if err != nil {
		return model.Melody{}, false, err
	}
	if err := tx.Model(&melody).Association("Tags").Replace(tags); err != nil {
		return model.Melody{}, false, err
	}

	return melody, created, nil
}

func (s *Scanner) indexVariant(tx *gorm.DB, absPath string, melody model.Melody, scanID string) error {
	relPath, err := relativeSlashPath(s.root, absPath)
	if err != nil {
		return err
	}

	fileName := filepath.Base(absPath)
	ext := filepath.Ext(fileName)
	format := storage.InferFormat(fileName)
	checksum, size, err := checksumAndSize(absPath)
	if err != nil {
		return err
	}

	meta := MelodyMetadata{}
	_ = readJSON(filepath.Join(filepath.Dir(absPath), melody.FileStem+s.melodyMetadataSuffix), &meta)
	primaryFormat := storage.NormalizeFormat(meta.PrimaryFormat)

	variant := model.FileVariant{}
	err = tx.Where("storage_path = ?", relPath).First(&variant).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		variant = model.FileVariant{StoragePath: relPath}
	} else if err != nil {
		return err
	}

	variant.MelodyID = melody.ID
	variant.Format = format
	variant.OriginalName = fileName
	variant.StoredName = fileName
	variant.MimeType = storage.MimeTypeForExtension(ext)
	variant.SizeBytes = size
	variant.ChecksumSHA = checksum
	variant.IsPrimary = primaryFormat == format || (primaryFormat == "" && format == "midi")
	variant.ScanID = scanID

	if variant.ID == 0 {
		return tx.Create(&variant).Error
	}
	return tx.Save(&variant).Error
}

func cleanupStale(tx *gorm.DB, scanID string) error {
	staleVariantQuery := tx.Where("scan_id <> ? OR scan_id = '' OR scan_id IS NULL", scanID)
	if err := staleVariantQuery.Delete(&model.FileVariant{}).Error; err != nil {
		return err
	}

	var staleMelodies []model.Melody
	if err := tx.Where("scan_id <> ? OR scan_id = '' OR scan_id IS NULL", scanID).Find(&staleMelodies).Error; err != nil {
		return err
	}
	for _, melody := range staleMelodies {
		if err := tx.Model(&melody).Association("Tags").Clear(); err != nil {
			return err
		}
	}
	if len(staleMelodies) > 0 {
		if err := tx.Delete(&staleMelodies).Error; err != nil {
			return err
		}
	}

	var staleGroups []model.LibraryGroup
	if err := tx.Where("scan_id <> ? OR scan_id = '' OR scan_id IS NULL", scanID).Find(&staleGroups).Error; err != nil {
		return err
	}
	sort.Slice(staleGroups, func(i, j int) bool {
		return len(staleGroups[i].Path) > len(staleGroups[j].Path)
	})
	for _, group := range staleGroups {
		if err := tx.Model(&group).Association("Tags").Clear(); err != nil {
			return err
		}
		if err := tx.Delete(&group).Error; err != nil {
			return err
		}
	}

	return nil
}

func loadOrCreateTags(tx *gorm.DB, names []string) ([]model.Tag, error) {
	seen := map[string]struct{}{}
	tags := make([]model.Tag, 0, len(names))

	for _, name := range names {
		name = strings.TrimSpace(name)
		if name == "" {
			continue
		}
		slug := util.Slugify(name)
		if _, ok := seen[slug]; ok {
			continue
		}
		seen[slug] = struct{}{}

		tag := model.Tag{}
		err := tx.Where("slug = ?", slug).First(&tag).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			tag = model.Tag{Name: name, Slug: slug}
			if err := tx.Create(&tag).Error; err != nil {
				return nil, err
			}
		} else if err != nil {
			return nil, err
		}
		tags = append(tags, tag)
	}

	return tags, nil
}

func readJSON(file string, out any) error {
	handle, err := os.Open(file)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	defer handle.Close()
	return json.NewDecoder(handle).Decode(out)
}

func relativeSlashPath(root string, absolute string) (string, error) {
	rel, err := filepath.Rel(root, absolute)
	if err != nil {
		return "", err
	}
	if rel == "." {
		return "", nil
	}
	return filepath.ToSlash(rel), nil
}

func checksumAndSize(file string) (string, int64, error) {
	handle, err := os.Open(file)
	if err != nil {
		return "", 0, err
	}
	defer handle.Close()

	hash := sha256.New()
	size, err := io.Copy(hash, handle)
	if err != nil {
		return "", 0, err
	}
	return hex.EncodeToString(hash.Sum(nil)), size, nil
}

func humanTitle(stem string) string {
	stem = strings.TrimSpace(stem)
	stem = strings.ReplaceAll(stem, "_", " ")
	stem = strings.ReplaceAll(stem, "-", " ")
	if stem == "" {
		return "Untitled"
	}
	return stem
}
