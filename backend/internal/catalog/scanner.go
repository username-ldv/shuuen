package catalog

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

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
	scanMu               sync.Mutex
}

var ErrScanInProgress = errors.New("catalog scan is already in progress")

type scanState struct {
	groups          map[string]model.LibraryGroup
	melodies        map[string]model.Melody
	variants        map[string]model.FileVariant
	primaryByMelody map[uint]uint
	primaryAssigned map[uint]bool
	demotedVariants map[uint]bool
	groupIDs        []uint
	melodyIDs       []uint
	variantIDs      []uint
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
	IsPublic    *bool    `json:"is_public"`
	IsActive    *bool    `json:"is_active,omitempty"`
}

type MelodyMetadata struct {
	Title         string   `json:"title"`
	Description   string   `json:"description"`
	Composer      string   `json:"composer"`
	Difficulty    string   `json:"difficulty"`
	Tags          []string `json:"tags"`
	SortOrder     int      `json:"sort_order"`
	IsPublic      *bool    `json:"is_public"`
	IsPublished   *bool    `json:"is_published,omitempty"`
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
	if !s.scanMu.TryLock() {
		return Result{}, ErrScanInProgress
	}
	defer s.scanMu.Unlock()

	scanID := time.Now().UTC().Format("20060102150405.000000000")
	result := Result{ScanID: scanID}

	err := s.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		state, err := loadScanState(tx)
		if err != nil {
			return err
		}
		groupsByPath := map[string]model.LibraryGroup{}
		indexedMelodies := map[string]model.Melody{}

		err = filepath.WalkDir(s.root, func(absPath string, entry fs.DirEntry, walkErr error) error {
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
				group, err := s.indexGroup(tx, absPath, groupsByPath, state, scanID)
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

			melody, created, err := s.indexMelodyForFile(tx, absPath, groupsByPath, indexedMelodies, state, scanID)
			if err != nil {
				return err
			}
			if created {
				result.MelodiesFound++
			}
			if err := s.indexVariant(tx, absPath, melody, state, scanID); err != nil {
				return err
			}
			result.VariantsFound++
			return nil
		})
		if err != nil {
			return err
		}
		result.MelodiesFound = len(indexedMelodies)
		if err := markSeen(tx, &model.LibraryGroup{}, state.groupIDs, scanID); err != nil {
			return err
		}
		if err := markSeen(tx, &model.Melody{}, state.melodyIDs, scanID); err != nil {
			return err
		}
		if err := markSeen(tx, &model.FileVariant{}, state.variantIDs, scanID); err != nil {
			return err
		}

		return cleanupStale(tx, scanID)
	})
	if err != nil {
		return Result{}, err
	}

	return result, nil
}

func (s *Scanner) indexGroup(tx *gorm.DB, absPath string, groupsByPath map[string]model.LibraryGroup, state *scanState, scanID string) (model.LibraryGroup, error) {
	relPath, err := relativeSlashPath(s.root, absPath)
	if err != nil {
		return model.LibraryGroup{}, err
	}

	meta := FolderMetadata{}
	metadataPath := filepath.Join(absPath, s.folderMetadataFile)
	if err := readJSON(metadataPath, &meta); err != nil {
		return model.LibraryGroup{}, fmt.Errorf("read group metadata %s: %w", metadataPath, err)
	}

	name := strings.TrimSpace(meta.Name)
	if name == "" {
		if relPath == "" {
			name = "Library"
		} else {
			name = filepath.Base(absPath)
		}
	}
	isPublic := publicValue(meta.IsPublic, meta.IsActive)

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
		if !parent.IsPublic {
			isPublic = false
		}
	}

	group, found := state.groups[relPath]
	if !found {
		group = model.LibraryGroup{Path: relPath}
	}
	previous := group

	group.ParentID = parentID
	group.Name = name
	group.Slug = util.Slugify(path.Base(relPath))
	if relPath == "" {
		group.Slug = "library"
	}
	group.Description = strings.TrimSpace(meta.Description)
	group.SortOrder = meta.SortOrder
	group.IsPublic = isPublic
	group.ScanID = scanID
	group.DeletedAt = gorm.DeletedAt{}
	if err := validateLengths(
		lengthField{"group path", group.Path, 640},
		lengthField{"group name", group.Name, 180},
		lengthField{"group slug", group.Slug, 220},
	); err != nil {
		return model.LibraryGroup{}, err
	}

	if group.ID == 0 {
		if err := tx.Omit("Parent", "Tags", "Children", "Melodies").Create(&group).Error; err != nil {
			return model.LibraryGroup{}, err
		}
	} else if previous.DeletedAt.Valid || !sameGroup(previous, group) {
		if err := tx.Unscoped().Omit("Parent", "Tags", "Children", "Melodies").Save(&group).Error; err != nil {
			return model.LibraryGroup{}, err
		}
	}

	tags, err := loadOrCreateTags(tx, meta.Tags)
	if err != nil {
		return model.LibraryGroup{}, err
	}
	if previous.ID == 0 || previous.DeletedAt.Valid || !sameTagSet(previous.Tags, tags) {
		if err := tx.Model(&group).Association("Tags").Replace(tags); err != nil {
			return model.LibraryGroup{}, err
		}
	}
	group.Tags = tags
	state.groups[relPath] = group
	state.groupIDs = append(state.groupIDs, group.ID)

	return group, nil
}

