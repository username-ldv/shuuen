package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
)

const (
	blueprintLevelOrder = "g.path asc, m.sort_order asc, m.title asc, m.id asc"
	managedLevelOrder   = "pg.sort_order asc, course_levels.sort_order asc, course_levels.name asc, course_levels.id asc"
)

func (h *Handler) ListCourses(c fiber.Ctx) error {
	limit, offset := parsePagination(c)
	root, err := gorm.G[model.LibraryGroup](h.db).Where("path = ?", "").First(c.Context())
	if err != nil {
		return err
	}
	query := gorm.G[model.LibraryGroup](h.db).Where("parent_id = ?", root.ID)
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	}
	groups, err := query.Find(c.Context())
	if err != nil {
		return err
	}
	needle := strings.ToLower(strings.TrimSpace(c.Query("q")))
	responses := make([]courseResponse, 0, len(groups))
	for _, group := range groups {
		courseRecord, findErr := gorm.G[model.Course](h.db).Where("id = ?", group.ID).First(c.Context())
		if findErr != nil && !errors.Is(findErr, gorm.ErrRecordNotFound) {
			return findErr
		}
		loaded := loadedCourse{Group: group}
		if findErr == nil {
			loaded.Course = &courseRecord
		}
		if !includePrivate(c) && !loaded.visible() {
			continue
		}
		if needle != "" && !strings.Contains(strings.ToLower(loaded.name()), needle) &&
			!strings.Contains(strings.ToLower(loaded.description()), needle) &&
			!strings.Contains(strings.ToLower(loaded.author()), needle) {
			continue
		}
		response, err := h.courseResponse(c, loaded, false)
		if err != nil {
			return err
		}
		responses = append(responses, response)
	}
	sortCourseResponses(responses)
	total := int64(len(responses))
	start := min(offset, len(responses))
	end := min(start+limit, len(responses))
	return c.JSON(listResponse{Data: responses[start:end], Meta: listMeta{Limit: limit, Offset: offset, Total: total}})
}

func (h *Handler) GetCourse(c fiber.Ctx) error {
	id, err := parseParamUint(c, "course_id")
	if err != nil {
		return err
	}
	loaded, err := h.loadCourse(c, id)
	if err != nil {
		return notFoundOrError(c, err, "course not found")
	}
	response, err := h.courseResponse(c, loaded, true)
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, response)
}

func (h *Handler) GetCourseMode(c fiber.Ctx) error {
	id, err := parseParamUint(c, "course_id")
	if err != nil {
		return err
	}
	loaded, err := h.loadCourse(c, id)
	if err != nil {
		return notFoundOrError(c, err, "course not found")
	}
	modeName := coursedomain.NormalizeMode(c.Params("mode"))
	if !coursedomain.IsValidMode(modeName) {
		return sendError(c, fiber.StatusBadRequest, "unsupported course mode")
	}
	modes, err := h.courseModes(c, loaded, true)
	if err != nil {
		return err
	}
	for _, mode := range modes {
		if mode.Mode == modeName {
			return sendData(c, fiber.StatusOK, mode)
		}
	}
	return sendError(c, fiber.StatusNotFound, "course mode not found")
}

type queryCourseLevelsRequest struct {
	IDs []string `json:"ids"`
}

func (h *Handler) ListCourseLevels(c fiber.Ctx) error {
	courseID, err := parseParamUint(c, "course_id")
	if err != nil {
		return err
	}
	loaded, err := h.loadCourse(c, courseID)
	if err != nil {
		return notFoundOrError(c, err, "course not found")
	}
	modeName := coursedomain.NormalizeMode(c.Params("mode"))
	if !coursedomain.IsValidMode(modeName) {
		return sendError(c, fiber.StatusBadRequest, "unsupported course mode")
	}
	limit, offset := parsePagination(c)
	ids, err := parseLevelIDs(c.Query("ids"), 200)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}
	if len(ids) > 0 {
		limit, offset = len(ids), 0
	}
	rows, total, err := h.listCourseLevels(c, loaded, modeName, strings.TrimSpace(c.Query("group_id")), ids, limit, offset)
	if err != nil {
		return courseLevelListError(c, err)
	}
	if len(ids) > 0 {
		byID := make(map[string]courseLevelResponse, len(rows))
		for _, row := range rows {
			byID[row.ID] = row
		}
		ordered := make([]courseLevelResponse, 0, len(rows))
		for _, id := range ids {
			if row, ok := byID[id]; ok {
				ordered = append(ordered, row)
			}
		}
		rows = ordered
	}
	return c.JSON(listResponse{Data: rows, Meta: listMeta{Limit: limit, Offset: offset, Total: total}})
}

