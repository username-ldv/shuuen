package httpapi

import (
	"errors"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"shuuen-backend/internal/model"
	dbquery "shuuen-backend/internal/query"
	"shuuen-backend/internal/storage"
)

func (h *Handler) ListMelodies(c fiber.Ctx) error {
	limit, offset := parsePagination(c)
	query, err := h.buildMelodyQuery(c)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}
	total, err := query.Distinct("melodies.id").Count(c.Context(), "melodies.id")
	if err != nil {
		return err
	}

	rows, err := query.
		Preload(dbquery.Melody.Group.Name(), nil).
		Preload(dbquery.Melody.Tags.Name(), nil).
		Preload(dbquery.Melody.Variants.Name(), nil).
		Distinct("melodies.*").
		Order(melodySort(c.Query("sort"))).
		Limit(limit).
		Offset(offset).
		Find(c.Context())
	if err != nil {
		return err
	}
	return c.JSON(listResponse{Data: rows, Meta: listMeta{Limit: limit, Offset: offset, Total: total}})
}

func (h *Handler) GetMelody(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	melody, err := gorm.G[model.Melody](h.db).
		Preload(dbquery.Melody.Group.Name(), nil).
		Preload(dbquery.Melody.Tags.Name(), nil).
		Preload(dbquery.Melody.Variants.Name(), nil).
		Where(dbquery.Melody.ID.Eq(id)).
		First(c.Context())
	if err != nil {
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

	melody, err := gorm.G[model.Melody](h.db).
		Preload(dbquery.Melody.Variants.Name(), nil).
		Where(dbquery.Melody.ID.Eq(id)).
		First(c.Context())
	if err != nil {
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
		if _, err := gorm.G[model.FileVariant](tx).
			Where(dbquery.FileVariant.MelodyID.Eq(melody.ID)).
			Delete(c.Context()); err != nil {
			return err
		}
		if _, err := gorm.G[model.Melody](tx).
			Where(dbquery.Melody.ID.Eq(melody.ID)).
			Set(dbquery.Melody.Tags.Unlink()).
			Update(c.Context()); err != nil {
			return err
		}
		_, err := gorm.G[model.Melody](tx).
			Where(dbquery.Melody.ID.Eq(melody.ID)).
			Delete(c.Context())
		return err
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

func (h *Handler) buildMelodyQuery(c fiber.Ctx) (gorm.ChainInterface[model.Melody], error) {
	query := gorm.G[model.Melody](h.db).
		Joins(clause.InnerJoin.Association(dbquery.Melody.Group.Name()), nil)
	if !includePrivate(c) {
		query = query.Where(
			dbquery.Melody.IsPublic.WithTable("melodies").Eq(true),
			dbquery.LibraryGroup.IsPublic.WithTable("Group").Eq(true),
		)
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
		query = query.Where("EXISTS (SELECT 1 FROM melody_tags WHERE melody_tags.melody_id = melodies.id AND melody_tags.tag_id = ?)", tagID)
	}
	if tagSlug := strings.TrimSpace(c.Query("tag")); tagSlug != "" {
		query = query.Where("EXISTS (SELECT 1 FROM melody_tags JOIN tags ON tags.id = melody_tags.tag_id WHERE melody_tags.melody_id = melodies.id AND tags.slug = ? AND tags.deleted_at IS NULL)", tagSlug)
	}
	if format := strings.TrimSpace(c.Query("format")); format != "" {
		normalized := storage.NormalizeFormat(format)
		if !storage.IsAllowedFormat(normalized) {
			return nil, errors.New("unsupported variant format")
		}
		query = query.Where("EXISTS (SELECT 1 FROM file_variants WHERE file_variants.melody_id = melodies.id AND file_variants.format = ? AND file_variants.deleted_at IS NULL)", normalized)
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
