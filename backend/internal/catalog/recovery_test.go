package catalog

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/ncruces/go-sqlite3/gormlite"
	"gorm.io/gorm"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
	"shuuen-backend/internal/storage"
)

func TestRecoverPendingDeletesRestoresActiveAndDiscardsDeletedVariants(t *testing.T) {
	root := t.TempDir()
	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := db.AutoMigrate(&model.LibraryGroup{}, &model.Melody{}, &model.FileVariant{}, &model.Tag{}); err != nil {
		t.Fatal(err)
	}
	group := model.LibraryGroup{Path: "group", Name: "Group", Slug: "group", IsPublic: true}
	if err := db.Create(&group).Error; err != nil {
		t.Fatal(err)
	}
	melody := model.Melody{GroupID: group.ID, SourcePath: "group/song", FileStem: "song", Title: "Song", Slug: "song", IsPublic: true}
	if err := db.Create(&melody).Error; err != nil {
		t.Fatal(err)
	}
	variant := model.FileVariant{MelodyID: melody.ID, Format: "midi", OriginalName: "song.mid", StoredName: "song.mid", StoragePath: "group/song.mid", ChecksumSHA: "checksum"}
	if err := db.Create(&variant).Error; err != nil {
		t.Fatal(err)
	}
	store, err := storage.NewFileStore(config.CatalogConfig{Root: root, MaxUploadBytes: 1024})
	if err != nil {
		t.Fatal(err)
	}
	file := filepath.Join(root, "group", "song.mid")
	if err := os.MkdirAll(filepath.Dir(file), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(file, []byte("midi"), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := store.StageDelete(variant.StoragePath); err != nil {
		t.Fatal(err)
	}
	if err := RecoverPendingDeletes(t.Context(), db, store.Root()); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(file); err != nil {
		t.Fatalf("active variant file was not recovered: %v", err)
	}

	if _, err := store.StageDelete(variant.StoragePath); err != nil {
		t.Fatal(err)
	}
	if err := db.Delete(&variant).Error; err != nil {
		t.Fatal(err)
	}
	if err := RecoverPendingDeletes(t.Context(), db, store.Root()); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(file); !os.IsNotExist(err) {
		t.Fatalf("deleted variant file was unexpectedly restored: %v", err)
	}
}
