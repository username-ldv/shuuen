package httpapi

import (
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	"shuuen-backend/internal/model"
)

type groupTreeResponse struct {
	Group        model.LibraryGroup   `json:"group"`
	Children     []model.LibraryGroup `json:"children"`
	Melodies     []model.Melody       `json:"melodies"`
	MelodiesMeta listMeta             `json:"melodies_meta"`
}

func (h *Handler) ListGroups(c fiber.Ctx) error {
	limit, offset := parsePagination(c)
	var rows []model.LibraryGroup
	var total int64

	query := h.db.Model(&model.LibraryGroup{}).Preload("Tags")
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	} else if value, err := parseOptionalBool(c, "public"); err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	} else if value != nil {
		query = query.Where("is_public = ?", *value)
	}
	if parentID := parseQueryInt(c, "parent_id", -1); parentID >= 0 {
		if parentID == 0 {
			query = query.Where("parent_id IS NULL")
		} else {
			query = query.Where("parent_id = ?", parentID)
		}
	}
	if parentPath := cleanURLPath(c.Query("parent_path")); parentPath != "" || strings.TrimSpace(c.Query("parent_path")) != "" {
		var parent model.LibraryGroup
		parentQuery := h.db.Where("path = ?", parentPath)
		if !includePrivate(c) {
			parentQuery = parentQuery.Where("is_public = ?", true)
		}
		if err := parentQuery.First(&parent).Error; err != nil {
			return notFoundOrError(c, err, "parent group not found")
		}
		query = query.Where("parent_id = ?", parent.ID)
	}
	if prefix := cleanURLPath(c.Query("path_prefix")); prefix != "" {
		query = query.Where("path = ? OR path LIKE ? ESCAPE '\\'", prefix, descendantPattern(prefix))
	}
	if q := strings.TrimSpace(c.Query("q")); q != "" {
		needle := containsPattern(strings.ToLower(q))
		query = query.Where("LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(path) LIKE ? ESCAPE '\\'", needle, needle)
	}

	if err := query.Count(&total).Error; err != nil {
		return err
	}
	if err := query.Order("path asc, sort_order asc, name asc").Limit(limit).Offset(offset).Find(&rows).Error; err != nil {
		return err
	}
	return c.JSON(listResponse{Data: rows, Meta: listMeta{Limit: limit, Offset: offset, Total: total}})
}

func (h *Handler) GetGroup(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	var group model.LibraryGroup
	query := h.db.Preload("Parent").Preload("Tags")
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	}
	if err := query.First(&group, id).Error; err != nil {
		return notFoundOrError(c, err, "group not found")
	}
	return h.sendGroupTree(c, group, parseBoolQuery(c, "recursive", false))
}

func (h *Handler) GetGroupByVersionedPath(c fiber.Ctx) error {
	return h.getGroupByPath(c, c.Params("*"))
}

func (h *Handler) GetGroupByDynamicPath(c fiber.Ctx) error {
	prefix := "/api/"
	requestPath := c.Path()
	if !strings.HasPrefix(requestPath, prefix) {
		return sendError(c, fiber.StatusNotFound, "group not found")
	}
	groupPath := strings.TrimPrefix(requestPath, prefix)
	if strings.HasPrefix(groupPath, "v1/") {
		return sendError(c, fiber.StatusNotFound, "group not found")
	}
	return h.getGroupByPath(c, groupPath)
}

func (h *Handler) getGroupByPath(c fiber.Ctx, rawPath string) error {
	groupPath := cleanURLPath(rawPath)
	var group model.LibraryGroup
	query := h.db.Preload("Parent").Preload("Tags").Where("path = ?", groupPath)
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	}
	if err := query.First(&group).Error; err != nil {
		return notFoundOrError(c, err, "group not found")
	}
	return h.sendGroupTree(c, group, parseBoolQuery(c, "recursive", false))
}

func (h *Handler) sendGroupTree(c fiber.Ctx, group model.LibraryGroup, recursive bool) error {
	var children []model.LibraryGroup
	childrenQuery := h.db.Preload("Tags").Where("parent_id = ?", group.ID)
	if !includePrivate(c) {
		childrenQuery = childrenQuery.Where("is_public = ?", true)
	}
	if err := childrenQuery.
		Order("sort_order asc, name asc").
		Find(&children).Error; err != nil {
		return err
	}

	var melodies []model.Melody
	limit, offset := parsePagination(c)
	melodyQuery := h.db.Model(&model.Melody{}).
		Preload("Tags").Preload("Variants").Preload("Group").
		Joins("JOIN library_groups ON library_groups.id = melodies.group_id").
		Where("library_groups.deleted_at IS NULL")
	if !includePrivate(c) {
		melodyQuery = melodyQuery.Where("melodies.is_public = ? AND library_groups.is_public = ?", true, true)
	}
	if recursive {
		if group.Path == "" {
			// The root group includes every visible descendant.
		} else {
			melodyQuery = melodyQuery.Where("library_groups.path = ? OR library_groups.path LIKE ? ESCAPE '\\'", group.Path, descendantPattern(group.Path))
		}
	} else {
		melodyQuery = melodyQuery.Where("melodies.group_id = ?", group.ID)
	}

	var total int64
	if err := melodyQuery.Distinct("melodies.id").Count(&total).Error; err != nil {
		return err
	}
	if err := melodyQuery.
		Select("melodies.*").
		Order("melodies.sort_order asc, melodies.title asc, melodies.id asc").
		Limit(limit).Offset(offset).
		Find(&melodies).Error; err != nil {
		return err
	}

	return sendData(c, fiber.StatusOK, groupTreeResponse{
		Group: group, Children: children, Melodies: melodies,
		MelodiesMeta: listMeta{Limit: limit, Offset: offset, Total: total},
	})
}

func withGroupPathFilter(query *gorm.DB, groupPath string, recursive bool) *gorm.DB {
	groupPath = cleanURLPath(groupPath)
	if groupPath == "" {
		return query
	}
	if recursive {
		return query.Where("library_groups.path = ? OR library_groups.path LIKE ? ESCAPE '\\'", groupPath, descendantPattern(groupPath))
	}
	return query.Where("library_groups.path = ?", groupPath)
}
