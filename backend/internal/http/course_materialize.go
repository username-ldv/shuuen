package httpapi

import (
	"errors"
	"fmt"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
)

type materializedBlueprintRow struct {
	MelodyID       uint
	Title          string
	MelodyPublic   bool
	SectionGroupID uint
	SectionPath    string
	SectionPublic  bool
	VariantID      uint
	OriginalName   string
}

// ensureManagedCourse turns a folder blueprint into editable rows inside the
// server. Public ids are derived from the original library ids, so the client
// can issue an edit using the id it just read without a separate conversion
// round trip. No audio files are moved or copied.
func (h *Handler) ensureManagedCourse(c fiber.Ctx, loaded loadedCourse) (loadedCourse, error) {
	if !loaded.isBlueprint() {
		return loaded, nil
	}
	var courseRecord model.Course
	err := h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		var lockedGroup model.LibraryGroup
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("id = ?", loaded.Group.ID).First(&lockedGroup).Error; err != nil {
			return err
		}
		var current model.Course
		findCourseErr := tx.Where("id = ? AND deleted_at IS NULL", loaded.Group.ID).First(&current).Error
		if findCourseErr == nil && current.StructureSource == model.CourseStructureManaged {
			courseRecord = current
			return nil
		}
		if findCourseErr != nil && !errors.Is(findCourseErr, gorm.ErrRecordNotFound) {
			return findCourseErr
		}
		if findCourseErr != nil {
			courseRecord = model.Course{
				ID: loaded.Group.ID, Name: loaded.Group.Name, Description: loaded.Group.Description,
				IsPublic: loaded.Group.IsPublic, SortOrder: loaded.Group.SortOrder,
				StructureSource: model.CourseStructureBlueprint,
			}
			if err := tx.Omit("LibraryGroup", "Modes").Create(&courseRecord).Error; err != nil {
				return err
			}
		} else {
			courseRecord = current
		}

		var existingModes int64
		if err := tx.Model(&model.CourseMode{}).Where("course_id = ?", loaded.Group.ID).Count(&existingModes).Error; err != nil {
			return err
		}
		if existingModes != 0 {
			return fmt.Errorf("blueprint course already contains managed modes")
		}

		mode := model.CourseMode{
			CourseID: loaded.Group.ID, Mode: coursedomain.ModeMelodies,
			LibraryGroupID: loaded.Group.ID, Name: coursedomain.DefaultModeName(coursedomain.ModeMelodies),
		}
		if err := tx.Omit("LibraryGroup", "Groups").Create(&mode).Error; err != nil {
			return err
		}

		var rows []materializedBlueprintRow
		query := tx.Table("melodies AS m").
			Joins("JOIN library_groups AS g ON g.id = m.group_id AND g.deleted_at IS NULL").
			Joins(`JOIN file_variants AS fv ON fv.id = (
				SELECT candidate.id FROM file_variants AS candidate
				WHERE candidate.melody_id = m.id AND candidate.format = 'midi' AND candidate.deleted_at IS NULL
				ORDER BY candidate.is_primary DESC, candidate.created_at ASC, candidate.id ASC LIMIT 1
			)`).
			Where("m.deleted_at IS NULL").
			Where("g.path = ? OR g.path LIKE ? ESCAPE '\\'", loaded.Group.Path, descendantPattern(loaded.Group.Path)).
			Select(`m.id AS melody_id, m.title, m.is_public AS melody_public,
				g.id AS section_group_id, g.path AS section_path, g.is_public AS section_public,
				fv.id AS variant_id, fv.original_name`).
			Order("g.path asc, m.sort_order asc, m.title asc, m.id asc")
		if err := query.Scan(&rows).Error; err != nil {
			return err
		}

		var children []model.LibraryGroup
		if err := tx.Where("parent_id = ? AND deleted_at IS NULL", loaded.Group.ID).
			Order("sort_order asc, name asc, id asc").Find(&children).Error; err != nil {
			return err
		}
		hasDirectLevels := false
		for _, row := range rows {
			if row.SectionGroupID == loaded.Group.ID {
				hasDirectLevels = true
				break
			}
		}

		groupsByPath := make(map[string]model.CourseProgressionGroup, len(children)+1)
		groups := make([]model.CourseProgressionGroup, 0, len(children)+1)
		if hasDirectLevels {
			groups = append(groups, model.CourseProgressionGroup{
				CourseModeID: mode.ID, PublicID: blueprintGroupID(loaded.Group.ID),
				LibraryGroupID: loaded.Group.ID, Name: "Default", SortOrder: -1,
			})
		}
		for _, child := range children {
			groups = append(groups, model.CourseProgressionGroup{
				CourseModeID: mode.ID, PublicID: blueprintGroupID(child.ID), LibraryGroupID: child.ID,
				Name: child.Name, Description: child.Description, SortOrder: child.SortOrder,
			})
		}
		for index := range groups {
			if err := tx.Omit("LibraryGroup", "Levels").Create(&groups[index]).Error; err != nil {
				return err
			}
			libraryGroup, err := groupByID(tx, groups[index].LibraryGroupID)
			if err != nil {
				return err
			}
			groupsByPath[libraryGroup.Path] = groups[index]
		}

		positions := map[uint]int{}
		levels := make([]model.CourseLevel, 0, len(rows))
		for _, row := range rows {
			rootPath := loaded.Group.Path
			if row.SectionPath != loaded.Group.Path {
				relative := strings.TrimPrefix(row.SectionPath, loaded.Group.Path+"/")
				rootPath += "/" + strings.Split(relative, "/")[0]
			}
			progression, ok := groupsByPath[rootPath]
			if !ok {
				return fmt.Errorf("could not map library melody %d to a progression group", row.MelodyID)
			}
			definition := coursedomain.DefaultMIDIDefinition(row.MelodyID, row.VariantID, row.OriginalName)
			melodyID, variantID, sectionID := row.MelodyID, row.VariantID, row.SectionGroupID
			levels = append(levels, model.CourseLevel{
				ID: blueprintLevelID(row.MelodyID), ProgressionGroupID: progression.ID,
				Name: row.Title, Source: "imported", Definition: model.JSONDocument(definition),
				SortOrder: positions[progression.ID], IsPublic: row.MelodyPublic && row.SectionPublic,
				LibraryMelodyID: &melodyID, LibraryVariantID: &variantID, SectionLibraryGroupID: &sectionID,
			})
			positions[progression.ID]++
		}
		if len(levels) > 0 {
			if err := tx.Omit("ProgressionGroup", "LibraryMelody", "LibraryVariant", "SectionLibraryGroup").
				CreateInBatches(&levels, 100).Error; err != nil {
				return err
			}
		}
		if err := tx.Model(&model.Course{}).Where("id = ?", loaded.Group.ID).
			Update("structure_source", model.CourseStructureManaged).Error; err != nil {
			return err
		}
		courseRecord.StructureSource = model.CourseStructureManaged
		return nil
	})
	if err != nil {
		return loadedCourse{}, err
	}
	loaded.Course = &courseRecord
	return loaded, nil
}

func groupByID(db *gorm.DB, id uint) (model.LibraryGroup, error) {
	var group model.LibraryGroup
	err := db.Where("id = ? AND deleted_at IS NULL", id).First(&group).Error
	return group, err
}
