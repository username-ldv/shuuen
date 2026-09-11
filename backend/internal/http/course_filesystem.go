package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type courseGroupMetadata struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	SortOrder   int    `json:"sort_order"`
	IsPublic    bool   `json:"is_public"`
}

// createCourseGroupFolder creates the folder and scanner metadata as one
// recoverable step. Rollback removes only files created by this operation and
// refuses to recursively remove a directory that gained other content.
func (h *Handler) createCourseGroupFolder(groupPath string, metadata courseGroupMetadata) (func() error, error) {
	if strings.TrimSpace(groupPath) == "" {
		return nil, errors.New("invalid group path")
	}
	absolute, err := h.storage.AbsolutePath(groupPath)
	if err != nil {
		return nil, err
	}
	parent, err := filepath.EvalSymlinks(filepath.Dir(absolute))
	if err != nil {
		return nil, errors.New("group parent does not exist")
	}
	absolute = filepath.Join(parent, filepath.Base(absolute))
	if err := os.Mkdir(absolute, 0o755); err != nil {
		if errors.Is(err, os.ErrExist) {
			return nil, errors.New("group folder already exists")
		}
		return nil, err
	}
	metadataPath := filepath.Join(absolute, h.folderMetadataFile)
	rollback := func() error {
		if err := os.Remove(metadataPath); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
		return os.Remove(absolute)
	}
	if err := writeCourseGroupMetadata(metadataPath, metadata, true); err != nil {
		_ = rollback()
		return nil, err
	}
	return rollback, nil
}

func (h *Handler) updateCourseGroupMetadata(groupPath string, metadata courseGroupMetadata) error {
	absolute, err := h.storage.AbsolutePath(groupPath)
	if err != nil {
		return err
	}
	info, err := os.Stat(absolute)
	if err != nil {
		return err
	}
	if !info.IsDir() {
		return errors.New("group path is not a directory")
	}
	return writeCourseGroupMetadata(filepath.Join(absolute, h.folderMetadataFile), metadata, false)
}

func writeCourseGroupMetadata(metadataPath string, metadata courseGroupMetadata, exclusive bool) error {
	payload := map[string]any{}
	if !exclusive {
		if existing, err := os.ReadFile(metadataPath); err == nil {
			if err := json.Unmarshal(existing, &payload); err != nil {
				return fmt.Errorf("read existing group metadata: %w", err)
			}
		} else if !errors.Is(err, os.ErrNotExist) {
			return err
		}
	}
	payload["name"] = metadata.Name
	payload["description"] = metadata.Description
	payload["sort_order"] = metadata.SortOrder
	payload["is_public"] = metadata.IsPublic
	delete(payload, "is_active")
	encoded, err := json.MarshalIndent(payload, "", "  ")
	if err != nil {
		return err
	}
	encoded = append(encoded, '\n')
	flags := os.O_CREATE | os.O_WRONLY | os.O_TRUNC
	if exclusive {
		flags |= os.O_EXCL
	}
	file, err := os.OpenFile(metadataPath, flags, 0o644)
	if err != nil {
		return err
	}
	if _, err := file.Write(encoded); err != nil {
		_ = file.Close()
		return err
	}
	if err := file.Sync(); err != nil {
		_ = file.Close()
		return err
	}
	return file.Close()
}
