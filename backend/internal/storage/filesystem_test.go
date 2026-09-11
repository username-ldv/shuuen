package storage

import (
	"os"
	"path/filepath"
	"testing"

	"shuuen-backend/internal/config"
)

func TestNormalizeFormat(t *testing.T) {
	tests := map[string]string{
		"mid":      "midi",
		"MIDI":     "midi",
		"xml":      "musicxml",
		"mxl":      "musicxml",
		"musicxml": "musicxml",
	}

	for input, expected := range tests {
		if actual := NormalizeFormat(input); actual != expected {
			t.Fatalf("NormalizeFormat(%q) = %q, want %q", input, actual, expected)
		}
	}
}

func TestInferFormat(t *testing.T) {
	tests := map[string]string{
		"song.mid":       "midi",
		"song.midi":      "midi",
		"score.musicxml": "musicxml",
		"score.mxl":      "musicxml",
		"score.xml":      "musicxml",
		"notes.txt":      "",
	}

	for filename, expected := range tests {
		if actual := InferFormat(filename); actual != expected {
			t.Fatalf("InferFormat(%q) = %q, want %q", filename, actual, expected)
		}
	}
}

func TestStagedDeleteCanRollbackOrCommit(t *testing.T) {
	root := t.TempDir()
	store, err := NewFileStore(config.CatalogConfig{Root: root, MaxUploadBytes: 1024})
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

	pending, err := store.StageDelete("group/song.mid")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(file); !os.IsNotExist(err) {
		t.Fatalf("staged file still exists at original path: %v", err)
	}
	if err := pending.Rollback(); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(file); err != nil {
		t.Fatalf("rollback did not restore file: %v", err)
	}

	pending, err = store.StageDelete("group/song.mid")
	if err != nil {
		t.Fatal(err)
	}
	if err := pending.Commit(); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(file); !os.IsNotExist(err) {
		t.Fatalf("committed deletion left original file: %v", err)
	}
}