func (s *Scanner) indexMelodyForFile(tx *gorm.DB, absPath string, groupsByPath map[string]model.LibraryGroup, indexed map[string]model.Melody, state *scanState, scanID string) (model.Melody, bool, error) {
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
	if melody, ok := indexed[sourcePath]; ok {
		return melody, false, nil
	}

	meta := MelodyMetadata{}
	metadataPath := filepath.Join(dir, fileStem+s.melodyMetadataSuffix)
	if err := readJSON(metadataPath, &meta); err != nil {
		return model.Melody{}, false, fmt.Errorf("read melody metadata %s: %w", metadataPath, err)
	}

	title := strings.TrimSpace(meta.Title)
	if title == "" {
		title = humanTitle(fileStem)
	}
	isPublic := publicValue(meta.IsPublic, meta.IsPublished)
	if !group.IsPublic {
		isPublic = false
	}

	melody, found := state.melodies[sourcePath]
	created := !found
	if !found {
		melody = model.Melody{SourcePath: sourcePath}
	}
	previous := melody

	melody.GroupID = group.ID
	melody.FileStem = fileStem
	melody.Title = title
	melody.Slug = util.Slugify(fileStem)
	melody.Description = strings.TrimSpace(meta.Description)
	melody.Composer = strings.TrimSpace(meta.Composer)
	melody.Difficulty = strings.TrimSpace(meta.Difficulty)
	melody.SortOrder = meta.SortOrder
	melody.IsPublic = isPublic
	melody.ScanID = scanID
	melody.DeletedAt = gorm.DeletedAt{}
	if err := validateLengths(
		lengthField{"melody source path", melody.SourcePath, 640},
		lengthField{"melody file stem", melody.FileStem, 260},
		lengthField{"melody title", melody.Title, 220},
		lengthField{"melody slug", melody.Slug, 260},
		lengthField{"melody composer", melody.Composer, 180},
		lengthField{"melody difficulty", melody.Difficulty, 80},
	); err != nil {
		return model.Melody{}, false, err
	}

	if melody.ID == 0 {
		if err := tx.Omit("Group", "Tags", "Variants").Create(&melody).Error; err != nil {
			return model.Melody{}, false, err
		}
	} else if previous.DeletedAt.Valid || !sameMelody(previous, melody) {
		if err := tx.Unscoped().Omit("Group", "Tags", "Variants").Save(&melody).Error; err != nil {
			return model.Melody{}, false, err
		}
	}

	tags, err := loadOrCreateTags(tx, meta.Tags)
	if err != nil {
		return model.Melody{}, false, err
	}
	if previous.ID == 0 || previous.DeletedAt.Valid || !sameTagSet(previous.Tags, tags) {
		if err := tx.Model(&melody).Association("Tags").Replace(tags); err != nil {
			return model.Melody{}, false, err
		}
	}
	melody.Tags = tags
	state.melodies[sourcePath] = melody
	state.melodyIDs = append(state.melodyIDs, melody.ID)
	indexed[sourcePath] = melody

	return melody, created, nil
}

