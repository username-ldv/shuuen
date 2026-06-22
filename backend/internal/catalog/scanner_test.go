package catalog

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/glebarez/sqlite"
	"gorm.io/gorm"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
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

	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
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

	var group model.LibraryGroup
	if err := db.Preload("Tags").Where("path = ?", "my_textbook/1").First(&group).Error; err != nil {
		t.Fatalf("expected group to be indexed: %v", err)
	}
	if group.Name != "Grade 1" || len(group.Tags) != 1 {
		t.Fatalf("unexpected group metadata: %#v", group)
	}

	var melody model.Melody
	if err := db.Preload("Tags").Preload("Variants").Where("source_path = ?", "my_textbook/1/warmup").First(&melody).Error; err != nil {
		t.Fatalf("expected melody to be indexed: %v", err)
	}
	if melody.Title != "Warmup" || len(melody.Tags) != 1 || len(melody.Variants) != 2 {
		t.Fatalf("unexpected melody metadata: %#v", melody)
	}

	var primary model.FileVariant
	if err := db.Where("melody_id = ? AND is_primary = ?", melody.ID, true).First(&primary).Error; err != nil {
		t.Fatalf("expected primary variant: %v", err)
	}
	if primary.Format != "musicxml" {
		t.Fatalf("primary format = %q, want musicxml", primary.Format)
	}
}

func writeFile(t *testing.T, path string, body string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatalf("WriteFile(%s) returned error: %v", path, err)
	}
}