func (h *Handler) QueryCourseLevels(c fiber.Ctx) error {
	courseID, err := parseParamUint(c, "course_id")
	if err != nil {
		return err
	}
	loaded, err := h.loadCourse(c, courseID)
	if err != nil {
		return notFoundOrError(c, err, "course not found")
	}
	modeName := coursedomain.NormalizeMode(c.Params("mode"))
	if !coursedomain.IsValidMode(modeName) {
		return sendError(c, fiber.StatusBadRequest, "unsupported course mode")
	}
	var request queryCourseLevelsRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	if len(request.IDs) == 0 || len(request.IDs) > 200 {
		return sendError(c, fiber.StatusBadRequest, "ids must contain between 1 and 200 level ids")
	}
	seen := map[string]bool{}
	for index, id := range request.IDs {
		id = strings.TrimSpace(id)
		if id == "" || seen[id] {
			return sendError(c, fiber.StatusBadRequest, "ids must be non-empty and unique")
		}
		seen[id] = true
		request.IDs[index] = id
	}
	rows, _, err := h.listCourseLevels(c, loaded, modeName, "", request.IDs, len(request.IDs), 0)
	if err != nil {
		return courseLevelListError(c, err)
	}
	byID := make(map[string]courseLevelResponse, len(rows))
	for _, row := range rows {
		byID[row.ID] = row
	}
	ordered := make([]courseLevelResponse, 0, len(rows))
	for _, id := range request.IDs {
		if row, ok := byID[id]; ok {
			ordered = append(ordered, row)
		}
	}
	return sendData(c, fiber.StatusOK, ordered)
}

func (h *Handler) GetCourseLevel(c fiber.Ctx) error {
	courseID, err := parseParamUint(c, "course_id")
	if err != nil {
		return err
	}
	loaded, err := h.loadCourse(c, courseID)
	if err != nil {
		return notFoundOrError(c, err, "course not found")
	}
	modeName := coursedomain.NormalizeMode(c.Params("mode"))
	if !coursedomain.IsValidMode(modeName) {
		return sendError(c, fiber.StatusBadRequest, "unsupported course mode")
	}
	levelID := strings.TrimSpace(c.Params("level_id"))
	rows, _, err := h.listCourseLevels(c, loaded, modeName, "", []string{levelID}, 1, 0)
	if err != nil {
		return courseLevelListError(c, err)
	}
	if len(rows) == 0 {
		return sendError(c, fiber.StatusNotFound, "course level not found")
	}
	navigation, err := h.courseLevelNavigation(c, loaded, modeName, rows[0])
	if err != nil {
		return courseLevelListError(c, err)
	}
	rows[0].Navigation = &navigation
	return sendData(c, fiber.StatusOK, rows[0])
}

func courseLevelListError(c fiber.Ctx, err error) error {
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return sendError(c, fiber.StatusNotFound, "course mode or progression group not found")
	}
	var requestErr *courseRequestError
	if errors.As(err, &requestErr) {
		return sendError(c, fiber.StatusBadRequest, requestErr.Error())
	}
	return err
}

type courseRequestError struct{ message string }

func (err *courseRequestError) Error() string { return err.message }

