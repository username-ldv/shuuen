package model

import (
	"time"

	"gorm.io/gorm"
)

type Base struct {
	ID        uint           `gorm:"primaryKey" json:"id"`
	CreatedAt time.Time      `json:"created_at"`
	UpdatedAt time.Time      `json:"updated_at"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
}

type User struct {
	Base
	Username     string `gorm:"size:20;not null" json:"username"`
	UsernameKey  string `gorm:"size:20;uniqueIndex;not null" json:"-"`
	DisplayName  string `gorm:"size:160" json:"display_name"`
	PasswordHash string `gorm:"size:255;not null" json:"-"`
	Role         string `gorm:"size:40;not null;default:user" json:"role"`
	TokenVersion uint   `gorm:"not null;default:1" json:"-"`
}

type LibraryGroup struct {
	Base
	ParentID    *uint          `gorm:"index" json:"parent_id"`
	Parent      *LibraryGroup  `gorm:"constraint:OnUpdate:CASCADE,OnDelete:SET NULL;" json:"parent,omitempty"`
	Path        string         `gorm:"size:640;uniqueIndex;not null" json:"path"`
	Name        string         `gorm:"size:180;not null" json:"name"`
	Slug        string         `gorm:"size:220;not null;index" json:"slug"`
	Description string         `gorm:"type:text" json:"description"`
	SortOrder   int            `gorm:"not null;default:0" json:"sort_order"`
	IsPublic    bool           `gorm:"not null" json:"is_public"`
	ScanID      string         `gorm:"size:64;index" json:"-"`
	Tags        []Tag          `gorm:"many2many:group_tags;" json:"tags,omitempty"`
	Children    []LibraryGroup `gorm:"foreignKey:ParentID" json:"children,omitempty"`
	Melodies    []Melody       `gorm:"foreignKey:GroupID" json:"melodies,omitempty"`
}

type Tag struct {
	Base
	Name     string         `gorm:"size:80;uniqueIndex;not null" json:"name"`
	Slug     string         `gorm:"size:100;uniqueIndex;not null" json:"slug"`
	Color    string         `gorm:"size:24" json:"color"`
	Melodies []Melody       `gorm:"many2many:melody_tags;" json:"-"`
	Groups   []LibraryGroup `gorm:"many2many:group_tags;" json:"-"`
}

type Melody struct {
	Base
	GroupID     uint          `gorm:"not null;index" json:"group_id"`
	Group       LibraryGroup  `gorm:"constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"group,omitempty"`
	SourcePath  string        `gorm:"size:640;uniqueIndex;not null" json:"source_path"`
	FileStem    string        `gorm:"size:260;not null" json:"file_stem"`
	Title       string        `gorm:"size:220;not null" json:"title"`
	Slug        string        `gorm:"size:260;not null;index" json:"slug"`
	Description string        `gorm:"type:text" json:"description"`
	Composer    string        `gorm:"size:180" json:"composer"`
	Difficulty  string        `gorm:"size:80" json:"difficulty"`
	SortOrder   int           `gorm:"not null;default:0" json:"sort_order"`
	IsPublic    bool          `gorm:"not null" json:"is_public"`
	ScanID      string        `gorm:"size:64;index" json:"-"`
	Tags        []Tag         `gorm:"many2many:melody_tags;" json:"tags,omitempty"`
	Variants    []FileVariant `gorm:"foreignKey:MelodyID" json:"variants,omitempty"`
}

type FileVariant struct {
	Base
	MelodyID     uint   `gorm:"not null;index" json:"melody_id"`
	Melody       Melody `gorm:"constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"melody,omitempty"`
	Format       string `gorm:"size:40;not null;index" json:"format"`
	OriginalName string `gorm:"size:255;not null" json:"original_name"`
	StoredName   string `gorm:"size:255;not null" json:"stored_name"`
	StoragePath  string `gorm:"size:640;uniqueIndex;not null" json:"storage_path"`
	MimeType     string `gorm:"size:120" json:"mime_type"`
	SizeBytes    int64  `gorm:"not null" json:"size_bytes"`
	FileModTime  int64  `gorm:"not null;default:0" json:"-"`
	ChecksumSHA  string `gorm:"size:64;not null;index" json:"checksum_sha256"`
	IsPrimary    bool   `gorm:"not null;default:false" json:"is_primary"`
	ScanID       string `gorm:"size:64;index" json:"-"`
}