func (s *Scanner) indexVariant(tx *gorm.DB, absPath string, melody model.Melody, state *scanState, scanID string) error {
	relPath, err := relativeSlashPath(s.root, absPath)
	if err != nil {
		return err
	}

	fileName := filepath.Base(absPath)
	ext := filepath.Ext(fileName)
	format := storage.InferFormat(fileName)
	info, err := os.Stat(absPath)
	if err != nil {
		return err
	}

	meta := MelodyMetadata{}
	metadataPath := filepath.Join(filepath.Dir(absPath), melody.FileStem+s.melodyMetadataSuffix)
	if err := readJSON(metadataPath, &meta); err != nil {
		return fmt.Errorf("read melody metadata %s: %w", metadataPath, err)
	}
	primaryFormat := storage.NormalizeFormat(meta.PrimaryFormat)

	variant, found := state.variants[relPath]
	if !found {
		variant = model.FileVariant{StoragePath: relPath}
	}
	previous := variant
	if state.demotedVariants[variant.ID] {
		variant.IsPrimary = false
	}
	checksum := variant.ChecksumSHA
	size := info.Size()
	modTime := info.ModTime().UnixNano()
	if variant.ID == 0 || checksum == "" || variant.SizeBytes != size || variant.FileModTime != modTime {
		checksum, size, err = checksumAndSize(absPath)
		if err != nil {
			return err
		}
	}

	variant.MelodyID = melody.ID
	variant.Format = format
	variant.OriginalName = fileName
	variant.StoredName = fileName
	variant.MimeType = storage.MimeTypeForExtension(ext)
	variant.SizeBytes = size
	variant.FileModTime = modTime
	variant.ChecksumSHA = checksum
	wantsPrimary := primaryFormat == format || primaryFormat == ""
	if wantsPrimary && !state.primaryAssigned[melody.ID] {
		state.primaryAssigned[melody.ID] = true
		oldPrimaryID := state.primaryByMelody[melody.ID]
		if oldPrimaryID != 0 && oldPrimaryID != variant.ID {
			if err := tx.Model(&model.FileVariant{}).Where("id = ?", oldPrimaryID).Update("is_primary", false).Error; err != nil {
				return err
			}
			state.demotedVariants[oldPrimaryID] = true
		}
		variant.IsPrimary = true
	}
	variant.ScanID = scanID
	variant.DeletedAt = gorm.DeletedAt{}
	if err := validateLengths(
		lengthField{"variant storage path", variant.StoragePath, 640},
		lengthField{"variant original name", variant.OriginalName, 255},
		lengthField{"variant stored name", variant.StoredName, 255},
		lengthField{"variant MIME type", variant.MimeType, 120},
	); err != nil {
		return err
	}

	if variant.ID == 0 {
		if err := tx.Omit("Melody").Create(&variant).Error; err != nil {
			return err
		}
	} else if previous.DeletedAt.Valid || !sameVariant(previous, variant) {
		if err := tx.Unscoped().Omit("Melody").Save(&variant).Error; err != nil {
			return err
		}
	}
	state.variants[relPath] = variant
	state.variantIDs = append(state.variantIDs, variant.ID)
	return nil
}

func loadScanState(tx *gorm.DB) (*scanState, error) {
	state := &scanState{
		groups:          map[string]model.LibraryGroup{},
		melodies:        map[string]model.Melody{},
		variants:        map[string]model.FileVariant{},
		primaryByMelody: map[uint]uint{},
		primaryAssigned: map[uint]bool{},
		demotedVariants: map[uint]bool{},
	}
	var groups []model.LibraryGroup
	if err := tx.Unscoped().Preload("Tags").Find(&groups).Error; err != nil {
		return nil, err
	}
	for _, group := range groups {
		state.groups[group.Path] = group
	}
	var melodies []model.Melody
	if err := tx.Unscoped().Preload("Tags").Find(&melodies).Error; err != nil {
		return nil, err
	}
	for _, melody := range melodies {
		state.melodies[melody.SourcePath] = melody
	}
	var variants []model.FileVariant
	if err := tx.Unscoped().Find(&variants).Error; err != nil {
		return nil, err
	}
	for _, variant := range variants {
		state.variants[variant.StoragePath] = variant
		if variant.IsPrimary && !variant.DeletedAt.Valid {
			state.primaryByMelody[variant.MelodyID] = variant.ID
		}
	}
	return state, nil
}

