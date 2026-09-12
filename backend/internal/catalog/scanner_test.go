package catalog

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/ncruces/go-sqlite3/gormlite"
	"gorm.io/gorm"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
	dbquery "shuuen-backend/internal/query"
)

func TestScannerIndexesRecursiveFoldersAndVariants(t *testing.T) {
	root := t.TempDir()
	groupDir := filepath.Join(root, "my_textbook", "1")
	if err := os.MkdirAll(groupDir, 0o755); err != nil {
		t.Fatalf("MkdirAll returned error: %v", err)
	}

	writeFile(t, filepath.Join(root, "my_textbook", ".shuuen.json"), `{"name":"My Textbook","tags":["book"]}`)
	writeFile(t, filepath.Join(groupDir, ".shuuen.json"), `{"name":"Grade 1","tags":["grade"]}`)
	writeFile(t, filepath.Join(groupDir, "warmup.shuuen.json"), `{"title":"Warmup","tags":["easy"],"primary_format":"musicxml","key":{"tonic":"D","spelling":"sharps","degrees":["D5","D1","D3","D2","D4","D6","D7"]}}`)
	writeFile(t, filepath.Join(groupDir, "warmup.mid"), "midi")
	writeFile(t, filepath.Join(groupDir, "warmup.musicxml"), "<score-partwise />")

	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatalf("gorm.Open returned error: %v", err)
	}
	if err := db.AutoMigrate(&model.User{}, &model.LibraryGroup{}, &model.Tag{}, &model.Melody{}, &model.FileVariant{}); err != nil {
		t.Fatalf("AutoMigrate returned error: %v", err)
	}

	scanner, err := NewScanner(db, config.CatalogConfig{
		Root:                 root,
		FolderMetadataFile:   ".shuuen.json",
		MelodyMetadataSuffix: ".shuuen.json",
		MaxUploadBytes:       1024 * 1024,
	})
	if err != nil {
		t.Fatalf("NewScanner returned error: %v", err)
	}

	result, err := scanner.Scan(t.Context())
	if err != nil {
		t.Fatalf("Scan returned error: %v", err)
	}
	if result.GroupsIndexed != 3 {
		t.Fatalf("GroupsIndexed = %d, want 3", result.GroupsIndexed)
	}
	if result.MelodiesFound != 1 {
		t.Fatalf("MelodiesFound = %d, want 1", result.MelodiesFound)
	}
	if result.VariantsFound != 2 {
		t.Fatalf("VariantsFound = %d, want 2", result.VariantsFound)
	}

	group, err := gorm.G[model.LibraryGroup](db).
		Preload(dbquery.LibraryGroup.Tags.Name(), nil).
		Where(dbquery.LibraryGroup.Path.Eq("my_textbook/1")).
		First(t.Context())
	if err != nil {
		t.Fatalf("expected group to be indexed: %v", err)
	}
	if group.Name != "Grade 1" || len(group.Tags) != 1 {
		t.Fatalf("unexpected group metadata: %#v", group)
	}

	melody, err := gorm.G[model.Melody](db).
		Preload(dbquery.Melody.Tags.Name(), nil).
		Preload(dbquery.Melody.Variants.Name(), nil).
		Where(dbquery.Melody.SourcePath.Eq("my_textbook/1/warmup")).
		First(t.Context())
	if err != nil {
		t.Fatalf("expected melody to be indexed: %v", err)
	}
	if melody.Title != "Warmup" || len(melody.Tags) != 1 || len(melody.Variants) != 2 {
		t.Fatalf("unexpected melody metadata: %#v", melody)
	}
	var key struct {
		Tonic     string   `json:"tonic"`
		Degrees   []string `json:"degrees"`
		ScaleType string   `json:"scale_type"`
	}
	if err := json.Unmarshal(melody.Key, &key); err != nil {
		t.Fatalf("melody key = %s: %v", melody.Key, err)
	}
	if key.Tonic != "D" || key.ScaleType != "Major" || len(key.Degrees) != 7 || key.Degrees[1] != "D2" {
		t.Fatalf("melody key was not normalized: %#v", key)
	}

	primary, err := gorm.G[model.FileVariant](db).
		Where("melody_id = ? AND is_primary = ?", melody.ID, true).
		First(t.Context())
	if err != nil {
		t.Fatalf("expected primary variant: %v", err)
	}
	if primary.Format != "musicxml" {
		t.Fatalf("primary format = %q, want musicxml", primary.Format)
	}
}

