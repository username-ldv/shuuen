package httpapi

import (
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"shuuen-backend/internal/model"
	dbquery "shuuen-backend/internal/query"
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

	query := gorm.G[model.Tag](h.db).Scopes()
	if q := strings.TrimSpace(c.Query("q")); q != "" {
		query = query.Where("LOWER(name) LIKE ? ESCAPE '\\'", containsPattern(strings.ToLower(q)))
	}

	total, err := query.Count(c.Context(), "*")
	if err != nil {
		return err
	}
	rows, err := query.Order(dbquery.Tag.Name.Asc()).Limit(limit).Offset(offset).Find(c.Context())
	if err != nil {
		return err
	}
	return c.JSON(listResponse{Data: rows, Meta: listMeta{Limit: limit, Offset: offset, Total: total}})
}

func (h *Handler) GetTag(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}
	tag, err := gorm.G[model.Tag](h.db).Where(dbquery.Tag.ID.Eq(id)).First(c.Context())
	if err != nil {
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
	matches, err := gorm.G[model.Tag](h.db).
		Scopes(dbquery.Unscoped).
		Where("name = ? OR slug = ?", req.Name, slug).
		Find(c.Context())
	if err != nil {
		return err
	}
	if len(matches) > 0 {
		if len(matches) != 1 || !matches[0].DeletedAt.Valid {
			return sendError(c, fiber.StatusConflict, "tag name or slug already exists")
		}
		tag = matches[0]
		tag.Name = req.Name
		tag.Slug = slug
		tag.Color = req.Color
		tag.DeletedAt = gorm.DeletedAt{}
		_, err = gorm.G[model.Tag](h.db).
			Scopes(dbquery.Unscoped).
			Where(dbquery.Tag.ID.Eq(tag.ID)).
			Set(
				dbquery.Tag.Name.Set(tag.Name),
				dbquery.Tag.Slug.Set(tag.Slug),
				dbquery.Tag.Color.Set(tag.Color),
				dbquery.Tag.DeletedAt.Set(tag.DeletedAt),
			).
			Update(c.Context())
		if err != nil {
			if isUniqueConstraint(err) {
				return sendError(c, fiber.StatusConflict, "tag name or slug already exists")
			}
			return err
		}
		tag, err = gorm.G[model.Tag](h.db).Where(dbquery.Tag.ID.Eq(tag.ID)).First(c.Context())
		if err != nil {
			return err
		}
		return sendData(c, fiber.StatusCreated, tag)
	}
	if err := gorm.G[model.Tag](h.db).Create(c.Context(), &tag); err != nil {
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

	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}
	tag, err := gorm.G[model.Tag](h.db).Where(dbquery.Tag.ID.Eq(id)).First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "tag not found")
	}

	updates := []clause.Assigner{}
	if req.Name != nil {
		name := strings.TrimSpace(*req.Name)
		if name == "" {
			return sendError(c, fiber.StatusBadRequest, "name must not be empty")
		}
		updates = append(updates, dbquery.Tag.Name.Set(name))
	}
	if req.Slug != nil {
		if strings.TrimSpace(*req.Slug) == "" {
			return sendError(c, fiber.StatusBadRequest, "slug must not be empty")
		}
		updates = append(updates, dbquery.Tag.Slug.Set(util.Slugify(*req.Slug)))
	}
	if req.Color != nil {
		updates = append(updates, dbquery.Tag.Color.Set(strings.TrimSpace(*req.Color)))
	}
	if len(updates) > 0 {
		_, err := gorm.G[model.Tag](h.db).
			Where(dbquery.Tag.ID.Eq(tag.ID)).
			Set(updates...).
			Update(c.Context())
		if err != nil {
			if isUniqueConstraint(err) {
				return sendError(c, fiber.StatusConflict, "tag name or slug already exists")
			}
			return err
		}
	}
	tag, err = gorm.G[model.Tag](h.db).Where(dbquery.Tag.ID.Eq(tag.ID)).First(c.Context())
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, tag)
}

func (h *Handler) DeleteTag(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}
	tag, err := gorm.G[model.Tag](h.db).Where(dbquery.Tag.ID.Eq(id)).First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "tag not found")
	}
	if err := h.db.Transaction(func(tx *gorm.DB) error {
		if _, err := gorm.G[model.Tag](tx).
			Where(dbquery.Tag.ID.Eq(tag.ID)).
			Set(
				dbquery.Tag.Melodies.Unlink(),
				dbquery.Tag.Groups.Unlink(),
			).
			Update(c.Context()); err != nil {
			return err
		}
		_, err := gorm.G[model.Tag](tx).Where(dbquery.Tag.ID.Eq(tag.ID)).Delete(c.Context())
		return err
	}); err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}
