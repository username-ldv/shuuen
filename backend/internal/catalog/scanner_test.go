package catalog

import (
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
	writeFile(t, filepath.Join(groupDir, "warmup.shuuen.json"), `{"title":"Warmup","tags":["easy"],"primary_format":"musicxml"}`)
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

func writeFile(t *testing.T, path string, body string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatalf("WriteFile(%s) returned error: %v", path, err)
	}
}