func TestScannerRestoresSoftDeletedCatalogRows(t *testing.T) {
	root := t.TempDir()
	groupDir := filepath.Join(root, "group")
	if err := os.MkdirAll(groupDir, 0o755); err != nil {
		t.Fatal(err)
	}
	midiPath := filepath.Join(groupDir, "song.mid")
	xmlPath := filepath.Join(groupDir, "song.musicxml")
	writeFile(t, filepath.Join(groupDir, "song.shuuen.json"), `{"tags":["restored"]}`)
	writeFile(t, midiPath, "midi")
	writeFile(t, xmlPath, "<score-partwise />")

	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := db.AutoMigrate(&model.User{}, &model.LibraryGroup{}, &model.Tag{}, &model.Melody{}, &model.FileVariant{}); err != nil {
		t.Fatal(err)
	}
	scanner, err := NewScanner(db, config.CatalogConfig{
		Root: root, FolderMetadataFile: ".shuuen.json", MelodyMetadataSuffix: ".shuuen.json", MaxUploadBytes: 1024,
	})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}
	original, err := gorm.G[model.Melody](db).
		Where(dbquery.Melody.SourcePath.Eq("group/song")).
		First(t.Context())
	if err != nil {
		t.Fatal(err)
	}

	if err := os.Remove(midiPath); err != nil {
		t.Fatal(err)
	}
	if err := os.Remove(xmlPath); err != nil {
		t.Fatal(err)
	}
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}
	if _, err := gorm.G[model.Melody](db).Where(dbquery.Melody.ID.Eq(original.ID)).First(t.Context()); !errors.Is(err, gorm.ErrRecordNotFound) {
		t.Fatalf("expected melody to be soft-deleted, got %v", err)
	}
	var joinCount int64
	if err := gorm.G[int64](db).Raw("SELECT COUNT(*) FROM melody_tags WHERE melody_id = ?", original.ID).Scan(t.Context(), &joinCount); err != nil {
		t.Fatal(err)
	}
	if joinCount != 0 {
		t.Fatalf("soft-deleted melody retained %d tag links", joinCount)
	}

	writeFile(t, midiPath, "midi")
	writeFile(t, xmlPath, "<score-partwise />")
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatalf("restoring files caused scan failure: %v", err)
	}
	restored, err := gorm.G[model.Melody](db).
		Preload(dbquery.Melody.Tags.Name(), nil).
		Where(dbquery.Melody.SourcePath.Eq("group/song")).
		First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if restored.ID != original.ID {
		t.Fatalf("restored melody ID = %d, want stable ID %d", restored.ID, original.ID)
	}
	if len(restored.Tags) != 1 || restored.Tags[0].Slug != "restored" {
		t.Fatalf("restored melody tags = %#v, want restored tag", restored.Tags)
	}
}

func TestScannerUsesUnifiedPublicVisibilityAndInheritsPrivateParent(t *testing.T) {
	root := t.TempDir()
	child := filepath.Join(root, "private", "child")
	if err := os.MkdirAll(child, 0o755); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(root, "private", ".shuuen.json"), `{"is_public":false}`)
	writeFile(t, filepath.Join(child, "song.mid"), "midi")

	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := db.AutoMigrate(&model.User{}, &model.LibraryGroup{}, &model.Tag{}, &model.Melody{}, &model.FileVariant{}); err != nil {
		t.Fatal(err)
	}
	scanner, err := NewScanner(db, config.CatalogConfig{
		Root: root, FolderMetadataFile: ".shuuen.json", MelodyMetadataSuffix: ".shuuen.json", MaxUploadBytes: 1024,
	})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}
	group, err := gorm.G[model.LibraryGroup](db).
		Where(dbquery.LibraryGroup.Path.Eq("private/child")).
		First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if group.IsPublic {
		t.Fatal("child of private group should be private")
	}
	melody, err := gorm.G[model.Melody](db).
		Where(dbquery.Melody.SourcePath.Eq("private/child/song")).
		First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if melody.IsPublic {
		t.Fatal("melody in private group should be private")
	}
}

