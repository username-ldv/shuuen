package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"path"
	"strings"
	"unicode/utf8"

	"github.com/gofiber/fiber/v3"
	"github.com/google/uuid"
	"gorm.io/gorm"

	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
	"shuuen-backend/internal/util"
)

type createCourseRequest struct {
	Name        string `json:"name"`
	Slug        string `json:"slug"`
	Description string `json:"description"`
	Author      string `json:"author"`
	IsPublic    *bool  `json:"is_public"`
	SortOrder   int    `json:"sort_order"`
}

type updateCourseRequest struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	Author      string `json:"author"`
	IsPublic    *bool  `json:"is_public"`
	SortOrder   int    `json:"sort_order"`
}

type createCourseModeRequest struct {
	Mode        string `json:"mode"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Position    *int   `json:"position"`
}

type updateCourseModeRequest struct {
	Name        string `json:"name"`
	Description string `json:"description"`
}

type createProgressionGroupRequest struct {
	Name        string `json:"name"`
	Slug        string `json:"slug"`
	Description string `json:"description"`
	Position    *int   `json:"position"`
}

type updateProgressionGroupRequest struct {
	Name        string `json:"name"`
	Description string `json:"description"`
}

type createCourseLevelRequest struct {
	ID         string          `json:"id"`
	GroupID    string          `json:"group_id"`
	Name       string          `json:"name"`
	Source     string          `json:"source"`
	Definition json.RawMessage `json:"definition"`
	IsPublic   *bool           `json:"is_public"`
	Position   *int            `json:"position"`
}

type updateCourseLevelRequest struct {
	Name       string          `json:"name"`
	Source     string          `json:"source"`
	Definition json.RawMessage `json:"definition"`
	IsPublic   *bool           `json:"is_public"`
}

type positionRequest struct {
	Position int `json:"position"`
}

type levelPositionRequest struct {
	GroupID  string `json:"group_id"`
	Position int    `json:"position"`
}

func (h *Handler) CreateCourse(c fiber.Ctx) error {
	var request createCourseRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	request.Name = strings.TrimSpace(request.Name)
	request.Description = strings.TrimSpace(request.Description)
	request.Author = strings.TrimSpace(request.Author)
	if request.Name == "" || runeLen(request.Name) > 180 || runeLen(request.Description) > 20_000 || runeLen(request.Author) > 180 {
		return sendError(c, fiber.StatusBadRequest, "course name or metadata is invalid")
	}
	slug := util.Slugify(request.Slug)
	if slug == "" {
		slug = util.Slugify(request.Name)
	}
	if slug == "" || len(slug) > 220 {
		return sendError(c, fiber.StatusBadRequest, "course slug is invalid")
	}
	isPublic := false
	if request.IsPublic != nil {
		isPublic = *request.IsPublic
	}
	root, err := gorm.G[model.LibraryGroup](h.db).Where("path = ?", "").First(c.Context())
	if err != nil {
		return err
	}
	groupPath := path.Join(root.Path, slug)
	rollback, err := h.createCourseGroupFolder(groupPath, courseGroupMetadata{
		Name: request.Name, Description: request.Description, SortOrder: request.SortOrder, IsPublic: isPublic,
	})
	if err != nil {
		return sendError(c, fiber.StatusConflict, err.Error())
	}
	created := false
	defer func() {
		if !created {
			_ = rollback()
		}
	}()
	var group model.LibraryGroup
	err = h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		group = model.LibraryGroup{
			ParentID: &root.ID, Path: groupPath, Name: request.Name, Slug: slug,
			Description: request.Description, SortOrder: request.SortOrder, IsPublic: isPublic, ScanID: "api",
		}
		if err := tx.Omit("Parent", "Tags", "Children", "Melodies").Create(&group).Error; err != nil {
			return err
		}
		course := model.Course{
			ID: group.ID, Name: request.Name, Description: request.Description, Author: request.Author,
			IsPublic: isPublic, SortOrder: request.SortOrder, StructureSource: model.CourseStructureManaged,
		}
		return tx.Omit("LibraryGroup", "Modes").Create(&course).Error
	})
	if err != nil {
		if isUniqueConstraint(err) {
			return sendError(c, fiber.StatusConflict, "course slug already exists")
		}
		return err
	}
	created = true
	loaded := loadedCourse{Group: group, Course: &model.Course{
		ID: group.ID, Name: request.Name, Description: request.Description, Author: request.Author,
		IsPublic: isPublic, SortOrder: request.SortOrder, StructureSource: model.CourseStructureManaged,
	}}
	response, err := h.courseResponse(c, loaded, true)
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusCreated, response)
}

func (h *Handler) UpdateCourse(c fiber.Ctx) error {
	courseID, err := parseParamUint(c, "course_id")
	if err != nil {
		return err
	}
	loaded, err := h.loadCourseForAdmin(c, courseID)
	if err != nil {
		return notFoundOrError(c, err, "course not found")
	}
	var request updateCourseRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	request.Name = strings.TrimSpace(request.Name)
	request.Description = strings.TrimSpace(request.Description)
	request.Author = strings.TrimSpace(request.Author)
	if request.Name == "" || request.IsPublic == nil || runeLen(request.Name) > 180 || runeLen(request.Description) > 20_000 || runeLen(request.Author) > 180 {
		return sendError(c, fiber.StatusBadRequest, "name and is_public are required and metadata must be within limits")
	}
	previousMetadata := courseGroupMetadata{
		Name: loaded.Group.Name, Description: loaded.Group.Description,
		SortOrder: loaded.Group.SortOrder, IsPublic: loaded.Group.IsPublic,
	}
	metadata := courseGroupMetadata{
		Name: request.Name, Description: request.Description,
		SortOrder: request.SortOrder, IsPublic: *request.IsPublic,
	}
	if err := h.updateCourseGroupMetadata(loaded.Group.Path, metadata); err != nil {
		return err
	}
	dbUpdated := false
	defer func() {
		if !dbUpdated {
			_ = h.updateCourseGroupMetadata(loaded.Group.Path, previousMetadata)
		}
	}()
	structureSource := model.CourseStructureBlueprint
	if loaded.Course != nil {
		structureSource = loaded.Course.StructureSource
	}
	err = h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&model.LibraryGroup{}).Where("id = ?", loaded.Group.ID).Updates(map[string]any{
			"name": request.Name, "description": request.Description,
			"sort_order": request.SortOrder, "is_public": *request.IsPublic,
		}).Error; err != nil {
			return err
		}
		course := model.Course{
			ID: loaded.Group.ID, Name: request.Name, Description: request.Description,
			Author: request.Author, IsPublic: *request.IsPublic, SortOrder: request.SortOrder,
			StructureSource: structureSource,
		}
		if loaded.Course == nil {
			return tx.Omit("LibraryGroup", "Modes").Create(&course).Error
		}
		return tx.Model(&model.Course{}).Where("id = ?", loaded.Group.ID).Updates(map[string]any{
			"name": request.Name, "description": request.Description, "author": request.Author,
			"is_public": *request.IsPublic, "sort_order": request.SortOrder,
		}).Error
	})
	if err != nil {
		return err
	}
	dbUpdated = true
	if loaded.Group.IsPublic != *request.IsPublic {
		if _, err := h.catalog.Scan(c.Context()); err != nil {
			return fmt.Errorf("course saved but catalog visibility reconciliation failed: %w", err)
		}
	}
	return h.GetCourseAdminResponse(c, courseID)
}

func (h *Handler) GetCourseAdminResponse(c fiber.Ctx, courseID uint) error {
	loaded, err := h.loadCourseForAdmin(c, courseID)
	if err != nil {
		return err
	}
	response, err := h.courseResponse(c, loaded, true)
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, response)
}

func (h *Handler) CreateCourseMode(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	_ = modeName
	var request createCourseModeRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	request.Mode = coursedomain.NormalizeMode(request.Mode)
	request.Name = strings.TrimSpace(request.Name)
	request.Description = strings.TrimSpace(request.Description)
	if !coursedomain.IsValidMode(request.Mode) {
		return sendError(c, fiber.StatusBadRequest, "unsupported course mode")
	}
	if request.Name == "" {
		request.Name = coursedomain.DefaultModeName(request.Mode)
	}
	if runeLen(request.Name) > 180 || runeLen(request.Description) > 20_000 {
		return sendError(c, fiber.StatusBadRequest, "mode metadata is too long")
	}
	if request.Position != nil && *request.Position < 0 {
		return sendError(c, fiber.StatusBadRequest, "position must be zero or greater")
	}
	if _, err := h.findManagedMode(c, loaded.Group.ID, request.Mode); err == nil {
		return sendError(c, fiber.StatusConflict, "course mode already exists")
	} else if !errors.Is(err, gorm.ErrRecordNotFound) {
		return err
	}
	position, err := requestedPosition(h.db.WithContext(c.Context()).Model(&model.CourseMode{}).Where("course_id = ?", loaded.Group.ID), request.Position)
	if err != nil {
		return err
	}
	groupPath := path.Join(loaded.Group.Path, request.Mode)
	rollback, err := h.createCourseGroupFolder(groupPath, courseGroupMetadata{
		Name: request.Name, Description: request.Description, SortOrder: position, IsPublic: loaded.Group.IsPublic,
	})
	if err != nil {
		return sendError(c, fiber.StatusConflict, err.Error())
	}
	created := false
	defer func() {
		if !created {
			_ = rollback()
		}
	}()
	var mode model.CourseMode
	err = h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		if err := shiftPositions(tx, &model.CourseMode{}, "course_id = ?", []any{loaded.Group.ID}, position); err != nil {
			return err
		}
		libraryGroup := model.LibraryGroup{
			ParentID: &loaded.Group.ID, Path: groupPath, Name: request.Name, Slug: request.Mode,
			Description: request.Description, SortOrder: position, IsPublic: loaded.Group.IsPublic, ScanID: "api",
		}
		if err := tx.Omit("Parent", "Tags", "Children", "Melodies").Create(&libraryGroup).Error; err != nil {
			return err
		}
		mode = model.CourseMode{
			CourseID: loaded.Group.ID, Mode: request.Mode, LibraryGroupID: libraryGroup.ID,
			Name: request.Name, Description: request.Description, SortOrder: position,
		}
		return tx.Omit("LibraryGroup", "Groups").Create(&mode).Error
	})
	if err != nil {
		if isUniqueConstraint(err) {
			return sendError(c, fiber.StatusConflict, "course mode already exists")
		}
		return err
	}
	created = true
	return sendData(c, fiber.StatusCreated, courseModeResponse{
		Mode: mode.Mode, Name: mode.Name, Description: mode.Description, SortOrder: mode.SortOrder,
		GroupCount: 0, LevelCount: 0, Groups: []progressionGroupResponse{},
	})
}

func (h *Handler) UpdateCourseMode(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	var request updateCourseModeRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	request.Name, request.Description = strings.TrimSpace(request.Name), strings.TrimSpace(request.Description)
	if request.Name == "" || runeLen(request.Name) > 180 || runeLen(request.Description) > 20_000 {
		return sendError(c, fiber.StatusBadRequest, "mode metadata is invalid")
	}
	if err := h.db.WithContext(c.Context()).Model(&model.CourseMode{}).Where("id = ?", mode.ID).
		Updates(map[string]any{"name": request.Name, "description": request.Description}).Error; err != nil {
		return err
	}
	mode.Name, mode.Description = request.Name, request.Description
	groups, err := h.managedGroups(c, mode)
	if err != nil {
		return err
	}
	response := courseModeResponse{Mode: mode.Mode, Name: mode.Name, Description: mode.Description, SortOrder: mode.SortOrder, GroupCount: len(groups), Groups: groups}
	for _, group := range groups {
		response.LevelCount += group.LevelCount
	}
	return sendData(c, fiber.StatusOK, response)
}

func (h *Handler) PositionCourseMode(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	var request positionRequest
	if err := c.Bind().Body(&request); err != nil || request.Position < 0 {
		return sendError(c, fiber.StatusBadRequest, "position must be zero or greater")
	}
	if err := reorderRecord(h.db.WithContext(c.Context()), &model.CourseMode{}, mode.ID, "course_id = ?", []any{loaded.Group.ID}, request.Position); err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) CreateProgressionGroup(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	var request createProgressionGroupRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	request.Name, request.Description = strings.TrimSpace(request.Name), strings.TrimSpace(request.Description)
	if request.Name == "" || runeLen(request.Name) > 180 || runeLen(request.Description) > 20_000 {
		return sendError(c, fiber.StatusBadRequest, "progression group metadata is invalid")
	}
	if request.Position != nil && *request.Position < 0 {
		return sendError(c, fiber.StatusBadRequest, "position must be zero or greater")
	}
	slug := util.Slugify(request.Slug)
	if slug == "" {
		slug = util.Slugify(request.Name)
	}
	if slug == "" || len(slug) > 220 {
		return sendError(c, fiber.StatusBadRequest, "progression group slug is invalid")
	}
	position, err := requestedPosition(h.db.WithContext(c.Context()).Model(&model.CourseProgressionGroup{}).Where("course_mode_id = ?", mode.ID), request.Position)
	if err != nil {
		return err
	}
	parentGroup, err := groupByID(h.db.WithContext(c.Context()), mode.LibraryGroupID)
	if err != nil {
		return err
	}
	groupPath := path.Join(parentGroup.Path, slug)
	rollback, err := h.createCourseGroupFolder(groupPath, courseGroupMetadata{
		Name: request.Name, Description: request.Description, SortOrder: position, IsPublic: parentGroup.IsPublic,
	})
	if err != nil {
		return sendError(c, fiber.StatusConflict, err.Error())
	}
	created := false
	defer func() {
		if !created {
			_ = rollback()
		}
	}()
	var progression model.CourseProgressionGroup
	err = h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		if err := shiftPositions(tx, &model.CourseProgressionGroup{}, "course_mode_id = ?", []any{mode.ID}, position); err != nil {
			return err
		}
		libraryGroup := model.LibraryGroup{
			ParentID: &parentGroup.ID, Path: groupPath, Name: request.Name, Slug: slug,
			Description: request.Description, SortOrder: position, IsPublic: parentGroup.IsPublic, ScanID: "api",
		}
		if err := tx.Omit("Parent", "Tags", "Children", "Melodies").Create(&libraryGroup).Error; err != nil {
			return err
		}
		progression = model.CourseProgressionGroup{
			CourseModeID: mode.ID, PublicID: uuid.NewString(), LibraryGroupID: libraryGroup.ID,
			Name: request.Name, Description: request.Description, SortOrder: position,
		}
		return tx.Omit("LibraryGroup", "Levels").Create(&progression).Error
	})
	if err != nil {
		if isUniqueConstraint(err) {
			return sendError(c, fiber.StatusConflict, "progression group slug already exists")
		}
		return err
	}
	created = true
	return sendData(c, fiber.StatusCreated, progressionGroupResponse{
		ID: progression.PublicID, LibraryGroupID: progression.LibraryGroupID,
		Name: progression.Name, Description: progression.Description, SortOrder: progression.SortOrder,
		LevelCount: 0, SectionCount: 0,
	})
}

func (h *Handler) UpdateProgressionGroup(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	group, err := h.findManagedGroup(c, mode.ID, c.Params("group_id"))
	if err != nil {
		return notFoundOrError(c, err, "progression group not found")
	}
	var request updateProgressionGroupRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	request.Name, request.Description = strings.TrimSpace(request.Name), strings.TrimSpace(request.Description)
	if request.Name == "" || runeLen(request.Name) > 180 || runeLen(request.Description) > 20_000 {
		return sendError(c, fiber.StatusBadRequest, "progression group metadata is invalid")
	}
	libraryGroup, err := groupByID(h.db.WithContext(c.Context()), group.LibraryGroupID)
	if err != nil {
		return err
	}
	previousMetadata := courseGroupMetadata{
		Name: libraryGroup.Name, Description: libraryGroup.Description,
		SortOrder: libraryGroup.SortOrder, IsPublic: libraryGroup.IsPublic,
	}
	if err := h.updateCourseGroupMetadata(libraryGroup.Path, courseGroupMetadata{
		Name: request.Name, Description: request.Description,
		SortOrder: libraryGroup.SortOrder, IsPublic: libraryGroup.IsPublic,
	}); err != nil {
		return err
	}
	dbUpdated := false
	defer func() {
		if !dbUpdated {
			_ = h.updateCourseGroupMetadata(libraryGroup.Path, previousMetadata)
		}
	}()
	if err := h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&model.LibraryGroup{}).Where("id = ?", libraryGroup.ID).
			Updates(map[string]any{"name": request.Name, "description": request.Description}).Error; err != nil {
			return err
		}
		return tx.Model(&model.CourseProgressionGroup{}).Where("id = ?", group.ID).
			Updates(map[string]any{"name": request.Name, "description": request.Description}).Error
	}); err != nil {
		return err
	}
	dbUpdated = true
	group.Name, group.Description = request.Name, request.Description
	groups, err := h.managedGroups(c, mode)
	if err != nil {
		return err
	}
	for _, response := range groups {
		if response.ID == group.PublicID {
			return sendData(c, fiber.StatusOK, response)
		}
	}
	return sendError(c, fiber.StatusNotFound, "progression group not found")
}

func (h *Handler) PositionProgressionGroup(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	group, err := h.findManagedGroup(c, mode.ID, c.Params("group_id"))
	if err != nil {
		return notFoundOrError(c, err, "progression group not found")
	}
	var request positionRequest
	if err := c.Bind().Body(&request); err != nil || request.Position < 0 {
		return sendError(c, fiber.StatusBadRequest, "position must be zero or greater")
	}
	if err := reorderRecord(h.db.WithContext(c.Context()), &model.CourseProgressionGroup{}, group.ID, "course_mode_id = ?", []any{mode.ID}, request.Position); err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) CreateCourseLevel(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	var request createCourseLevelRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	group, err := h.findManagedGroup(c, mode.ID, request.GroupID)
	if err != nil {
		return notFoundOrError(c, err, "progression group not found")
	}
	levelID := strings.TrimSpace(request.ID)
	if levelID == "" {
		levelID = uuid.NewString()
	} else if strings.HasPrefix(levelID, "library-") {
		return sendError(c, fiber.StatusBadRequest, "level ids beginning with library- are reserved for folder blueprints")
	}
	request.Name = strings.TrimSpace(request.Name)
	request.Source = strings.TrimSpace(request.Source)
	if request.IsPublic == nil {
		value := true
		request.IsPublic = &value
	}
	if err := validateLevelMetadata(levelID, request.Name, request.Source); err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}
	if request.Position != nil && *request.Position < 0 {
		return sendError(c, fiber.StatusBadRequest, "position must be zero or greater")
	}
	references, err := coursedomain.ValidateDefinition(modeName, request.Definition, *request.IsPublic)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid level definition", err.Error())
	}
	melodyID, variantID, err := h.validateMIDIReferences(c, references, *request.IsPublic)
	if err != nil {
		return err
	}
	position, err := requestedPosition(h.db.WithContext(c.Context()).Model(&model.CourseLevel{}).Where("progression_group_id = ?", group.ID), request.Position)
	if err != nil {
		return err
	}
	sectionID := group.LibraryGroupID
	level := model.CourseLevel{
		ID: levelID, ProgressionGroupID: group.ID, Name: request.Name, Source: request.Source,
		Definition: model.JSONDocument(append([]byte(nil), request.Definition...)), SortOrder: position,
		IsPublic: *request.IsPublic, LibraryMelodyID: melodyID, LibraryVariantID: variantID,
		SectionLibraryGroupID: &sectionID,
	}
	if err := h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		if err := shiftPositions(tx, &model.CourseLevel{}, "progression_group_id = ?", []any{group.ID}, position); err != nil {
			return err
		}
		return tx.Omit("ProgressionGroup", "LibraryMelody", "LibraryVariant", "SectionLibraryGroup").Create(&level).Error
	}); err != nil {
		if isUniqueConstraint(err) {
			return sendError(c, fiber.StatusConflict, "level id already exists")
		}
		return err
	}
	return sendData(c, fiber.StatusCreated, courseLevelFromModel(level, group.PublicID))
}

func (h *Handler) UpdateCourseLevel(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	level, group, err := h.findManagedLevel(c, mode, c.Params("level_id"))
	if err != nil {
		return notFoundOrError(c, err, "course level not found")
	}
	var request updateCourseLevelRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	request.Name, request.Source = strings.TrimSpace(request.Name), strings.TrimSpace(request.Source)
	if request.IsPublic == nil {
		return sendError(c, fiber.StatusBadRequest, "is_public is required")
	}
	if err := validateLevelMetadata(level.ID, request.Name, request.Source); err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}
	references, err := coursedomain.ValidateDefinition(modeName, request.Definition, *request.IsPublic)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid level definition", err.Error())
	}
	melodyID, variantID, err := h.validateMIDIReferences(c, references, *request.IsPublic)
	if err != nil {
		return err
	}
	if err := h.db.WithContext(c.Context()).Model(&model.CourseLevel{}).Where("id = ?", level.ID).
		Updates(map[string]any{
			"name": request.Name, "source": request.Source,
			"definition": model.JSONDocument(append([]byte(nil), request.Definition...)),
			"is_public":  *request.IsPublic, "library_melody_id": melodyID, "library_variant_id": variantID,
		}).Error; err != nil {
		return err
	}
	level.Name, level.Source, level.IsPublic = request.Name, request.Source, *request.IsPublic
	level.Definition, level.LibraryMelodyID, level.LibraryVariantID = model.JSONDocument(request.Definition), melodyID, variantID
	return sendData(c, fiber.StatusOK, courseLevelFromModel(level, group.PublicID))
}

func (h *Handler) PositionCourseLevel(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	level, sourceGroup, err := h.findManagedLevel(c, mode, c.Params("level_id"))
	if err != nil {
		return notFoundOrError(c, err, "course level not found")
	}
	var request levelPositionRequest
	if err := c.Bind().Body(&request); err != nil || request.Position < 0 {
		return sendError(c, fiber.StatusBadRequest, "position must be zero or greater")
	}
	targetGroup := sourceGroup
	if strings.TrimSpace(request.GroupID) != "" && request.GroupID != sourceGroup.PublicID {
		targetGroup, err = h.findManagedGroup(c, mode.ID, request.GroupID)
		if err != nil {
			return notFoundOrError(c, err, "target progression group not found")
		}
	}
	if err := moveLevel(h.db.WithContext(c.Context()), level, sourceGroup, targetGroup, request.Position); err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) DeleteCourseLevel(c fiber.Ctx) error {
	loaded, modeName, err := h.loadManagedMutationCourse(c)
	if err != nil {
		return err
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return notFoundOrError(c, err, "course mode not found")
	}
	level, group, err := h.findManagedLevel(c, mode, c.Params("level_id"))
	if err != nil {
		return notFoundOrError(c, err, "course level not found")
	}
	if err := h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		if err := tx.Delete(&level).Error; err != nil {
			return err
		}
		return normalizePositions(tx, &model.CourseLevel{}, "progression_group_id = ?", []any{group.ID})
	}); err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) loadManagedMutationCourse(c fiber.Ctx) (loadedCourse, string, error) {
	id, err := parseParamUint(c, "course_id")
	if err != nil {
		return loadedCourse{}, "", err
	}
	loaded, err := h.loadCourseForAdmin(c, id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return loadedCourse{}, "", fiber.NewError(fiber.StatusNotFound, "course not found")
		}
		return loadedCourse{}, "", err
	}
	loaded, err = h.ensureManagedCourse(c, loaded)
	if err != nil {
		return loadedCourse{}, "", err
	}
	return loaded, coursedomain.NormalizeMode(c.Params("mode")), nil
}

func (h *Handler) validateMIDIReferences(c fiber.Ctx, references coursedomain.MIDIReferences, public bool) (*uint, *uint, error) {
	if references.File == nil || references.File.Type == "local" {
		return nil, nil, nil
	}
	melody, err := gorm.G[model.Melody](h.db).Where("id = ?", *references.File.MelodyID).First(c.Context())
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil, fiber.NewError(fiber.StatusBadRequest, "referenced library melody not found")
		}
		return nil, nil, err
	}
	variant, err := gorm.G[model.FileVariant](h.db).
		Where("id = ? AND melody_id = ?", *references.File.VariantID, melody.ID).
		First(c.Context())
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil, fiber.NewError(fiber.StatusBadRequest, "referenced MIDI variant not found")
		}
		return nil, nil, err
	}
	if variant.Format != "midi" {
		return nil, nil, fiber.NewError(fiber.StatusBadRequest, "referenced variant is not a MIDI file")
	}
	if public {
		group, err := gorm.G[model.LibraryGroup](h.db).Where("id = ?", melody.GroupID).First(c.Context())
		if err != nil {
			return nil, nil, err
		}
		if !melody.IsPublic || !group.IsPublic {
			return nil, nil, fiber.NewError(fiber.StatusBadRequest, "public MIDI levels must reference a public library melody")
		}
	}
	melodyID, variantID := melody.ID, variant.ID
	return &melodyID, &variantID, nil
}

func validateLevelMetadata(id string, name string, source string) error {
	if strings.TrimSpace(id) == "" || runeLen(id) > 64 {
		return errors.New("level id is required and must be at most 64 characters")
	}
	if strings.TrimSpace(name) == "" || runeLen(name) > 220 {
		return errors.New("level name is required and must be at most 220 characters")
	}
	if !coursedomain.IsValidLevelSource(source) {
		return errors.New("level source must be built_in, user, or imported")
	}
	return nil
}

func runeLen(value string) int {
	return utf8.RuneCountInString(value)
}

func (h *Handler) findManagedLevel(c fiber.Ctx, mode model.CourseMode, id string) (model.CourseLevel, model.CourseProgressionGroup, error) {
	var level model.CourseLevel
	err := h.db.WithContext(c.Context()).Table("course_levels").
		Select("course_levels.*").
		Joins("JOIN course_progression_groups AS pg ON pg.id = course_levels.progression_group_id AND pg.deleted_at IS NULL").
		Where("course_levels.id = ? AND course_levels.deleted_at IS NULL AND pg.course_mode_id = ?", strings.TrimSpace(id), mode.ID).
		First(&level).Error
	if err != nil {
		return model.CourseLevel{}, model.CourseProgressionGroup{}, err
	}
	var group model.CourseProgressionGroup
	if err := h.db.WithContext(c.Context()).Where("id = ?", level.ProgressionGroupID).First(&group).Error; err != nil {
		return model.CourseLevel{}, model.CourseProgressionGroup{}, err
	}
	return level, group, nil
}

func courseLevelFromModel(level model.CourseLevel, groupID string) courseLevelResponse {
	response := courseLevelResponse{
		ID: level.ID, ProgressionGroupID: groupID, Name: level.Name, Source: level.Source,
		Definition: json.RawMessage(level.Definition), SortOrder: level.SortOrder,
		IsPublic: level.IsPublic, Sections: []courseSectionResponse{},
	}
	if level.LibraryMelodyID != nil && level.LibraryVariantID != nil {
		response.MIDI = midiResource(*level.LibraryMelodyID, *level.LibraryVariantID)
	}
	return response
}

func requestedPosition(query *gorm.DB, requested *int) (int, error) {
	var count int64
	if err := query.Count(&count).Error; err != nil {
		return 0, err
	}
	if requested == nil || *requested > int(count) {
		return int(count), nil
	}
	if *requested < 0 {
		return 0, errors.New("position must be zero or greater")
	}
	return *requested, nil
}

func shiftPositions(tx *gorm.DB, target any, where string, args []any, position int) error {
	query := tx.Model(target).Where(where, args...).Where("sort_order >= ?", position)
	return query.UpdateColumn("sort_order", gorm.Expr("sort_order + 1")).Error
}

func normalizePositions(tx *gorm.DB, target any, where string, args []any) error {
	var rows []struct {
		ID uint
	}
	if _, ok := target.(*model.CourseLevel); ok {
		var levels []struct{ ID string }
		if err := tx.Model(target).Select("id").Where(where, args...).Order("sort_order asc, id asc").Scan(&levels).Error; err != nil {
			return err
		}
		for index, row := range levels {
			if err := tx.Model(target).Where("id = ?", row.ID).UpdateColumn("sort_order", index).Error; err != nil {
				return err
			}
		}
		return nil
	}
	if err := tx.Model(target).Select("id").Where(where, args...).Order("sort_order asc, id asc").Scan(&rows).Error; err != nil {
		return err
	}
	for index, row := range rows {
		if err := tx.Model(target).Where("id = ?", row.ID).UpdateColumn("sort_order", index).Error; err != nil {
			return err
		}
	}
	return nil
}

func reorderRecord(db *gorm.DB, target any, id uint, where string, args []any, position int) error {
	return db.Transaction(func(tx *gorm.DB) error {
		var rows []struct{ ID uint }
		if err := tx.Model(target).Select("id").Where(where, args...).Order("sort_order asc, id asc").Scan(&rows).Error; err != nil {
			return err
		}
		ordered := make([]uint, 0, len(rows))
		found := false
		for _, row := range rows {
			if row.ID == id {
				found = true
				continue
			}
			ordered = append(ordered, row.ID)
		}
		if !found {
			return gorm.ErrRecordNotFound
		}
		position = min(position, len(ordered))
		ordered = append(ordered, 0)
		copy(ordered[position+1:], ordered[position:])
		ordered[position] = id
		for index, rowID := range ordered {
			if err := tx.Model(target).Where("id = ?", rowID).UpdateColumn("sort_order", index).Error; err != nil {
				return err
			}
		}
		return nil
	})
}

func moveLevel(db *gorm.DB, level model.CourseLevel, source model.CourseProgressionGroup, target model.CourseProgressionGroup, position int) error {
	return db.Transaction(func(tx *gorm.DB) error {
		if source.ID == target.ID {
			return reorderStringRecord(tx, &model.CourseLevel{}, level.ID, "progression_group_id = ?", []any{source.ID}, position)
		}
		var targetCount int64
		if err := tx.Model(&model.CourseLevel{}).Where("progression_group_id = ?", target.ID).Count(&targetCount).Error; err != nil {
			return err
		}
		position = min(position, int(targetCount))
		if err := tx.Model(&model.CourseLevel{}).Where("progression_group_id = ? AND sort_order >= ?", target.ID, position).
			UpdateColumn("sort_order", gorm.Expr("sort_order + 1")).Error; err != nil {
			return err
		}
		sectionID := target.LibraryGroupID
		if err := tx.Model(&model.CourseLevel{}).Where("id = ?", level.ID).Updates(map[string]any{
			"progression_group_id": target.ID, "sort_order": position, "section_library_group_id": sectionID,
		}).Error; err != nil {
			return err
		}
		return normalizePositions(tx, &model.CourseLevel{}, "progression_group_id = ?", []any{source.ID})
	})
}

func reorderStringRecord(tx *gorm.DB, target any, id string, where string, args []any, position int) error {
	var rows []struct{ ID string }
	if err := tx.Model(target).Select("id").Where(where, args...).Order("sort_order asc, id asc").Scan(&rows).Error; err != nil {
		return err
	}
	ordered := make([]string, 0, len(rows))
	found := false
	for _, row := range rows {
		if row.ID == id {
			found = true
			continue
		}
		ordered = append(ordered, row.ID)
	}
	if !found {
		return gorm.ErrRecordNotFound
	}
	position = min(position, len(ordered))
	ordered = append(ordered, "")
	copy(ordered[position+1:], ordered[position:])
	ordered[position] = id
	for index, rowID := range ordered {
		if err := tx.Model(target).Where("id = ?", rowID).UpdateColumn("sort_order", index).Error; err != nil {
			return err
		}
	}
	return nil
}
