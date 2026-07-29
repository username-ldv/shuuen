package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"path"
	"sort"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
)

type loadedCourse struct {
	Group  model.LibraryGroup
	Course *model.Course
}

func (value loadedCourse) isBlueprint() bool {
	return value.Course == nil || value.Course.StructureSource == model.CourseStructureBlueprint
}

func (value loadedCourse) visible() bool {
	if !value.Group.IsPublic {
		return false
	}
	return value.Course == nil || value.Course.IsPublic
}

func (value loadedCourse) name() string {
	if value.Course != nil {
		return value.Course.Name
	}
	return value.Group.Name
}

func (value loadedCourse) description() string {
	if value.Course != nil {
		return value.Course.Description
	}
	return value.Group.Description
}

func (value loadedCourse) author() string {
	if value.Course != nil {
		return value.Course.Author
	}
	return ""
}

func (value loadedCourse) sortOrder() int {
	if value.Course != nil {
		return value.Course.SortOrder
	}
	return value.Group.SortOrder
}

func (value loadedCourse) structureSource() string {
	if value.isBlueprint() {
		return model.CourseStructureBlueprint
	}
	return model.CourseStructureManaged
}

type courseSectionResponse struct {
	LibraryGroupID uint   `json:"library_group_id"`
	Name           string `json:"name"`
	Path           string `json:"path"`
	Depth          int    `json:"depth"`
}

type progressionGroupResponse struct {
	ID             string `json:"id"`
	LibraryGroupID uint   `json:"library_group_id"`
	Name           string `json:"name"`
	Description    string `json:"description"`
	SortOrder      int    `json:"sort_order"`
	LevelCount     int64  `json:"level_count"`
	SectionCount   int64  `json:"section_count"`
	Blueprint      bool   `json:"blueprint"`
}

type courseModeResponse struct {
	Mode        string                     `json:"mode"`
	Name        string                     `json:"name"`
	Description string                     `json:"description"`
	SortOrder   int                        `json:"sort_order"`
	GroupCount  int                        `json:"group_count"`
	LevelCount  int64                      `json:"level_count"`
	Groups      []progressionGroupResponse `json:"groups,omitempty"`
}

type courseResponse struct {
	ID                uint                 `json:"id"`
	LibraryGroupID    uint                 `json:"library_group_id"`
	Slug              string               `json:"slug"`
	Name              string               `json:"name"`
	Description       string               `json:"description"`
	Author            string               `json:"author"`
	IsPublic          bool                 `json:"is_public"`
	SortOrder         int                  `json:"sort_order"`
	StructureSource   string               `json:"structure_source"`
	ModeCount         int                  `json:"mode_count"`
	ProgressionGroups int                  `json:"progression_group_count"`
	LevelCount        int64                `json:"level_count"`
	Modes             []courseModeResponse `json:"modes,omitempty"`
}

type courseMIDIResource struct {
	MelodyID    uint   `json:"melody_id"`
	VariantID   uint   `json:"variant_id"`
	DownloadURL string `json:"download_url"`
}

type courseLevelResponse struct {
	ID                 string                  `json:"id"`
	ProgressionGroupID string                  `json:"progression_group_id"`
	Name               string                  `json:"name"`
	Source             string                  `json:"source"`
	Definition         json.RawMessage         `json:"definition"`
	SortOrder          int                     `json:"sort_order"`
	IsPublic           bool                    `json:"is_public"`
	MIDI               *courseMIDIResource     `json:"midi,omitempty"`
	Sections           []courseSectionResponse `json:"sections"`
}

func (h *Handler) loadCourse(c fiber.Ctx, id uint) (loadedCourse, error) {
	return h.loadCourseByID(c, id, true)
}

func (h *Handler) loadCourseForAdmin(c fiber.Ctx, id uint) (loadedCourse, error) {
	return h.loadCourseByID(c, id, false)
}

func (h *Handler) loadCourseByID(c fiber.Ctx, id uint, enforceVisibility bool) (loadedCourse, error) {
	root, err := gorm.G[model.LibraryGroup](h.db).Where("path = ?", "").First(c.Context())
	if err != nil {
		return loadedCourse{}, err
	}
	group, err := gorm.G[model.LibraryGroup](h.db).
		Where("id = ? AND parent_id = ?", id, root.ID).
		First(c.Context())
	if err != nil {
		return loadedCourse{}, err
	}
	course, err := gorm.G[model.Course](h.db).Where("id = ?", id).First(c.Context())
	if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
		return loadedCourse{}, err
	}
	result := loadedCourse{Group: group}
	if err == nil {
		result.Course = &course
	}
	if enforceVisibility && !includePrivate(c) && !result.visible() {
		return loadedCourse{}, gorm.ErrRecordNotFound
	}
	return result, nil
}

