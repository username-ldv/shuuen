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
	dbquery "shuuen-backend/internal/query"
	"shuuen-backend/internal/storage"
	"shuuen-backend/internal/util"
)

// recentChangeSkew keeps a directory out of the incremental fast path while its
// modification time is this fresh. Filesystem clocks are coarse - about 16 ms on
// Windows - so a file added within the same tick as the scan would otherwise stay
// invisible until something else in that directory changed.
const recentChangeSkew = 2 * time.Second

type Scanner struct {
	db                   *gorm.DB
	root                 string
	folderMetadataFile   string
	melodyMetadataSuffix string
	recentChangeSkew     time.Duration
	scanMu               sync.Mutex
}

var ErrScanInProgress = errors.New("catalog scan is already in progress")

type scanState struct {
	groups          map[string]model.LibraryGroup
	melodies        map[string]model.Melody
	variants        map[string]model.FileVariant
	childPaths      map[string][]string
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
	GroupsScanned int    `json:"groups_scanned"`
	Full          bool   `json:"full"`
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
	SortOrder     *int     `json:"sort_order"`
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
		recentChangeSkew:     recentChangeSkew,
	}, nil
}

// Scan reconciles the catalog incrementally. A directory whose modification time
// still matches the indexed one is never read, so the cost follows the number of
// directories instead of the number of files. Adding, removing or renaming a file
// changes its directory's modification time and is picked up; editing a file in
// place does not, so that only becomes visible through ScanFull.
func (s *Scanner) Scan(ctx context.Context) (Result, error) {
	return s.scan(ctx, false)
}

// ScanFull reads every directory, however recently it changed.
func (s *Scanner) ScanFull(ctx context.Context) (Result, error) {
	return s.scan(ctx, true)
}

func (s *Scanner) scan(ctx context.Context, full bool) (Result, error) {
	if !s.scanMu.TryLock() {
		return Result{}, ErrScanInProgress
	}
	defer s.scanMu.Unlock()

	scanID := time.Now().UTC().Format("20060102150405.000000000")
	result := Result{ScanID: scanID, Full: full}

	err := s.db.Transaction(func(tx *gorm.DB) error {
		state, err := loadScanState(ctx, tx)
		if err != nil {
			return err
		}

		seenGroups := map[uint]bool{}
		scannedGroups := []uint{}

		var visit func(absPath string, relPath string, parent *model.LibraryGroup, force bool) error
		visit = func(absPath string, relPath string, parent *model.LibraryGroup, force bool) error {
			if err := ctx.Err(); err != nil {
				return err
			}
			info, err := os.Stat(absPath)
			if errors.Is(err, os.ErrNotExist) {
				// The folder is gone; cleanup removes whatever it held.
				return nil
			}
			if err != nil {
				return err
			}

			known, isKnown := state.groups[relPath]
			if !full && !force && isKnown && s.unchangedDirectory(absPath, known, info) {
				seenGroups[known.ID] = true
				for _, childPath := range state.childPaths[relPath] {
					childAbs := filepath.Join(s.root, filepath.FromSlash(childPath))
					if err := visit(childAbs, childPath, &known, false); err != nil {
						return err
					}
				}
				return nil
			}

			entries, err := os.ReadDir(absPath)
			if err != nil {
				return err
			}
			meta, metaModTime, err := s.readFolderMetadata(absPath, entries)
			if err != nil {
				return err
			}
			group, changed, err := s.indexGroup(ctx, tx, groupInput{
				relPath:     relPath,
				absPath:     absPath,
				meta:        meta,
				parent:      parent,
				dirModTime:  s.recordedModTime(info.ModTime(), metaModTime),
				metaModTime: metaModTime,
			}, state, scanID)
			if err != nil {
				return err
			}
			seenGroups[group.ID] = true
			scannedGroups = append(scannedGroups, group.ID)
			result.GroupsScanned++

			if err := s.indexDirectoryFiles(ctx, tx, absPath, relPath, group, entries, state, scanID); err != nil {
				return err
			}

			for _, entry := range entries {
				if !entry.IsDir() || strings.HasPrefix(entry.Name(), ".") {
					continue
				}
				if err := visit(filepath.Join(absPath, entry.Name()), joinRelPath(relPath, entry.Name()), &group, changed); err != nil {
					return err
				}
			}
			return nil
		}

		if err := visit(s.root, "", nil, false); err != nil {
			return err
		}

		if err := markSeen[model.LibraryGroup](ctx, tx, state.groupIDs, scanID); err != nil {
			return err
		}
		if err := markSeen[model.Melody](ctx, tx, state.melodyIDs, scanID); err != nil {
			return err
		}
		if err := markSeen[model.FileVariant](ctx, tx, state.variantIDs, scanID); err != nil {
			return err
		}
		if err := cleanupStale(ctx, tx, scanID, scannedGroups, missingGroups(state, seenGroups)); err != nil {
			return err
		}
		return countCatalog(ctx, tx, &result)
	})
	if err != nil {
		return Result{}, err
	}
	if s.db.Dialector.Name() == "sqlite" {
		if err := gorm.G[any](s.db).Exec(ctx, "PRAGMA optimize"); err != nil {
			return Result{}, fmt.Errorf("optimize catalog query planner: %w", err)
		}
	}

	return result, nil
}

