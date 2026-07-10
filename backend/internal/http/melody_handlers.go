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
	if !includePrivate(c) && (!melody.IsPublic || !melody.Group.IsPublic) {
		return sendError(c, fiber.StatusNotFound, "melody not found")
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
	staged := make([]*storage.StagedDelete, 0, len(melody.Variants))
	for _, variant := range melody.Variants {
		pending, err := h.storage.StageDelete(variant.StoragePath)
		if err != nil {
			rollbackStagedDeletes(staged)
			return err
		}
		staged = append(staged, pending)
	}

	err = h.db.Transaction(func(tx *gorm.DB) error {
		for _, variant := range melody.Variants {
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
		rollbackStagedDeletes(staged)
		return err
	}
	for _, pending := range staged {
		if err := pending.Commit(); err != nil {
			return err
		}
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func rollbackStagedDeletes(staged []*storage.StagedDelete) {
	for index := len(staged) - 1; index >= 0; index-- {
		_ = staged[index].Rollback()
	}
}

func (h *Handler) buildMelodyQuery(c fiber.Ctx) (*gorm.DB, error) {
	query := h.db.Model(&model.Melody{}).
		Joins("JOIN library_groups ON library_groups.id = melodies.group_id").
		Where("library_groups.deleted_at IS NULL")
	if !includePrivate(c) {
		query = query.Where("melodies.is_public = ? AND library_groups.is_public = ?", true, true)
	} else {
		publicValue, err := parseOptionalBool(c, "public")
		if err != nil {
			return nil, err
		}
		if publicValue == nil {
			publicValue, err = parseOptionalBool(c, "published")
			if err != nil {
				return nil, err
			}
		}
		if publicValue != nil {
			query = query.Where("melodies.is_public = ?", *publicValue)
		}
	}

	if groupID := parseQueryInt(c, "group_id", 0); groupID > 0 {
		query = query.Where("melodies.group_id = ?", groupID)
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
			Where("tags.slug = ? AND tags.deleted_at IS NULL", tagSlug)
	}
	if format := strings.TrimSpace(c.Query("format")); format != "" {
		normalized := storage.NormalizeFormat(format)
		if !storage.IsAllowedFormat(normalized) {
			return nil, errors.New("unsupported variant format")
		}
		query = query.Joins("JOIN file_variants ON file_variants.melody_id = melodies.id").
			Where("file_variants.format = ? AND file_variants.deleted_at IS NULL", normalized)
	}
	if q := strings.TrimSpace(c.Query("q")); q != "" {
		needle := containsPattern(strings.ToLower(q))
		query = query.Where("LOWER(melodies.title) LIKE ? ESCAPE '\\' OR LOWER(melodies.composer) LIKE ? ESCAPE '\\' OR LOWER(melodies.source_path) LIKE ? ESCAPE '\\'", needle, needle, needle)
	}

	return query, nil
}

func melodySort(sort string) string {
	switch strings.TrimSpace(sort) {
	case "title":
		return "melodies.title asc, melodies.id asc"
	case "-title":
		return "melodies.title desc, melodies.id desc"
	case "path":
		return "melodies.source_path asc, melodies.id asc"
	case "-path":
		return "melodies.source_path desc, melodies.id desc"
	case "created_at":
		return "melodies.created_at asc, melodies.id asc"
	case "-created_at":
		return "melodies.created_at desc, melodies.id desc"
	case "updated_at":
		return "melodies.updated_at asc, melodies.id asc"
	case "-updated_at":
		return "melodies.updated_at desc, melodies.id desc"
	case "sort_order":
		return "melodies.sort_order asc, melodies.title asc, melodies.id asc"
	default:
		return "melodies.sort_order asc, melodies.title asc, melodies.id asc"
	}
}