func TestScannerAssignsNaturalMelodyOrderUnlessMetadataOverridesIt(t *testing.T) {
	root := t.TempDir()
	groupDir := filepath.Join(root, "group")
	if err := os.MkdirAll(groupDir, 0o755); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(groupDir, "1.mid"), "midi")
	writeFile(t, filepath.Join(groupDir, "2.mid"), "midi")
	writeFile(t, filepath.Join(groupDir, "2.musicxml"), "<score-partwise />")
	writeFile(t, filepath.Join(groupDir, "10.mid"), "midi")
	writeFile(t, filepath.Join(groupDir, "custom.mid"), "midi")
	writeFile(t, filepath.Join(groupDir, "custom.shuuen.json"), `{"sort_order":42}`)

	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := db.AutoMigrate(&model.User{}, &model.LibraryGroup{}, &model.Tag{}, &model.Melody{}, &model.FileVariant{}); err != nil {
		t.Fatal(err)
	}
	scanner, err := NewScanner(db, config.CatalogConfig{
		Root: root, FolderMetadataFile: ".shuuen.json", MelodyMetadataSuffix: ".shuuen.json", MaxUploadBytes: 1024,
	})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}

	melodies, err := gorm.G[model.Melody](db).
		Where("source_path LIKE ?", "group/%").
		Order("sort_order asc, title asc, id asc").
		Find(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if len(melodies) != 4 {
		t.Fatalf("indexed %d melodies, want 4", len(melodies))
	}
	wantStems := []string{"1", "2", "10", "custom"}
	wantOrders := []int{0, 1, 2, 42}
	for index, melody := range melodies {
		if melody.FileStem != wantStems[index] || melody.SortOrder != wantOrders[index] {
			t.Fatalf("melody %d = (%q, %d), want (%q, %d)", index, melody.FileStem, melody.SortOrder, wantStems[index], wantOrders[index])
		}
	}
}

func TestUnchangedScanDoesNotRewriteCatalogTimestamps(t *testing.T) {
	root := t.TempDir()
	groupDir := filepath.Join(root, "group")
	if err := os.MkdirAll(groupDir, 0o755); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(groupDir, "song.mid"), "midi")
	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := db.AutoMigrate(&model.User{}, &model.LibraryGroup{}, &model.Tag{}, &model.Melody{}, &model.FileVariant{}); err != nil {
		t.Fatal(err)
	}
	scanner, err := NewScanner(db, config.CatalogConfig{
		Root: root, FolderMetadataFile: ".shuuen.json", MelodyMetadataSuffix: ".shuuen.json", MaxUploadBytes: 1024,
	})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}
	before, err := gorm.G[model.Melody](db).
		Where(dbquery.Melody.SourcePath.Eq("group/song")).
		First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	time.Sleep(5 * time.Millisecond)
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}
	after, err := gorm.G[model.Melody](db).Where(dbquery.Melody.ID.Eq(before.ID)).First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if !after.UpdatedAt.Equal(before.UpdatedAt) {
		t.Fatalf("unchanged melody UpdatedAt changed from %s to %s", before.UpdatedAt, after.UpdatedAt)
	}
}

func newScannerForTest(t *testing.T, root string) (*Scanner, *gorm.DB) {
	t.Helper()
	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatalf("gorm.Open returned error: %v", err)
	}
	if err := db.AutoMigrate(&model.User{}, &model.LibraryGroup{}, &model.Tag{}, &model.Melody{}, &model.FileVariant{}); err != nil {
		t.Fatalf("AutoMigrate returned error: %v", err)
	}
	scanner, err := NewScanner(db, config.CatalogConfig{
		Root: root, FolderMetadataFile: ".shuuen.json", MelodyMetadataSuffix: ".shuuen.json", MaxUploadBytes: 1024,
	})
	if err != nil {
		t.Fatalf("NewScanner returned error: %v", err)
	}
	// These tests change files right after scanning them, and the guard that keeps
	// freshly changed folders out of the fast path would hide those changes.
	scanner.recentChangeSkew = 0
	return scanner, db
}