// unchangedDirectory reports whether the folder and its metadata file still carry
// the modification times recorded for the indexed group.
func (s *Scanner) unchangedDirectory(absPath string, known model.LibraryGroup, info os.FileInfo) bool {
	if known.DeletedAt.Valid || known.DirModTime == 0 || known.DirModTime != info.ModTime().UnixNano() {
		return false
	}
	if known.MetaModTime == 0 {
		return true
	}
	metaInfo, err := os.Stat(filepath.Join(absPath, s.folderMetadataFile))
	if err != nil {
		return false
	}
	return metaInfo.ModTime().UnixNano() == known.MetaModTime
}

// recordedModTime returns 0 for a folder that changed moments ago, which makes the
// next scan read it again rather than trust a timestamp that a change in the same
// filesystem clock tick could share.
func (s *Scanner) recordedModTime(dirModTime time.Time, metaModTime int64) int64 {
	if time.Since(dirModTime) < s.recentChangeSkew {
		return 0
	}
	if metaModTime != 0 && time.Since(time.Unix(0, metaModTime)) < s.recentChangeSkew {
		return 0
	}
	return dirModTime.UnixNano()
}

func (s *Scanner) readFolderMetadata(absPath string, entries []fs.DirEntry) (FolderMetadata, int64, error) {
	meta := FolderMetadata{}
	for _, entry := range entries {
		if entry.IsDir() || entry.Name() != s.folderMetadataFile {
			continue
		}
		info, err := entry.Info()
		if err != nil {
			return meta, 0, err
		}
		metadataPath := filepath.Join(absPath, s.folderMetadataFile)
		if err := readJSON(metadataPath, &meta); err != nil {
			return meta, 0, fmt.Errorf("read group metadata %s: %w", metadataPath, err)
		}
		return meta, info.ModTime().UnixNano(), nil
	}
	return meta, 0, nil
}

// indexDirectoryFiles indexes one folder's melodies from the listing the walk
// already has, so neither the folder nor its metadata files are read twice.
func (s *Scanner) indexDirectoryFiles(ctx context.Context, tx *gorm.DB, absPath string, relPath string, group model.LibraryGroup, entries []fs.DirEntry, state *scanState, scanID string) error {
	present := make(map[string]struct{}, len(entries))
	stems := make([]string, 0, len(entries))
	filesByStem := map[string][]fs.DirEntry{}
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		present[entry.Name()] = struct{}{}
		if storage.InferFormat(entry.Name()) == "" {
			continue
		}
		stem := strings.TrimSuffix(entry.Name(), filepath.Ext(entry.Name()))
		if _, seen := filesByStem[stem]; !seen {
			stems = append(stems, stem)
		}
		filesByStem[stem] = append(filesByStem[stem], entry)
	}

	orders := naturalMelodyOrders(stems)
	for _, stem := range stems {
		meta := MelodyMetadata{}
		if _, ok := present[stem+s.melodyMetadataSuffix]; ok {
			metadataPath := filepath.Join(absPath, stem+s.melodyMetadataSuffix)
			if err := readJSON(metadataPath, &meta); err != nil {
				return fmt.Errorf("read melody metadata %s: %w", metadataPath, err)
			}
		}

		melody, err := s.indexMelody(ctx, tx, relPath, stem, meta, orders[stem], group, state, scanID)
		if err != nil {
			return err
		}
		primaryFormat := storage.NormalizeFormat(meta.PrimaryFormat)
		for _, entry := range filesByStem[stem] {
			info, err := entry.Info()
			if err != nil {
				return err
			}
			if err := s.indexVariant(ctx, tx, filepath.Join(absPath, entry.Name()), joinRelPath(relPath, entry.Name()), info, melody, primaryFormat, state, scanID); err != nil {
				return err
			}
		}
	}
	return nil
}