func markSeen(tx *gorm.DB, value any, ids []uint, scanID string) error {
	const batchSize = 500
	for start := 0; start < len(ids); start += batchSize {
		end := min(start+batchSize, len(ids))
		if err := tx.Unscoped().Model(value).Where("id IN ?", ids[start:end]).UpdateColumn("scan_id", scanID).Error; err != nil {
			return err
		}
	}
	return nil
}

func sameGroup(left model.LibraryGroup, right model.LibraryGroup) bool {
	return sameUintPointer(left.ParentID, right.ParentID) &&
		left.Path == right.Path && left.Name == right.Name && left.Slug == right.Slug &&
		left.Description == right.Description && left.SortOrder == right.SortOrder && left.IsPublic == right.IsPublic
}

func sameMelody(left model.Melody, right model.Melody) bool {
	return left.GroupID == right.GroupID && left.SourcePath == right.SourcePath && left.FileStem == right.FileStem &&
		left.Title == right.Title && left.Slug == right.Slug && left.Description == right.Description &&
		left.Composer == right.Composer && left.Difficulty == right.Difficulty && left.SortOrder == right.SortOrder &&
		left.IsPublic == right.IsPublic
}

func sameVariant(left model.FileVariant, right model.FileVariant) bool {
	return left.MelodyID == right.MelodyID && left.Format == right.Format && left.OriginalName == right.OriginalName &&
		left.StoredName == right.StoredName && left.StoragePath == right.StoragePath && left.MimeType == right.MimeType &&
		left.SizeBytes == right.SizeBytes && left.FileModTime == right.FileModTime && left.ChecksumSHA == right.ChecksumSHA &&
		left.IsPrimary == right.IsPrimary
}

func sameUintPointer(left *uint, right *uint) bool {
	if left == nil || right == nil {
		return left == nil && right == nil
	}
	return *left == *right
}

func sameTagSet(left []model.Tag, right []model.Tag) bool {
	if len(left) != len(right) {
		return false
	}
	ids := make(map[uint]struct{}, len(left))
	for _, tag := range left {
		ids[tag.ID] = struct{}{}
	}
	for _, tag := range right {
		if _, ok := ids[tag.ID]; !ok {
			return false
		}
	}
	return true
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
		if err := validateLengths(lengthField{"tag name", name, 80}, lengthField{"tag slug", slug, 100}); err != nil {
			return nil, err
		}
		if _, ok := seen[slug]; ok {
			continue
		}
		seen[slug] = struct{}{}

		tag := model.Tag{}
		err := tx.Unscoped().Where("slug = ?", slug).First(&tag).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			tag = model.Tag{Name: name, Slug: slug}
			if err := tx.Create(&tag).Error; err != nil {
				return nil, err
			}
		} else if err != nil {
			return nil, err
		}
		if tag.DeletedAt.Valid {
			tag.DeletedAt = gorm.DeletedAt{}
			tag.Name = name
			if err := tx.Unscoped().Save(&tag).Error; err != nil {
				return nil, err
			}
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
	decoder := json.NewDecoder(handle)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(out); err != nil {
		return err
	}
	var trailing any
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		if err == nil {
			return errors.New("metadata must contain exactly one JSON value")
		}
		return err
	}
	return nil
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

func publicValue(current *bool, legacy *bool) bool {
	if current != nil {
		return *current
	}
	if legacy != nil {
		return *legacy
	}
	return true
}

type lengthField struct {
	name  string
	value string
	max   int
}

func validateLengths(fields ...lengthField) error {
	for _, field := range fields {
		if utf8.RuneCountInString(field.value) > field.max {
			return fmt.Errorf("%s exceeds %d characters", field.name, field.max)
		}
	}
	return nil
}