func TestIncrementalScanReadsOnlyChangedFolders(t *testing.T) {
	root := t.TempDir()
	groupDir := filepath.Join(root, "group")
	if err := os.MkdirAll(groupDir, 0o755); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(groupDir, "song.mid"), "midi")
	scanner, _ := newScannerForTest(t, root)

	first, err := scanner.Scan(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if first.GroupsScanned != 2 {
		t.Fatalf("first scan read %d folders, want 2", first.GroupsScanned)
	}

	unchanged, err := scanner.Scan(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if unchanged.GroupsScanned != 0 {
		t.Fatalf("unchanged scan read %d folders, want 0", unchanged.GroupsScanned)
	}
	if unchanged.MelodiesFound != 1 || unchanged.VariantsFound != 1 {
		t.Fatalf("unchanged scan reported %d melodies and %d variants, want 1 and 1", unchanged.MelodiesFound, unchanged.VariantsFound)
	}

	time.Sleep(50 * time.Millisecond)
	writeFile(t, filepath.Join(groupDir, "another.mid"), "midi")
	added, err := scanner.Scan(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if added.GroupsScanned != 1 {
		t.Fatalf("scan after adding a file read %d folders, want only the changed one", added.GroupsScanned)
	}
	if added.MelodiesFound != 2 {
		t.Fatalf("MelodiesFound = %d, want 2", added.MelodiesFound)
	}
}

func TestIncrementalScanLeavesInPlaceEditsToFullScan(t *testing.T) {
	root := t.TempDir()
	groupDir := filepath.Join(root, "group")
	if err := os.MkdirAll(groupDir, 0o755); err != nil {
		t.Fatal(err)
	}
	songPath := filepath.Join(groupDir, "song.mid")
	writeFile(t, songPath, "midi")
	scanner, db := newScannerForTest(t, root)
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}
	before, err := gorm.G[model.FileVariant](db).
		Where(dbquery.FileVariant.StoragePath.Eq("group/song.mid")).
		First(t.Context())
	if err != nil {
		t.Fatal(err)
	}

	time.Sleep(50 * time.Millisecond)
	writeFile(t, songPath, "a longer midi body")

	incremental, err := scanner.Scan(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if incremental.GroupsScanned != 0 {
		t.Fatalf("editing a file in place read %d folders, want 0", incremental.GroupsScanned)
	}
	stale, err := gorm.G[model.FileVariant](db).Where(dbquery.FileVariant.ID.Eq(before.ID)).First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if stale.ChecksumSHA != before.ChecksumSHA {
		t.Fatal("incremental scan unexpectedly re-read a file edited in place")
	}

	full, err := scanner.ScanFull(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if full.GroupsScanned != 2 {
		t.Fatalf("full scan read %d folders, want 2", full.GroupsScanned)
	}
	updated, err := gorm.G[model.FileVariant](db).Where(dbquery.FileVariant.ID.Eq(before.ID)).First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if updated.ChecksumSHA == before.ChecksumSHA || updated.SizeBytes != int64(len("a longer midi body")) {
		t.Fatalf("full scan did not pick up the edit: %#v", updated)
	}
}

func TestIncrementalScanDetectsFolderMetadataEdits(t *testing.T) {
	root := t.TempDir()
	groupDir := filepath.Join(root, "group")
	if err := os.MkdirAll(groupDir, 0o755); err != nil {
		t.Fatal(err)
	}
	metadataPath := filepath.Join(groupDir, ".shuuen.json")
	writeFile(t, metadataPath, `{"is_public":true}`)
	writeFile(t, filepath.Join(groupDir, "song.mid"), "midi")
	scanner, db := newScannerForTest(t, root)
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}

	time.Sleep(50 * time.Millisecond)
	writeFile(t, metadataPath, `{"is_public":false}`)
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}

	group, err := gorm.G[model.LibraryGroup](db).Where(dbquery.LibraryGroup.Path.Eq("group")).First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if group.IsPublic {
		t.Fatal("editing folder metadata should have made the group private")
	}
	melody, err := gorm.G[model.Melody](db).Where(dbquery.Melody.SourcePath.Eq("group/song")).First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if melody.IsPublic {
		t.Fatal("melody in a private group should be private")
	}
}

func TestIncrementalScanRemovesDeletedFolders(t *testing.T) {
	root := t.TempDir()
	keptDir := filepath.Join(root, "kept")
	goneDir := filepath.Join(root, "gone")
	if err := os.MkdirAll(keptDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(goneDir, 0o755); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(keptDir, "kept.mid"), "midi")
	writeFile(t, filepath.Join(goneDir, "gone.mid"), "midi")
	scanner, db := newScannerForTest(t, root)
	if _, err := scanner.Scan(t.Context()); err != nil {
		t.Fatal(err)
	}

	time.Sleep(50 * time.Millisecond)
	if err := os.RemoveAll(goneDir); err != nil {
		t.Fatal(err)
	}
	result, err := scanner.Scan(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if result.MelodiesFound != 1 || result.GroupsIndexed != 2 {
		t.Fatalf("after removing a folder: %d melodies and %d groups, want 1 and 2", result.MelodiesFound, result.GroupsIndexed)
	}
	if _, err := gorm.G[model.LibraryGroup](db).Where(dbquery.LibraryGroup.Path.Eq("gone")).First(t.Context()); !errors.Is(err, gorm.ErrRecordNotFound) {
		t.Fatalf("expected the removed folder to be deleted, got %v", err)
	}
	if _, err := gorm.G[model.Melody](db).Where(dbquery.Melody.SourcePath.Eq("kept/kept")).First(t.Context()); err != nil {
		t.Fatalf("melody in the untouched folder should have survived: %v", err)
	}
}

func writeFile(t *testing.T, path string, body string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatalf("WriteFile(%s) returned error: %v", path, err)
	}
}