func (h *Handler) listCourseLevels(c fiber.Ctx, loaded loadedCourse, modeName string, groupID string, ids []string, limit int, offset int) ([]courseLevelResponse, int64, error) {
	if loaded.isBlueprint() {
		if modeName != coursedomain.ModeMelodies {
			return nil, 0, gorm.ErrRecordNotFound
		}
		return h.listBlueprintLevels(c, loaded.Group, groupID, ids, limit, offset)
	}
	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return nil, 0, err
	}
	return h.listManagedLevels(c, loaded.Group, mode, groupID, ids, limit, offset)
}

func (h *Handler) courseLevelNavigation(c fiber.Ctx, loaded loadedCourse, modeName string, level courseLevelResponse) (courseLevelNavigationResponse, error) {
	if loaded.isBlueprint() {
		melodyID, err := parseBlueprintResourceID(level.ID)
		if err != nil {
			return courseLevelNavigationResponse{}, err
		}
		progressionGroup, err := h.findBlueprintProgressionGroup(c, loaded.Group, level.ProgressionGroupID)
		if err != nil {
			return courseLevelNavigationResponse{}, err
		}
		return h.blueprintLevelNavigation(c, loaded.Group, progressionGroup, melodyID)
	}

	mode, err := h.findManagedMode(c, loaded.Group.ID, modeName)
	if err != nil {
		return courseLevelNavigationResponse{}, err
	}
	progressionGroup, err := h.findManagedGroup(c, mode.ID, level.ProgressionGroupID)
	if err != nil {
		return courseLevelNavigationResponse{}, err
	}
	return h.managedLevelNavigation(c, mode, progressionGroup, level.ID)
}

type blueprintLevelRow struct {
	MelodyID       uint
	Title          string
	SortOrder      int
	IsPublic       bool
	SectionPublic  bool
	SectionGroupID uint
	SectionPath    string
	SectionName    string
	VariantID      uint
	OriginalName   string
}

func (h *Handler) blueprintLevelBaseQuery(c fiber.Ctx, courseGroup model.LibraryGroup, progressionGroup *model.LibraryGroup) *gorm.DB {
	query := h.db.WithContext(c.Context()).Table("melodies AS m").
		Joins("JOIN library_groups AS g ON g.id = m.group_id AND g.deleted_at IS NULL").
		Joins(`JOIN file_variants AS fv ON fv.id = (
			SELECT candidate.id FROM file_variants AS candidate
			WHERE candidate.melody_id = m.id AND candidate.format = 'midi' AND candidate.deleted_at IS NULL
			ORDER BY candidate.is_primary DESC, candidate.created_at ASC, candidate.id ASC LIMIT 1
		)`).
		Where("m.deleted_at IS NULL")
	if progressionGroup == nil {
		query = query.Where("g.path = ? OR g.path LIKE ? ESCAPE '\\'", courseGroup.Path, descendantPattern(courseGroup.Path))
	} else if progressionGroup.ID == courseGroup.ID {
		query = query.Where("g.id = ?", courseGroup.ID)
	} else {
		query = query.Where("g.path = ? OR g.path LIKE ? ESCAPE '\\'", progressionGroup.Path, descendantPattern(progressionGroup.Path))
	}
	if !includePrivate(c) {
		query = query.Where("m.is_public = ? AND g.is_public = ?", true, true)
	}
	return query
}

func (h *Handler) findBlueprintProgressionGroup(c fiber.Ctx, courseGroup model.LibraryGroup, requestedGroupID string) (*model.LibraryGroup, error) {
	if requestedGroupID == "" {
		return nil, nil
	}
	libraryID, err := parseBlueprintResourceID(requestedGroupID)
	if err != nil {
		return nil, &courseRequestError{message: "invalid blueprint group id"}
	}
	group, err := gorm.G[model.LibraryGroup](h.db).Where("id = ?", libraryID).First(c.Context())
	if err != nil {
		return nil, err
	}
	if group.ID != courseGroup.ID && (group.ParentID == nil || *group.ParentID != courseGroup.ID) {
		return nil, gorm.ErrRecordNotFound
	}
	if !includePrivate(c) && !group.IsPublic {
		return nil, gorm.ErrRecordNotFound
	}
	return &group, nil
}

