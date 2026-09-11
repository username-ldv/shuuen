package storage

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"mime/multipart"
	"os"
	"path/filepath"
	"strings"

	"shuuen-backend/internal/config"
)

var allowedExtensions = map[string]map[string]bool{
	"midi": {
		".mid":  true,
		".midi": true,
	},
	"musicxml": {
		".musicxml": true,
		".mxl":      true,
		".xml":      true,
	},
}

type FileStore struct {
	root           string
	maxUploadBytes int64
}

type StoredFile struct {
	OriginalName string
	StoredName   string
	StoragePath  string
	MimeType     string
	SizeBytes    int64
	ChecksumSHA  string
	FileModTime  int64
}

type StagedDelete struct {
	original     string
	staged       string
	operationDir string
	missing      bool
}

func NewFileStore(cfg config.CatalogConfig) (*FileStore, error) {
	root, err := filepath.Abs(cfg.Root)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(root, 0o755); err != nil {
		return nil, err
	}
	root, err = filepath.EvalSymlinks(root)
	if err != nil {
		return nil, err
	}
	return &FileStore{
		root:           root,
		maxUploadBytes: cfg.MaxUploadBytes,
	}, nil
}

func (s *FileStore) Root() string {
	return s.root
}

func (s *FileStore) Check() error {
	info, err := os.Stat(s.root)
	if err != nil {
		return err
	}
	if !info.IsDir() {
		return errors.New("storage root is not a directory")
	}
	return nil
}

func (s *FileStore) SaveVariant(groupPath string, fileStem string, format string, header *multipart.FileHeader) (StoredFile, error) {
	format = NormalizeFormat(format)
	if format == "" {
		return StoredFile{}, errors.New("format is required")
	}
	if header == nil {
		return StoredFile{}, errors.New("file is required")
	}
	if header.Size > s.maxUploadBytes {
		return StoredFile{}, fmt.Errorf("file is too large; maximum is %d bytes", s.maxUploadBytes)
	}

	originalName := filepath.Base(header.Filename)
	ext := strings.ToLower(filepath.Ext(originalName))
	if !IsAllowedExtension(format, ext) {
		return StoredFile{}, fmt.Errorf("extension %q is not allowed for %s files", ext, format)
	}
	if strings.TrimSpace(fileStem) == "" {
		fileStem = strings.TrimSuffix(originalName, filepath.Ext(originalName))
	}

	src, err := header.Open()
	if err != nil {
		return StoredFile{}, err
	}
	defer src.Close()

	relativeDir, err := cleanRelativePath(groupPath)
	if err != nil {
		return StoredFile{}, err
	}
	dir := filepath.Join(s.root, filepath.FromSlash(relativeDir))
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return StoredFile{}, err
	}
	resolvedDir, err := filepath.EvalSymlinks(dir)
	if err != nil || !pathWithin(s.root, resolvedDir) {
		return StoredFile{}, errors.New("invalid storage path")
	}
	dir = resolvedDir

	storedName := safeFileStem(fileStem) + ext
	destPath := filepath.Join(dir, storedName)
	dest, err := os.OpenFile(destPath, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o644)
	if err != nil {
		if errors.Is(err, os.ErrExist) {
			return StoredFile{}, errors.New("a variant file with that name already exists")
		}
		return StoredFile{}, err
	}

	hash := sha256.New()
	written, err := io.Copy(io.MultiWriter(dest, hash), io.LimitReader(src, s.maxUploadBytes+1))
	if err != nil {
		_ = dest.Close()
		_ = os.Remove(destPath)
		return StoredFile{}, err
	}
	if written > s.maxUploadBytes {
		_ = dest.Close()
		_ = os.Remove(destPath)
		return StoredFile{}, fmt.Errorf("file is too large; maximum is %d bytes", s.maxUploadBytes)
	}
	if err := dest.Sync(); err != nil {
		_ = dest.Close()
		_ = os.Remove(destPath)
		return StoredFile{}, err
	}
	info, err := dest.Stat()
	if err != nil {
		_ = dest.Close()
		_ = os.Remove(destPath)
		return StoredFile{}, err
	}
	if err := dest.Close(); err != nil {
		_ = os.Remove(destPath)
		return StoredFile{}, err
	}

	relPath, err := filepath.Rel(s.root, destPath)
	if err != nil {
		_ = os.Remove(destPath)
		return StoredFile{}, err
	}

	return StoredFile{
		OriginalName: originalName,
		StoredName:   storedName,
		StoragePath:  filepath.ToSlash(relPath),
		MimeType:     MimeTypeForExtension(ext),
		SizeBytes:    written,
		ChecksumSHA:  hex.EncodeToString(hash.Sum(nil)),
		FileModTime:  info.ModTime().UnixNano(),
	}, nil
}