type groupInput struct {
	relPath     string
	absPath     string
	meta        FolderMetadata
	parent      *model.LibraryGroup
	dirModTime  int64
	metaModTime int64
}

// indexGroup upserts one folder. The second return value reports whether children
// must be read even when their own modification time is unchanged, which happens
// when this folder is new, restored, or changed visibility that they inherit.
func (s *Scanner) indexGroup(ctx context.Context, tx *gorm.DB, in groupInput, state *scanState, scanID string) (model.LibraryGroup, bool, error) {
	name := strings.TrimSpace(in.meta.Name)
	if name == "" {
		if in.relPath == "" {
			name = "Library"
		} else {
			name = filepath.Base(in.absPath)
		}
	}
	isPublic := publicValue(in.meta.IsPublic, in.meta.IsActive)

	var parentID *uint
	if in.parent != nil {
		parentID = &in.parent.ID
		if !in.parent.IsPublic {
			isPublic = false
		}
	}

	group, found := state.groups[in.relPath]
	if !found {
		group = model.LibraryGroup{Path: in.relPath}
	}
	previous := group

	group.ParentID = parentID
	group.Name = name
	group.Slug = util.Slugify(path.Base(in.relPath))
	if in.relPath == "" {
		group.Slug = "library"
	}
	group.Description = strings.TrimSpace(in.meta.Description)
	group.SortOrder = in.meta.SortOrder
	group.IsPublic = isPublic
	group.DirModTime = in.dirModTime
	group.MetaModTime = in.metaModTime
	group.ScanID = scanID
	group.DeletedAt = gorm.DeletedAt{}
	if err := validateLengths(
		lengthField{"group path", group.Path, 640},
		lengthField{"group name", group.Name, 180},
		lengthField{"group slug", group.Slug, 220},
	); err != nil {
		return model.LibraryGroup{}, false, err
	}

	if group.ID == 0 {
		if err := gorm.G[model.LibraryGroup](tx).
			Omit(
				dbquery.LibraryGroup.Parent.Name(),
				dbquery.LibraryGroup.Tags.Name(),
				dbquery.LibraryGroup.Children.Name(),
				dbquery.LibraryGroup.Melodies.Name(),
			).
			Create(ctx, &group); err != nil {
			return model.LibraryGroup{}, false, err
		}
	} else if previous.DeletedAt.Valid || !sameGroup(previous, group) {
		if _, err := gorm.G[model.LibraryGroup](tx).
			Scopes(dbquery.Unscoped).
			Where(dbquery.LibraryGroup.ID.Eq(group.ID)).
			Select("*").
			Omit(
				dbquery.LibraryGroup.Parent.Name(),
				dbquery.LibraryGroup.Tags.Name(),
				dbquery.LibraryGroup.Children.Name(),
				dbquery.LibraryGroup.Melodies.Name(),
			).
			Updates(ctx, group); err != nil {
			return model.LibraryGroup{}, false, err
		}
	}

	tags, err := loadOrCreateTags(ctx, tx, in.meta.Tags)
	if err != nil {
		return model.LibraryGroup{}, false, err
	}
	if previous.ID == 0 || previous.DeletedAt.Valid || !sameTagSet(previous.Tags, tags) {
		if err := replaceGroupTags(ctx, tx, group.ID, tags); err != nil {
			return model.LibraryGroup{}, false, err
		}
	}
	group.Tags = tags
	state.groups[in.relPath] = group
	state.groupIDs = append(state.groupIDs, group.ID)

	changed := previous.ID == 0 || previous.DeletedAt.Valid || previous.IsPublic != group.IsPublic
	return group, changed, nil
}