func (h *Handler) listBlueprintLevels(c fiber.Ctx, courseGroup model.LibraryGroup, requestedGroupID string, ids []string, limit int, offset int) ([]courseLevelResponse, int64, error) {
	progressionGroup, err := h.findBlueprintProgressionGroup(c, courseGroup, requestedGroupID)
	if err != nil {
		return nil, 0, err
	}
	query := h.blueprintLevelBaseQuery(c, courseGroup, progressionGroup)
	if len(ids) > 0 {
		melodyIDs := make([]uint, 0, len(ids))
		for _, id := range ids {
			value, err := parseBlueprintResourceID(id)
			if err != nil {
				return nil, 0, &courseRequestError{message: "blueprint level ids must use the library-<id> form"}
			}
			melodyIDs = append(melodyIDs, value)
		}
		query = query.Where("m.id IN ?", melodyIDs)
	}
	var total int64
	if err := query.Distinct("m.id").Count(&total).Error; err != nil {
		return nil, 0, err
	}
	var rows []blueprintLevelRow
	if err := query.Select(`m.id AS melody_id, m.title, m.sort_order, m.is_public,
		g.is_public AS section_public,
		g.id AS section_group_id, g.path AS section_path, g.name AS section_name,
		fv.id AS variant_id, fv.original_name`).
		Order(blueprintLevelOrder).
		Limit(limit).Offset(offset).Scan(&rows).Error; err != nil {
		return nil, 0, err
	}
	pathMap, err := h.loadGroupPathMap(c, courseGroup)
	if err != nil {
		return nil, 0, err
	}
	directGroups, err := gorm.G[model.LibraryGroup](h.db).Where("parent_id = ?", courseGroup.ID).Find(c.Context())
	if err != nil {
		return nil, 0, err
	}
	directByPath := make(map[string]model.LibraryGroup, len(directGroups))
	for _, group := range directGroups {
		directByPath[group.Path] = group
	}
	responses := make([]courseLevelResponse, 0, len(rows))
	for _, row := range rows {
		root := courseGroup
		if progressionGroup != nil {
			root = *progressionGroup
		} else if row.SectionPath != courseGroup.Path {
			relative := strings.TrimPrefix(row.SectionPath, courseGroup.Path+"/")
			first := strings.Split(relative, "/")[0]
			if direct, ok := directByPath[courseGroup.Path+"/"+first]; ok {
				root = direct
			}
		}
		section := pathMap[row.SectionPath]
		definition := coursedomain.DefaultMIDIDefinition(row.MelodyID, row.VariantID, row.OriginalName)
		responses = append(responses, courseLevelResponse{
			ID: blueprintLevelID(row.MelodyID), ProgressionGroupID: blueprintGroupID(root.ID),
			Name: row.Title, Source: "imported", Definition: definition, SortOrder: row.SortOrder,
			IsPublic: row.IsPublic && row.SectionPublic, MIDI: midiResource(row.MelodyID, row.VariantID),
			Sections: sectionTrail(root, &section, pathMap),
		})
	}
	return responses, total, nil
}

type blueprintNavigationRow struct {
	PreviousMelodyID *uint
	NextMelodyID     *uint
	Position         int64
	Total            int64
}

