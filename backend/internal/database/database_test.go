package database

import (
	"testing"

	"github.com/ncruces/go-sqlite3/gormlite"
	"gorm.io/gorm"

	"shuuen-backend/internal/model"
)

func TestMigrateUpgradesLegacyVisibilityColumnsAndIsIdempotent(t *testing.T) {
	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[any](db).Exec(t.Context(), "CREATE TABLE library_groups (id integer primary key, is_active numeric not null default 1)"); err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[any](db).Exec(t.Context(), "INSERT INTO library_groups (id, is_active) VALUES (1, 0)"); err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[any](db).Exec(t.Context(), "CREATE TABLE melodies (id integer primary key, is_published numeric not null default 1)"); err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[any](db).Exec(t.Context(), "INSERT INTO melodies (id, is_published) VALUES (1, 0)"); err != nil {
		t.Fatal(err)
	}

	if err := migrateVisibilityColumn(t.Context(), db, "library_groups", "is_active"); err != nil {
		t.Fatalf("group visibility migration failed: %v", err)
	}
	if err := migrateVisibilityColumn(t.Context(), db, "melodies", "is_published"); err != nil {
		t.Fatalf("melody visibility migration failed: %v", err)
	}
	if !db.Migrator().HasColumn("library_groups", "is_public") || !db.Migrator().HasColumn("melodies", "is_public") {
		t.Fatal("legacy visibility columns were not migrated")
	}
	var groupPublic, melodyPublic bool
	if err := gorm.G[bool](db).Raw("SELECT is_public FROM library_groups WHERE id = 1").Scan(t.Context(), &groupPublic); err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[bool](db).Raw("SELECT is_public FROM melodies WHERE id = 1").Scan(t.Context(), &melodyPublic); err != nil {
		t.Fatal(err)
	}
	if groupPublic || melodyPublic {
		t.Fatal("legacy private visibility values were not preserved")
	}

	fresh, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := Migrate(t.Context(), fresh); err != nil {
		t.Fatalf("first migration failed: %v", err)
	}
	if err := Migrate(t.Context(), fresh); err != nil {
		t.Fatalf("second migration failed: %v", err)
	}
	count, err := gorm.G[schemaMigration](fresh).Count(t.Context(), "*")
	if err != nil {
		t.Fatal(err)
	}
	if count != int64(len(migrations)) {
		t.Fatalf("migration records = %d, want %d", count, len(migrations))
	}
	if !fresh.Migrator().HasIndex("file_variants", "idx_file_variants_one_primary") {
		t.Fatal("primary variant invariant index was not created")
	}
	group := model.LibraryGroup{Path: "group", Name: "Group", Slug: "group", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](fresh).Create(t.Context(), &group); err != nil {
		t.Fatal(err)
	}
	melody := model.Melody{GroupID: group.ID, SourcePath: "group/song", FileStem: "song", Title: "Song", Slug: "song", IsPublic: true}
	if err := gorm.G[model.Melody](fresh).Create(t.Context(), &melody); err != nil {
		t.Fatal(err)
	}
	first := model.FileVariant{MelodyID: melody.ID, Format: "midi", OriginalName: "song.mid", StoredName: "song.mid", StoragePath: "group/song.mid", ChecksumSHA: "a", IsPrimary: true}
	if err := gorm.G[model.FileVariant](fresh).Create(t.Context(), &first); err != nil {
		t.Fatal(err)
	}
	second := model.FileVariant{MelodyID: melody.ID, Format: "musicxml", OriginalName: "song.xml", StoredName: "song.xml", StoragePath: "group/song.xml", ChecksumSHA: "b", IsPrimary: true}
	if err := gorm.G[model.FileVariant](fresh).Create(t.Context(), &second); err == nil {
		t.Fatal("database allowed two active primary variants for one melody")
	}
}