func (h *Handler) courseResponse(c fiber.Ctx, value loadedCourse, includeGroups bool) (courseResponse, error) {
	modes, err := h.courseModes(c, value, includeGroups)
	if err != nil {
		return courseResponse{}, err
	}
	response := courseResponse{
		ID: value.Group.ID, LibraryGroupID: value.Group.ID, Slug: value.Group.Slug,
		Name: value.name(), Description: value.description(), Author: value.author(),
		IsPublic: value.visible(), SortOrder: value.sortOrder(), StructureSource: value.structureSource(),
		ModeCount: len(modes), Modes: modes,
	}
	for _, mode := range modes {
		response.ProgressionGroups += mode.GroupCount
		response.LevelCount += mode.LevelCount
	}
	return response, nil
}

func (h *Handler) courseModes(c fiber.Ctx, value loadedCourse, includeGroups bool) ([]courseModeResponse, error) {
	if value.isBlueprint() {
		groups, levelCount, err := h.blueprintGroups(c, value.Group)
		if err != nil {
			return nil, err
		}
		mode := courseModeResponse{
			Mode: coursedomain.ModeMelodies, Name: coursedomain.DefaultModeName(coursedomain.ModeMelodies),
			SortOrder: 0, GroupCount: len(groups), LevelCount: levelCount,
		}
		if includeGroups {
			mode.Groups = groups
		}
		return []courseModeResponse{mode}, nil
	}
	modes, err := gorm.G[model.CourseMode](h.db).
		Where("course_id = ?", value.Group.ID).
		Order("sort_order asc, mode asc, id asc").
		Find(c.Context())
	if err != nil {
		return nil, err
	}
	responses := make([]courseModeResponse, 0, len(modes))
	for _, mode := range modes {
		groups, err := h.managedGroups(c, mode)
		if err != nil {
			return nil, err
		}
		response := courseModeResponse{
			Mode: mode.Mode, Name: mode.Name, Description: mode.Description, SortOrder: mode.SortOrder,
			GroupCount: len(groups),
		}
		for _, group := range groups {
			response.LevelCount += group.LevelCount
		}
		if includeGroups {
			response.Groups = groups
		}
		responses = append(responses, response)
	}
	return responses, nil
}

func (h *Handler) blueprintGroups(c fiber.Ctx, courseGroup model.LibraryGroup) ([]progressionGroupResponse, int64, error) {
	childrenQuery := gorm.G[model.LibraryGroup](h.db).Where("parent_id = ?", courseGroup.ID)
	if !includePrivate(c) {
		childrenQuery = childrenQuery.Where("is_public = ?", true)
	}
	children, err := childrenQuery.Order("sort_order asc, name asc, id asc").Find(c.Context())
	if err != nil {
		return nil, 0, err
	}
	responses := make([]progressionGroupResponse, 0, len(children)+1)
	var total int64
	directCount, err := h.countBlueprintLevels(c, courseGroup, false)
	if err != nil {
		return nil, 0, err
	}
	if directCount > 0 {
		responses = append(responses, progressionGroupResponse{
			ID: blueprintGroupID(courseGroup.ID), LibraryGroupID: courseGroup.ID,
			Name: "Default", SortOrder: -1, LevelCount: directCount, Blueprint: true,
		})
		total += directCount
	}
	for _, child := range children {
		count, err := h.countBlueprintLevels(c, child, true)
		if err != nil {
			return nil, 0, err
		}
		sectionCount, err := h.countDescendantGroups(c, child)
		if err != nil {
			return nil, 0, err
		}
		responses = append(responses, progressionGroupResponse{
			ID: blueprintGroupID(child.ID), LibraryGroupID: child.ID, Name: child.Name,
			Description: child.Description, SortOrder: child.SortOrder, LevelCount: count,
			SectionCount: sectionCount, Blueprint: true,
		})
		total += count
	}
	return responses, total, nil
}

func (h *Handler) countBlueprintLevels(c fiber.Ctx, group model.LibraryGroup, recursive bool) (int64, error) {
	query := h.db.WithContext(c.Context()).Table("melodies AS m").
		Joins("JOIN library_groups AS g ON g.id = m.group_id AND g.deleted_at IS NULL").
		Where("m.deleted_at IS NULL").
		Where("EXISTS (SELECT 1 FROM file_variants AS fv WHERE fv.melody_id = m.id AND fv.format = ? AND fv.deleted_at IS NULL)", "midi")
	if recursive {
		query = query.Where("g.path = ? OR g.path LIKE ? ESCAPE '\\'", group.Path, descendantPattern(group.Path))
	} else {
		query = query.Where("g.id = ?", group.ID)
	}
	if !includePrivate(c) {
		query = query.Where("m.is_public = ? AND g.is_public = ?", true, true)
	}
	var count int64
	if err := query.Count(&count).Error; err != nil {
		return 0, err
	}
	return count, nil
}

