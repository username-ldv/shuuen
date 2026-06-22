package httpapi

import (
	"errors"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	"shuuen-backend/internal/model"
	"shuuen-backend/internal/storage"
)

func (h *Handler) ListMelodies(c fiber.Ctx) error {
	limit, offset := parsePagination(c)
	var rows []model.Melody
	var total int64

	countQuery, err := h.buildMelodyQuery(c)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}
	if err := countQuery.Distinct("melodies.id").Count(&total).Error; err != nil {
		return err
	}

	dataQuery, err := h.buildMelodyQuery(c)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}
	dataQuery = dataQuery.
		Preload("Group").
		Preload("Tags").
		Preload("Variants").
		Distinct("melodies.*").
		Order(melodySort(c.Query("sort"))).
		Limit(limit).
		Offset(offset)

	if err := dataQuery.Find(&rows).Error; err != nil {
		return err
	}
	return c.JSON(listResponse{Data: rows, Meta: listMeta{Limit: limit, Offset: offset, Total: total}})
}

func (h *Handler) GetMelody(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	var melody model.Melody
	if err := h.db.Preload("Group").Preload("Tags").Preload("Variants").First(&melody, id).Error; err != nil {
		return notFoundOrError(c, err, "melody not found")
	}
	return sendData(c, fiber.StatusOK, melody)
}

func (h *Handler) DeleteMelody(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	var melody model.Melody
	if err := h.db.Preload("Variants").First(&melody, id).Error; err != nil {
		return notFoundOrError(c, err, "melody not found")
	}

	err = h.db.Transaction(func(tx *gorm.DB) error {
		for _, variant := range melody.Variants {
			if err := h.storage.Delete(variant.StoragePath); err != nil {
				return err
			}
			if err := tx.Delete(&variant).Error; err != nil {
				return err
			}
		}
		if err := tx.Model(&melody).Association("Tags").Clear(); err != nil {
			return err
		}
		return tx.Delete(&melody).Error
	})
	if err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) buildMelodyQuery(c fiber.Ctx) (*gorm.DB, error) {
	query := h.db.Model(&model.Melody{})

	if groupID := parseQueryInt(c, "group_id", 0); groupID > 0 {
		query = query.Where("group_id = ?", groupID)
	}
	if groupPath := cleanURLPath(c.Query("group_path")); groupPath != "" {
		query = withGroupPathFilter(query, groupPath, parseBoolQuery(c, "recursive", false))
	}
	if tagID := parseQueryInt(c, "tag_id", 0); tagID > 0 {
		query = query.Joins("JOIN melody_tags ON melody_tags.melody_id = melodies.id").
			Where("melody_tags.tag_id = ?", tagID)
	}
	if tagSlug := strings.TrimSpace(c.Query("tag")); tagSlug != "" {
		query = query.Joins("JOIN melody_tags AS melody_tags_slug ON melody_tags_slug.melody_id = melodies.id").
			Joins("JOIN tags ON tags.id = melody_tags_slug.tag_id").
			Where("tags.slug = ?", tagSlug)
	}
	if format := strings.TrimSpace(c.Query("format")); format != "" {
		normalized := storage.NormalizeFormat(format)
		if !storage.IsAllowedFormat(normalized) {
			return nil, errors.New("unsupported variant format")
		}
		query = query.Joins("JOIN file_variants ON file_variants.melody_id = melodies.id").
			Where("file_variants.format = ?", normalized)
	}
	if rawPublished := strings.TrimSpace(c.Query("published")); rawPublished != "" {
		query = query.Where("is_published = ?", parseBoolQuery(c, "published", false))
	}
	if q := strings.TrimSpace(c.Query("q")); q != "" {
		needle := "%" + strings.ToLower(q) + "%"
		query = query.Where("LOWER(title) LIKE ? OR LOWER(composer) LIKE ? OR LOWER(source_path) LIKE ?", needle, needle, needle)
	}

	return query, nil
}

func melodySort(sort string) string {
	switch strings.TrimSpace(sort) {
	case "title":
		return "title asc"
	case "-title":
		return "title desc"
	case "path":
		return "source_path asc"
	case "-path":
		return "source_path desc"
	case "created_at":
		return "created_at asc"
	case "-created_at":
		return "created_at desc"
	case "updated_at":
		return "updated_at asc"
	case "-updated_at":
		return "updated_at desc"
	case "sort_order":
		return "sort_order asc, title asc"
	default:
		return "sort_order asc, title asc"
	}
}