func (h *Handler) blueprintLevelNavigation(c fiber.Ctx, courseGroup model.LibraryGroup, progressionGroup *model.LibraryGroup, melodyID uint) (courseLevelNavigationResponse, error) {
	ordered := h.blueprintLevelBaseQuery(c, courseGroup, progressionGroup).
		Select(`m.id AS melody_id,
			LAG(m.id) OVER (ORDER BY ` + blueprintLevelOrder + `) AS previous_melody_id,
			LEAD(m.id) OVER (ORDER BY ` + blueprintLevelOrder + `) AS next_melody_id,
			ROW_NUMBER() OVER (ORDER BY ` + blueprintLevelOrder + `) - 1 AS position,
			COUNT(*) OVER () AS total`)
	var row blueprintNavigationRow
	if err := h.db.WithContext(c.Context()).Table("(?) AS ordered_levels", ordered).
		Where("melody_id = ?", melodyID).Limit(1).Scan(&row).Error; err != nil {
		return courseLevelNavigationResponse{}, err
	}
	if row.Total == 0 {
		return courseLevelNavigationResponse{}, gorm.ErrRecordNotFound
	}
	return courseLevelNavigationResponse{
		PreviousLevelID: blueprintLevelIDPointer(row.PreviousMelodyID),
		NextLevelID:     blueprintLevelIDPointer(row.NextMelodyID),
		Position:        row.Position,
		Total:           row.Total,
	}, nil
}

func (h *Handler) listManagedLevels(c fiber.Ctx, courseGroup model.LibraryGroup, mode model.CourseMode, requestedGroupID string, ids []string, limit int, offset int) ([]courseLevelResponse, int64, error) {
	query := h.managedLevelBaseQuery(c, mode)
	if requestedGroupID != "" {
		group, err := h.findManagedGroup(c, mode.ID, requestedGroupID)
		if err != nil {
			return nil, 0, err
		}
		query = query.Where("course_levels.progression_group_id = ?", group.ID)
	}
	if len(ids) > 0 {
		query = query.Where("course_levels.id IN ?", ids)
	}
	var total int64
	if err := query.Count(&total).Error; err != nil {
		return nil, 0, err
	}
	var levels []model.CourseLevel
	if err := query.Select("course_levels.*").
		Order(managedLevelOrder).
		Limit(limit).Offset(offset).Find(&levels).Error; err != nil {
		return nil, 0, err
	}
	groups, err := gorm.G[model.CourseProgressionGroup](h.db).Where("course_mode_id = ?", mode.ID).Find(c.Context())
	if err != nil {
		return nil, 0, err
	}
	groupByID := make(map[uint]model.CourseProgressionGroup, len(groups))
	rootByID := make(map[uint]model.LibraryGroup, len(groups))
	for _, group := range groups {
		groupByID[group.ID] = group
		root, err := gorm.G[model.LibraryGroup](h.db).Where("id = ?", group.LibraryGroupID).First(c.Context())
		if err != nil {
			return nil, 0, err
		}
		rootByID[group.ID] = root
	}
	pathMap, err := h.loadGroupPathMap(c, courseGroup)
	if err != nil {
		return nil, 0, err
	}
	groupByLibraryID := make(map[uint]model.LibraryGroup, len(pathMap))
	for _, group := range pathMap {
		groupByLibraryID[group.ID] = group
	}
	responses := make([]courseLevelResponse, 0, len(levels))
	for _, level := range levels {
		group := groupByID[level.ProgressionGroupID]
		root := rootByID[level.ProgressionGroupID]
		var section *model.LibraryGroup
		if level.SectionLibraryGroupID != nil {
			if value, ok := groupByLibraryID[*level.SectionLibraryGroupID]; ok {
				section = &value
			}
		}
		response := courseLevelResponse{
			ID: level.ID, ProgressionGroupID: group.PublicID, Name: level.Name, Source: level.Source,
			Definition: json.RawMessage(level.Definition), SortOrder: level.SortOrder, IsPublic: level.IsPublic,
			Sections: sectionTrail(root, section, pathMap),
		}
		if level.LibraryMelodyID != nil && level.LibraryVariantID != nil {
			response.MIDI = midiResource(*level.LibraryMelodyID, *level.LibraryVariantID)
		}
		responses = append(responses, response)
	}
	return responses, total, nil
}