func (h *Handler) countDescendantGroups(c fiber.Ctx, group model.LibraryGroup) (int64, error) {
	query := h.db.WithContext(c.Context()).Model(&model.LibraryGroup{}).
		Where("path LIKE ? ESCAPE '\\'", descendantPattern(group.Path))
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	}
	var count int64
	if err := query.Count(&count).Error; err != nil {
		return 0, err
	}
	return count, nil
}

func (h *Handler) managedGroups(c fiber.Ctx, mode model.CourseMode) ([]progressionGroupResponse, error) {
	groups, err := gorm.G[model.CourseProgressionGroup](h.db).
		Where("course_mode_id = ?", mode.ID).
		Order("sort_order asc, name asc, id asc").
		Find(c.Context())
	if err != nil {
		return nil, err
	}
	responses := make([]progressionGroupResponse, 0, len(groups))
	for _, group := range groups {
		query := h.db.WithContext(c.Context()).Model(&model.CourseLevel{}).
			Where("progression_group_id = ?", group.ID)
		if !includePrivate(c) {
			query = query.Where("is_public = ?", true)
		}
		var count int64
		if err := query.Count(&count).Error; err != nil {
			return nil, err
		}
		libraryGroup, err := gorm.G[model.LibraryGroup](h.db).Where("id = ?", group.LibraryGroupID).First(c.Context())
		if err != nil {
			return nil, err
		}
		sectionCount, err := h.countDescendantGroups(c, libraryGroup)
		if err != nil {
			return nil, err
		}
		responses = append(responses, progressionGroupResponse{
			ID: group.PublicID, LibraryGroupID: group.LibraryGroupID, Name: group.Name,
			Description: group.Description, SortOrder: group.SortOrder, LevelCount: count,
			SectionCount: sectionCount,
		})
	}
	return responses, nil
}

func (h *Handler) findManagedMode(c fiber.Ctx, courseID uint, modeName string) (model.CourseMode, error) {
	return gorm.G[model.CourseMode](h.db).
		Where("course_id = ? AND mode = ?", courseID, coursedomain.NormalizeMode(modeName)).
		First(c.Context())
}

func (h *Handler) findManagedGroup(c fiber.Ctx, modeID uint, publicID string) (model.CourseProgressionGroup, error) {
	return gorm.G[model.CourseProgressionGroup](h.db).
		Where("course_mode_id = ? AND public_id = ?", modeID, strings.TrimSpace(publicID)).
		First(c.Context())
}

func blueprintGroupID(id uint) string { return fmt.Sprintf("library-%d", id) }
func blueprintLevelID(id uint) string { return fmt.Sprintf("library-%d", id) }

func sectionTrail(root model.LibraryGroup, section *model.LibraryGroup, groups map[string]model.LibraryGroup) []courseSectionResponse {
	if section == nil || section.ID == root.ID || section.Path == root.Path {
		return []courseSectionResponse{}
	}
	relative := strings.TrimPrefix(section.Path, root.Path+"/")
	parts := strings.Split(relative, "/")
	trail := make([]courseSectionResponse, 0, len(parts))
	current := root.Path
	for index, part := range parts {
		current = path.Join(current, part)
		group, ok := groups[current]
		if !ok {
			continue
		}
		trail = append(trail, courseSectionResponse{
			LibraryGroupID: group.ID, Name: group.Name, Path: group.Path, Depth: index + 1,
		})
	}
	return trail
}

func (h *Handler) loadGroupPathMap(c fiber.Ctx, root model.LibraryGroup) (map[string]model.LibraryGroup, error) {
	query := gorm.G[model.LibraryGroup](h.db).
		Where("path = ? OR path LIKE ? ESCAPE '\\'", root.Path, descendantPattern(root.Path))
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	}
	groups, err := query.Find(c.Context())
	if err != nil {
		return nil, err
	}
	result := make(map[string]model.LibraryGroup, len(groups))
	for _, group := range groups {
		result[group.Path] = group
	}
	return result, nil
}

func sortCourseResponses(values []courseResponse) {
	sort.SliceStable(values, func(i, j int) bool {
		if values[i].SortOrder != values[j].SortOrder {
			return values[i].SortOrder < values[j].SortOrder
		}
		if values[i].Name != values[j].Name {
			return strings.ToLower(values[i].Name) < strings.ToLower(values[j].Name)
		}
		return values[i].ID < values[j].ID
	})
}