func (s *Scanner) indexMelody(ctx context.Context, tx *gorm.DB, groupPath string, fileStem string, meta MelodyMetadata, naturalOrder int, group model.LibraryGroup, state *scanState, scanID string) (model.Melody, error) {
	sourcePath := joinRelPath(groupPath, fileStem)

	title := strings.TrimSpace(meta.Title)
	if title == "" {
		title = humanTitle(fileStem)
	}
	isPublic := publicValue(meta.IsPublic, meta.IsPublished)
	if !group.IsPublic {
		isPublic = false
	}

	melody, found := state.melodies[sourcePath]
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
	melody.SortOrder = naturalOrder
	if meta.SortOrder != nil {
		melody.SortOrder = *meta.SortOrder
	}
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
		return model.Melody{}, err
	}

	if melody.ID == 0 {
		if err := gorm.G[model.Melody](tx).
			Omit(
				dbquery.Melody.Group.Name(),
				dbquery.Melody.Tags.Name(),
				dbquery.Melody.Variants.Name(),
			).
			Create(ctx, &melody); err != nil {
			return model.Melody{}, err
		}
	} else if previous.DeletedAt.Valid || !sameMelody(previous, melody) {
		if _, err := gorm.G[model.Melody](tx).
			Scopes(dbquery.Unscoped).
			Where(dbquery.Melody.ID.Eq(melody.ID)).
			Select("*").
			Omit(
				dbquery.Melody.Group.Name(),
				dbquery.Melody.Tags.Name(),
				dbquery.Melody.Variants.Name(),
			).
			Updates(ctx, melody); err != nil {
			return model.Melody{}, err
		}
	}

	tags, err := loadOrCreateTags(ctx, tx, meta.Tags)
	if err != nil {
		return model.Melody{}, err
	}
	if previous.ID == 0 || previous.DeletedAt.Valid || !sameTagSet(previous.Tags, tags) {
		if err := replaceMelodyTags(ctx, tx, melody.ID, tags); err != nil {
			return model.Melody{}, err
		}
	}
	melody.Tags = tags
	state.melodies[sourcePath] = melody
	state.melodyIDs = append(state.melodyIDs, melody.ID)

	return melody, nil
}

func (s *Scanner) indexVariant(ctx context.Context, tx *gorm.DB, absPath string, relPath string, info fs.FileInfo, melody model.Melody, primaryFormat string, state *scanState, scanID string) error {
	fileName := filepath.Base(absPath)
	ext := filepath.Ext(fileName)
	format := storage.InferFormat(fileName)

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
		var err error
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
			if _, err := gorm.G[model.FileVariant](tx).
				Where(dbquery.FileVariant.ID.Eq(oldPrimaryID)).
				Update(ctx, "is_primary", false); err != nil {
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
		if err := gorm.G[model.FileVariant](tx).
			Omit(dbquery.FileVariant.Melody.Name()).
			Create(ctx, &variant); err != nil {
			return err
		}
	} else if previous.DeletedAt.Valid || !sameVariant(previous, variant) {
		if _, err := gorm.G[model.FileVariant](tx).
			Scopes(dbquery.Unscoped).
			Where(dbquery.FileVariant.ID.Eq(variant.ID)).
			Select("*").
			Omit(dbquery.FileVariant.Melody.Name()).
			Updates(ctx, variant); err != nil {
			return err
		}
	}
	state.variants[relPath] = variant
	state.variantIDs = append(state.variantIDs, variant.ID)
	return nil
}

func naturalMelodyOrders(stems []string) map[string]int {
	sorted := make([]string, len(stems))
	copy(sorted, stems)
	sort.Slice(sorted, func(i, j int) bool { return util.NaturalLess(sorted[i], sorted[j]) })

	orders := make(map[string]int, len(sorted))
	for index, stem := range sorted {
		orders[stem] = index
	}
	return orders
}

func joinRelPath(base string, name string) string {
	if base == "" {
		return name
	}
	return path.Join(base, name)
}