func (h *Handler) managedLevelBaseQuery(c fiber.Ctx, mode model.CourseMode) *gorm.DB {
	query := h.db.WithContext(c.Context()).Model(&model.CourseLevel{}).
		Joins("JOIN course_progression_groups AS pg ON pg.id = course_levels.progression_group_id AND pg.deleted_at IS NULL").
		Where("pg.course_mode_id = ?", mode.ID)
	if !includePrivate(c) {
		query = query.Where("course_levels.is_public = ?", true).
			Where(`(
				(course_levels.library_melody_id IS NULL AND course_levels.library_variant_id IS NULL)
				OR EXISTS (
					SELECT 1
					FROM melodies AS referenced_melody
					JOIN library_groups AS referenced_group
						ON referenced_group.id = referenced_melody.group_id AND referenced_group.deleted_at IS NULL
					JOIN file_variants AS referenced_variant
						ON referenced_variant.id = course_levels.library_variant_id
						AND referenced_variant.melody_id = referenced_melody.id
						AND referenced_variant.deleted_at IS NULL
					WHERE referenced_melody.id = course_levels.library_melody_id
						AND referenced_melody.deleted_at IS NULL
						AND referenced_melody.is_public = ?
						AND referenced_group.is_public = ?
						AND referenced_variant.format = 'midi'
				)
			)`, true, true)
	}
	return query
}

type managedNavigationRow struct {
	PreviousLevelID *string
	NextLevelID     *string
	Position        int64
	Total           int64
}

func (h *Handler) managedLevelNavigation(c fiber.Ctx, mode model.CourseMode, progressionGroup model.CourseProgressionGroup, levelID string) (courseLevelNavigationResponse, error) {
	ordered := h.managedLevelBaseQuery(c, mode).
		Where("course_levels.progression_group_id = ?", progressionGroup.ID).
		Select(`course_levels.id AS level_id,
			LAG(course_levels.id) OVER (ORDER BY ` + managedLevelOrder + `) AS previous_level_id,
			LEAD(course_levels.id) OVER (ORDER BY ` + managedLevelOrder + `) AS next_level_id,
			ROW_NUMBER() OVER (ORDER BY ` + managedLevelOrder + `) - 1 AS position,
			COUNT(*) OVER () AS total`)
	var row managedNavigationRow
	if err := h.db.WithContext(c.Context()).Table("(?) AS ordered_levels", ordered).
		Where("level_id = ?", levelID).Limit(1).Scan(&row).Error; err != nil {
		return courseLevelNavigationResponse{}, err
	}
	if row.Total == 0 {
		return courseLevelNavigationResponse{}, gorm.ErrRecordNotFound
	}
	return courseLevelNavigationResponse{
		PreviousLevelID: row.PreviousLevelID,
		NextLevelID:     row.NextLevelID,
		Position:        row.Position,
		Total:           row.Total,
	}, nil
}

func midiResource(melodyID uint, variantID uint) *courseMIDIResource {
	return &courseMIDIResource{
		MelodyID: melodyID, VariantID: variantID,
		DownloadURL: fmt.Sprintf("/api/v1/library/variants/%d/download", variantID),
	}
}

func parseLevelIDs(raw string, maximum int) ([]string, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, nil
	}
	parts := strings.Split(raw, ",")
	if len(parts) > maximum {
		return nil, fmt.Errorf("ids accepts at most %d values", maximum)
	}
	seen := map[string]bool{}
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part == "" || seen[part] {
			return nil, errors.New("ids must be non-empty and unique")
		}
		seen[part] = true
		result = append(result, part)
	}
	return result, nil
}

func parseBlueprintResourceID(value string) (uint, error) {
	raw := strings.TrimPrefix(strings.TrimSpace(value), "library-")
	if raw == value || raw == "" {
		return 0, errors.New("invalid blueprint resource id")
	}
	id, err := strconv.ParseUint(raw, 10, 64)
	if err != nil || id == 0 {
		return 0, errors.New("invalid blueprint resource id")
	}
	return uint(id), nil
}

func blueprintLevelIDPointer(id *uint) *string {
	if id == nil {
		return nil
	}
	value := blueprintLevelID(*id)
	return &value
}
