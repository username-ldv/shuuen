package model

import (
	"time"

	"gorm.io/gorm"
)

const (
	CourseStructureBlueprint = "blueprint"
	CourseStructureManaged   = "managed"
)

// Course is the editable metadata overlay for a top-level library group. A
// top-level group without a row here is still exposed as a blueprint course.
// The public course id is deliberately the library group id so promotion from
// a folder blueprint never changes URLs or client-side identity.
type Course struct {
	ID              uint           `gorm:"primaryKey;autoIncrement:false" json:"id"`
	CreatedAt       time.Time      `json:"created_at"`
	UpdatedAt       time.Time      `json:"updated_at"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	Name            string         `gorm:"size:180;not null" json:"name"`
	Description     string         `gorm:"type:text" json:"description"`
	Author          string         `gorm:"size:180" json:"author"`
	IsPublic        bool           `gorm:"not null" json:"is_public"`
	SortOrder       int            `gorm:"not null;default:0" json:"sort_order"`
	StructureSource string         `gorm:"size:24;not null;default:blueprint" json:"structure_source"`
	Modes           []CourseMode   `gorm:"foreignKey:CourseID" json:"-"`
}

type CourseMode struct {
	ID             uint                     `gorm:"primaryKey" json:"id"`
	CreatedAt      time.Time                `json:"created_at"`
	UpdatedAt      time.Time                `json:"updated_at"`
	DeletedAt      gorm.DeletedAt           `gorm:"index" json:"-"`
	CourseID       uint                     `gorm:"not null;uniqueIndex:idx_course_modes_course_mode;index:idx_course_modes_order,priority:1" json:"course_id"`
	Mode           string                   `gorm:"size:40;not null;uniqueIndex:idx_course_modes_course_mode;index:idx_course_modes_order,priority:2" json:"mode"`
	LibraryGroupID uint                     `gorm:"not null;index" json:"library_group_id"`
	LibraryGroup   LibraryGroup             `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;" json:"-"`
	Name           string                   `gorm:"size:180;not null" json:"name"`
	Description    string                   `gorm:"type:text" json:"description"`
	SortOrder      int                      `gorm:"not null;default:0;index:idx_course_modes_order,priority:3" json:"sort_order"`
	Groups         []CourseProgressionGroup `gorm:"foreignKey:CourseModeID" json:"-"`
}

type CourseProgressionGroup struct {
	ID             uint           `gorm:"primaryKey" json:"-"`
	CreatedAt      time.Time      `json:"created_at"`
	UpdatedAt      time.Time      `json:"updated_at"`
	DeletedAt      gorm.DeletedAt `gorm:"index" json:"-"`
	CourseModeID   uint           `gorm:"not null;index:idx_course_groups_order,priority:1" json:"course_mode_id"`
	PublicID       string         `gorm:"size:64;uniqueIndex;not null" json:"id"`
	LibraryGroupID uint           `gorm:"not null;index" json:"library_group_id"`
	LibraryGroup   LibraryGroup   `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;" json:"-"`
	Name           string         `gorm:"size:180;not null" json:"name"`
	Description    string         `gorm:"type:text" json:"description"`
	SortOrder      int            `gorm:"not null;default:0;index:idx_course_groups_order,priority:2" json:"sort_order"`
	Levels         []CourseLevel  `gorm:"foreignKey:ProgressionGroupID" json:"-"`
}

type CourseLevel struct {
	ID                    string                 `gorm:"size:64;primaryKey" json:"id"`
	CreatedAt             time.Time              `json:"created_at"`
	UpdatedAt             time.Time              `json:"updated_at"`
	DeletedAt             gorm.DeletedAt         `gorm:"index" json:"-"`
	ProgressionGroupID    uint                   `gorm:"not null;index:idx_course_levels_order,priority:1" json:"-"`
	ProgressionGroup      CourseProgressionGroup `gorm:"constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"-"`
	Name                  string                 `gorm:"size:220;not null" json:"name"`
	Source                string                 `gorm:"size:40;not null;default:imported" json:"source"`
	Definition            JSONDocument           `gorm:"not null" json:"definition"`
	SortOrder             int                    `gorm:"not null;default:0;index:idx_course_levels_order,priority:2" json:"sort_order"`
	IsPublic              bool                   `gorm:"not null" json:"is_public"`
	LibraryMelodyID       *uint                  `gorm:"index" json:"library_melody_id,omitempty"`
	LibraryMelody         *Melody                `gorm:"constraint:OnUpdate:CASCADE,OnDelete:SET NULL;" json:"-"`
	LibraryVariantID      *uint                  `gorm:"index" json:"library_variant_id,omitempty"`
	LibraryVariant        *FileVariant           `gorm:"constraint:OnUpdate:CASCADE,OnDelete:SET NULL;" json:"-"`
	SectionLibraryGroupID *uint                  `gorm:"index" json:"section_library_group_id,omitempty"`
	SectionLibraryGroup   *LibraryGroup          `gorm:"constraint:OnUpdate:CASCADE,OnDelete:SET NULL;" json:"-"`
}