func replaceGroupTags(ctx context.Context, tx *gorm.DB, groupID uint, tags []model.Tag) error {
	owner := gorm.G[model.LibraryGroup](tx).Where(dbquery.LibraryGroup.ID.Eq(groupID))
	if _, err := owner.Set(dbquery.LibraryGroup.Tags.Unlink()).Update(ctx); err != nil {
		return err
	}
	if len(tags) == 0 {
		return nil
	}
	_, err := owner.Set(dbquery.LibraryGroup.Tags.CreateInBatch(tags)).Update(ctx)
	return err
}

func replaceMelodyTags(ctx context.Context, tx *gorm.DB, melodyID uint, tags []model.Tag) error {
	owner := gorm.G[model.Melody](tx).Where(dbquery.Melody.ID.Eq(melodyID))
	if _, err := owner.Set(dbquery.Melody.Tags.Unlink()).Update(ctx); err != nil {
		return err
	}
	if len(tags) == 0 {
		return nil
	}
	_, err := owner.Set(dbquery.Melody.Tags.CreateInBatch(tags)).Update(ctx)
	return err
}

func loadScanState(ctx context.Context, tx *gorm.DB) (*scanState, error) {
	state := &scanState{
		groups:          map[string]model.LibraryGroup{},
		melodies:        map[string]model.Melody{},
		variants:        map[string]model.FileVariant{},
		childPaths:      map[string][]string{},
		primaryByMelody: map[uint]uint{},
		primaryAssigned: map[uint]bool{},
		demotedVariants: map[uint]bool{},
	}
	groups, err := gorm.G[model.LibraryGroup](tx).
		Scopes(dbquery.Unscoped).
		Preload(dbquery.LibraryGroup.Tags.Name(), nil).
		Find(ctx)
	if err != nil {
		return nil, err
	}
	for _, group := range groups {
		state.groups[group.Path] = group
		// The known tree lets an incremental scan descend into a folder it skipped
		// without listing it. A new or removed subfolder changes the parent's
		// modification time, so the parent is read and the change is seen there.
		if group.DeletedAt.Valid || group.Path == "" {
			continue
		}
		parent := path.Dir(group.Path)
		if parent == "." {
			parent = ""
		}
		state.childPaths[parent] = append(state.childPaths[parent], group.Path)
	}
	for parent := range state.childPaths {
		sort.Strings(state.childPaths[parent])
	}
	melodies, err := gorm.G[model.Melody](tx).
		Scopes(dbquery.Unscoped).
		Preload(dbquery.Melody.Tags.Name(), nil).
		Find(ctx)
	if err != nil {
		return nil, err
	}
	for _, melody := range melodies {
		state.melodies[melody.SourcePath] = melody
	}
	variants, err := gorm.G[model.FileVariant](tx).
		Scopes(dbquery.Unscoped).
		Find(ctx)
	if err != nil {
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

func markSeen[T any](ctx context.Context, tx *gorm.DB, ids []uint, scanID string) error {
	const batchSize = 500
	for start := 0; start < len(ids); start += batchSize {
		end := min(start+batchSize, len(ids))
		if _, err := gorm.G[T](tx).
			Scopes(dbquery.Unscoped, dbquery.SkipHooks).
			Where("id IN ?", ids[start:end]).
			Update(ctx, "scan_id", scanID); err != nil {
			return err
		}
	}
	return nil
}

func countCatalog(ctx context.Context, tx *gorm.DB, result *Result) error {
	groups, err := gorm.G[model.LibraryGroup](tx).Count(ctx, "*")
	if err != nil {
		return err
	}
	melodies, err := gorm.G[model.Melody](tx).Count(ctx, "*")
	if err != nil {
		return err
	}
	variants, err := gorm.G[model.FileVariant](tx).Count(ctx, "*")
	if err != nil {
		return err
	}
	result.GroupsIndexed = int(groups)
	result.MelodiesFound = int(melodies)
	result.VariantsFound = int(variants)
	return nil
}

func sameGroup(left model.LibraryGroup, right model.LibraryGroup) bool {
	return sameUintPointer(left.ParentID, right.ParentID) &&
		left.Path == right.Path && left.Name == right.Name && left.Slug == right.Slug &&
		left.Description == right.Description && left.SortOrder == right.SortOrder && left.IsPublic == right.IsPublic &&
		left.DirModTime == right.DirModTime && left.MetaModTime == right.MetaModTime
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

// missingGroups returns the indexed folders that no longer exist on disk, deepest
// first so children are removed before their parents.
func missingGroups(state *scanState, seen map[uint]bool) []model.LibraryGroup {
	missing := make([]model.LibraryGroup, 0)
	for _, group := range state.groups {
		if group.ID == 0 || group.DeletedAt.Valid || seen[group.ID] {
			continue
		}
		missing = append(missing, group)
	}
	sort.Slice(missing, func(i, j int) bool { return len(missing[i].Path) > len(missing[j].Path) })
	return missing
}

// cleanupStale removes what this scan no longer found. Only folders that were read
// are considered, so rows in skipped folders - which cannot have lost files without
// their folder changing - are left untouched.
func cleanupStale(ctx context.Context, tx *gorm.DB, scanID string, scannedGroups []uint, missing []model.LibraryGroup) error {
	const chunkSize = 200
	const staleCondition = "(scan_id <> ? OR scan_id = '' OR scan_id IS NULL)"

	for start := 0; start < len(scannedGroups); start += chunkSize {
		end := min(start+chunkSize, len(scannedGroups))
		chunk := scannedGroups[start:end]
		if _, err := gorm.G[model.FileVariant](tx).
			Where(staleCondition+" AND melody_id IN (SELECT id FROM melodies WHERE group_id IN ?)", scanID, chunk).
			Delete(ctx); err != nil {
			return err
		}
		staleMelodies, err := gorm.G[model.Melody](tx).
			Where(staleCondition+" AND group_id IN ?", scanID, chunk).
			Find(ctx)
		if err != nil {
			return err
		}
		if err := deleteMelodies(ctx, tx, staleMelodies); err != nil {
			return err
		}
	}

	for _, group := range missing {
		melodies, err := gorm.G[model.Melody](tx).
			Where(dbquery.Melody.GroupID.Eq(group.ID)).
			Find(ctx)
		if err != nil {
			return err
		}
		if err := deleteMelodies(ctx, tx, melodies); err != nil {
			return err
		}
		owner := gorm.G[model.LibraryGroup](tx).Where(dbquery.LibraryGroup.ID.Eq(group.ID))
		if _, err := owner.Set(dbquery.LibraryGroup.Tags.Unlink()).Update(ctx); err != nil {
			return err
		}
		if _, err := owner.Delete(ctx); err != nil {
			return err
		}
	}
	return nil
}

func deleteMelodies(ctx context.Context, tx *gorm.DB, melodies []model.Melody) error {
	if len(melodies) == 0 {
		return nil
	}
	ids := make([]uint, len(melodies))
	for index := range melodies {
		ids[index] = melodies[index].ID
	}
	if _, err := gorm.G[model.FileVariant](tx).
		Where(dbquery.FileVariant.MelodyID.In(ids...)).
		Delete(ctx); err != nil {
		return err
	}
	owner := gorm.G[model.Melody](tx).Where(dbquery.Melody.ID.In(ids...))
	if _, err := owner.Set(dbquery.Melody.Tags.Unlink()).Update(ctx); err != nil {
		return err
	}
	_, err := owner.Delete(ctx)
	return err
}

func loadOrCreateTags(ctx context.Context, tx *gorm.DB, names []string) ([]model.Tag, error) {
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

		tag, err := gorm.G[model.Tag](tx).
			Scopes(dbquery.Unscoped).
			Where(dbquery.Tag.Slug.Eq(slug)).
			First(ctx)
		if errors.Is(err, gorm.ErrRecordNotFound) {
			tag = model.Tag{Name: name, Slug: slug}
			if err := gorm.G[model.Tag](tx).Create(ctx, &tag); err != nil {
				return nil, err
			}
		} else if err != nil {
			return nil, err
		}
		if tag.DeletedAt.Valid {
			tag.DeletedAt = gorm.DeletedAt{}
			tag.Name = name
			if _, err := gorm.G[model.Tag](tx).
				Scopes(dbquery.Unscoped).
				Where(dbquery.Tag.ID.Eq(tag.ID)).
				Set(
					dbquery.Tag.Name.Set(tag.Name),
					dbquery.Tag.DeletedAt.Set(tag.DeletedAt),
				).
				Update(ctx); err != nil {
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
