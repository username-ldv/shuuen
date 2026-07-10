package catalog

import (
	"context"
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"gorm.io/gorm"

	"shuuen-backend/internal/model"
)

// RecoverPendingDeletes resolves files left in .trash if the process stopped
// between staging a delete and committing or rolling back its database change.
func RecoverPendingDeletes(ctx context.Context, db *gorm.DB, root string) error {
	trashRoot := filepath.Join(root, ".trash")
	operations, err := os.ReadDir(trashRoot)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	for _, operation := range operations {
		if !operation.IsDir() {
			continue
		}
		operationDir := filepath.Join(trashRoot, operation.Name())
		err := filepath.WalkDir(operationDir, func(stagedPath string, entry fs.DirEntry, walkErr error) error {
			if walkErr != nil {
				return walkErr
			}
			if err := ctx.Err(); err != nil {
				return err
			}
			if entry.IsDir() {
				return nil
			}
			relative, err := filepath.Rel(operationDir, stagedPath)
			if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
				return errors.New("invalid staged delete path")
			}
			storagePath := filepath.ToSlash(relative)
			var variant model.FileVariant
			err = db.WithContext(ctx).Unscoped().Where("storage_path = ?", storagePath).First(&variant).Error
			if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
			if err == nil && !variant.DeletedAt.Valid {
				destination := filepath.Join(root, relative)
				if _, statErr := os.Stat(destination); statErr == nil {
					return errors.New("cannot recover staged delete because destination already exists")
				} else if !errors.Is(statErr, os.ErrNotExist) {
					return statErr
				}
				if err := os.MkdirAll(filepath.Dir(destination), 0o755); err != nil {
					return err
				}
				return os.Rename(stagedPath, destination)
			}
			return os.Remove(stagedPath)
		})
		if err != nil {
			return err
		}
		if err := os.RemoveAll(operationDir); err != nil {
			return err
		}
	}
	return nil
}
