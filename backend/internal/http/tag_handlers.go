package httpapi

import (
	"strings"

	"github.com/gofiber/fiber/v3"

	"shuuen-backend/internal/model"
	"shuuen-backend/internal/util"
)

type createTagRequest struct {
	Name  string `json:"name" validate:"required,max=80"`
	Slug  string `json:"slug" validate:"max=100"`
	Color string `json:"color" validate:"max=24"`
}

type updateTagRequest struct {
	Name  *string `json:"name" validate:"omitempty,max=80"`
	Slug  *string `json:"slug" validate:"omitempty,max=100"`
	Color *string `json:"color" validate:"omitempty,max=24"`
}

func (h *Handler) ListTags(c fiber.Ctx) error {
	limit, offset := parsePagination(c)
	var rows []model.Tag
	var total int64

	query := h.db.Model(&model.Tag{})
	if q := strings.TrimSpace(c.Query("q")); q != "" {
		query = query.Where("LOWER(name) LIKE ?", "%"+strings.ToLower(q)+"%")
	}

	if err := query.Count(&total).Error; err != nil {
		return err
	}
	if err := query.Order("name asc").Limit(limit).Offset(offset).Find(&rows).Error; err != nil {
		return err
	}
	return c.JSON(listResponse{Data: rows, Meta: listMeta{Limit: limit, Offset: offset, Total: total}})
}

func (h *Handler) GetTag(c fiber.Ctx) error {
	var tag model.Tag
	if err := h.db.First(&tag, c.Params("id")).Error; err != nil {
		return notFoundOrError(c, err, "tag not found")
	}
	return sendData(c, fiber.StatusOK, tag)
}

func (h *Handler) CreateTag(c fiber.Ctx) error {
	var req createTagRequest
	if err := c.Bind().Body(&req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	req.Name = strings.TrimSpace(req.Name)
	req.Slug = strings.TrimSpace(req.Slug)
	req.Color = strings.TrimSpace(req.Color)
	if err := h.validate.Struct(req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "validation failed", err.Error())
	}

	slug := req.Slug
	if slug == "" {
		slug = util.Slugify(req.Name)
	} else {
		slug = util.Slugify(slug)
	}

	tag := model.Tag{Name: req.Name, Slug: slug, Color: req.Color}
	if err := h.db.Create(&tag).Error; err != nil {
		if isUniqueConstraint(err) {
			return sendError(c, fiber.StatusConflict, "tag name or slug already exists")
		}
		return err
	}
	return sendData(c, fiber.StatusCreated, tag)
}

func (h *Handler) UpdateTag(c fiber.Ctx) error {
	var req updateTagRequest
	if err := c.Bind().Body(&req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	if err := h.validate.Struct(req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "validation failed", err.Error())
	}

	var tag model.Tag
	if err := h.db.First(&tag, c.Params("id")).Error; err != nil {
		return notFoundOrError(c, err, "tag not found")
	}

	updates := map[string]any{}
	if req.Name != nil {
		updates["name"] = strings.TrimSpace(*req.Name)
	}
	if req.Slug != nil {
		updates["slug"] = util.Slugify(*req.Slug)
	}
	if req.Color != nil {
		updates["color"] = strings.TrimSpace(*req.Color)
	}
	if len(updates) > 0 {
		if err := h.db.Model(&tag).Updates(updates).Error; err != nil {
			if isUniqueConstraint(err) {
				return sendError(c, fiber.StatusConflict, "tag name or slug already exists")
			}
			return err
		}
	}
	if err := h.db.First(&tag, tag.ID).Error; err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, tag)
}

func (h *Handler) DeleteTag(c fiber.Ctx) error {
	var tag model.Tag
	if err := h.db.First(&tag, c.Params("id")).Error; err != nil {
		return notFoundOrError(c, err, "tag not found")
	}
	if err := h.db.Model(&tag).Association("Melodies").Clear(); err != nil {
		return err
	}
	if err := h.db.Delete(&tag).Error; err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}
