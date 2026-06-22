package httpapi

import (
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	"shuuen-backend/internal/model"
)

type groupTreeResponse struct {
	Group    model.LibraryGroup   `json:"group"`
	Children []model.LibraryGroup `json:"children"`
	Melodies []model.Melody       `json:"melodies"`
}

func (h *Handler) ListGroups(c fiber.Ctx) error {
	limit, offset := parsePagination(c)
	var rows []model.LibraryGroup
	var total int64

	query := h.db.Model(&model.LibraryGroup{}).Preload("Tags")
	if parentID := parseQueryInt(c, "parent_id", -1); parentID >= 0 {
		if parentID == 0 {
			query = query.Where("parent_id IS NULL")
		} else {
			query = query.Where("parent_id = ?", parentID)
		}
	}
	if parentPath := cleanURLPath(c.Query("parent_path")); parentPath != "" || strings.TrimSpace(c.Query("parent_path")) != "" {
		var parent model.LibraryGroup
		if err := h.db.Where("path = ?", parentPath).First(&parent).Error; err != nil {
			return notFoundOrError(c, err, "parent group not found")
		}
		query = query.Where("parent_id = ?", parent.ID)
	}
	if prefix := cleanURLPath(c.Query("path_prefix")); prefix != "" {
		query = query.Where("path = ? OR path LIKE ?", prefix, prefix+"/%")
	}
	if q := strings.TrimSpace(c.Query("q")); q != "" {
		query = query.Where("LOWER(name) LIKE ? OR LOWER(path) LIKE ?", "%"+strings.ToLower(q)+"%", "%"+strings.ToLower(q)+"%")
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
	if err := h.db.Preload("Parent").Preload("Tags").First(&group, id).Error; err != nil {
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
	if err := h.db.Preload("Parent").Preload("Tags").Where("path = ?", groupPath).First(&group).Error; err != nil {
		return notFoundOrError(c, err, "group not found")
	}
	return h.sendGroupTree(c, group, parseBoolQuery(c, "recursive", false))
}

func (h *Handler) sendGroupTree(c fiber.Ctx, group model.LibraryGroup, recursive bool) error {
	var children []model.LibraryGroup
	if err := h.db.Preload("Tags").
		Where("parent_id = ?", group.ID).
		Order("sort_order asc, name asc").
		Find(&children).Error; err != nil {
		return err
	}

	var melodies []model.Melody
	melodyQuery := h.db.Preload("Tags").Preload("Variants").Preload("Group").
		Order("sort_order asc, title asc")
	if recursive {
		if group.Path == "" {
			melodyQuery = melodyQuery.Joins("JOIN library_groups ON library_groups.id = melodies.group_id")
		} else {
			melodyQuery = melodyQuery.Joins("JOIN library_groups ON library_groups.id = melodies.group_id").
				Where("library_groups.path = ? OR library_groups.path LIKE ?", group.Path, group.Path+"/%")
		}
	} else {
		melodyQuery = melodyQuery.Where("group_id = ?", group.ID)
	}

	if err := melodyQuery.Find(&melodies).Error; err != nil {
		return err
	}

	if !recursive && len(children) == 0 && len(melodies) == 0 {
		// Keep empty folders visible; callers can distinguish an empty group from a missing path.
		return sendData(c, fiber.StatusOK, groupTreeResponse{Group: group, Children: children, Melodies: melodies})
	}

	return sendData(c, fiber.StatusOK, groupTreeResponse{Group: group, Children: children, Melodies: melodies})
}

func withGroupPathFilter(query *gorm.DB, groupPath string, recursive bool) *gorm.DB {
	groupPath = cleanURLPath(groupPath)
	if groupPath == "" {
		return query
	}
	query = query.Joins("JOIN library_groups ON library_groups.id = melodies.group_id")
	if recursive {
		return query.Where("library_groups.path = ? OR library_groups.path LIKE ?", groupPath, groupPath+"/%")
	}
	return query.Where("library_groups.path = ?", groupPath)
}