func (s *FileStore) AbsolutePath(storagePath string) (string, error) {
	clean, err := cleanRelativePath(storagePath)
	if err != nil {
		return "", err
	}
	absolute := filepath.Join(s.root, filepath.FromSlash(clean))
	if resolved, resolveErr := filepath.EvalSymlinks(absolute); resolveErr == nil {
		absolute = resolved
	} else if errors.Is(resolveErr, os.ErrNotExist) {
		parent, parentErr := filepath.EvalSymlinks(filepath.Dir(absolute))
		if parentErr == nil {
			absolute = filepath.Join(parent, filepath.Base(absolute))
		} else if !errors.Is(parentErr, os.ErrNotExist) {
			return "", parentErr
		}
	} else {
		return "", resolveErr
	}
	if !pathWithin(s.root, absolute) {
		return "", errors.New("invalid storage path")
	}
	return absolute, nil
}

func (s *FileStore) Delete(storagePath string) error {
	absolute, err := s.AbsolutePath(storagePath)
	if err != nil {
		return err
	}
	err = os.Remove(absolute)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}

// StageDelete atomically moves a file out of the indexed tree. Rollback can
// restore it if the accompanying database transaction fails; Commit removes
// the staged file after the database transaction succeeds.
func (s *FileStore) StageDelete(storagePath string) (*StagedDelete, error) {
	clean, err := cleanRelativePath(storagePath)
	if err != nil {
		return nil, err
	}
	if clean == "" {
		return nil, errors.New("invalid storage path")
	}
	absolute, err := s.AbsolutePath(storagePath)
	if err != nil {
		return nil, err
	}
	if _, err := os.Stat(absolute); errors.Is(err, os.ErrNotExist) {
		return &StagedDelete{missing: true}, nil
	} else if err != nil {
		return nil, err
	}

	token := make([]byte, 16)
	if _, err := rand.Read(token); err != nil {
		return nil, err
	}
	operationDir := filepath.Join(s.root, ".trash", hex.EncodeToString(token))
	staged := filepath.Join(operationDir, filepath.FromSlash(clean))
	if err := os.MkdirAll(filepath.Dir(staged), 0o700); err != nil {
		return nil, err
	}
	if err := os.Rename(absolute, staged); err != nil {
		return nil, err
	}
	return &StagedDelete{original: absolute, staged: staged, operationDir: operationDir}, nil
}

func (d *StagedDelete) Rollback() error {
	if d == nil || d.missing {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(d.original), 0o755); err != nil {
		return err
	}
	if err := os.Rename(d.staged, d.original); err != nil {
		return err
	}
	return os.RemoveAll(d.operationDir)
}

func (d *StagedDelete) Commit() error {
	if d == nil || d.missing {
		return nil
	}
	if err := os.Remove(d.staged); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	return os.RemoveAll(d.operationDir)
}

func NormalizeFormat(format string) string {
	format = strings.ToLower(strings.TrimSpace(format))
	switch format {
	case "mid", "midi":
		return "midi"
	case "musicxml", "xml", "mxl":
		return "musicxml"
	default:
		return format
	}
}

func IsAllowedFormat(format string) bool {
	_, ok := allowedExtensions[NormalizeFormat(format)]
	return ok
}

func IsAllowedExtension(format string, ext string) bool {
	allowed, ok := allowedExtensions[NormalizeFormat(format)]
	if !ok {
		return false
	}
	return allowed[strings.ToLower(ext)]
}

func InferFormat(filename string) string {
	ext := strings.ToLower(filepath.Ext(filename))
	for format, extensions := range allowedExtensions {
		if extensions[ext] {
			return format
		}
	}
	return ""
}

func MimeTypeForExtension(ext string) string {
	switch strings.ToLower(ext) {
	case ".mid", ".midi":
		return "audio/midi"
	case ".musicxml":
		return "application/vnd.recordare.musicxml+xml"
	case ".mxl":
		return "application/vnd.recordare.musicxml"
	case ".xml":
		return "application/xml"
	default:
		return "application/octet-stream"
	}
}

func cleanRelativePath(value string) (string, error) {
	value = filepath.ToSlash(strings.TrimSpace(value))
	if value == "" || value == "." {
		return "", nil
	}
	if strings.HasPrefix(value, "/") {
		return "", errors.New("invalid storage path")
	}
	clean := filepath.ToSlash(filepath.Clean(filepath.FromSlash(value)))
	if clean == "." {
		return "", nil
	}
	if clean == ".." || strings.HasPrefix(clean, "../") {
		return "", errors.New("invalid storage path")
	}
	return clean, nil
}

func safeFileStem(value string) string {
	value = strings.TrimSpace(value)
	value = strings.TrimSuffix(value, filepath.Ext(value))
	value = strings.ReplaceAll(value, "/", "-")
	value = strings.ReplaceAll(value, "\\", "-")
	if value == "" || value == "." || value == ".." {
		return "melody"
	}
	return value
}

func pathWithin(root string, target string) bool {
	rel, err := filepath.Rel(root, target)
	return err == nil && rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator))
}
