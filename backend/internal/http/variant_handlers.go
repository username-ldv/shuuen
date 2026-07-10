package httpapi

import (
	"errors"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"shuuen-backend/internal/catalog"
	"shuuen-backend/internal/model"
	dbquery "shuuen-backend/internal/query"
	"shuuen-backend/internal/storage"
)

type updateVariantRequest struct {
	IsPrimary *bool `json:"is_primary"`
}

func (h *Handler) ListVariants(c fiber.Ctx) error {
	melodyID, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}
	melody, err := gorm.G[model.Melody](h.db).
		Preload(dbquery.Melody.Group.Name(), nil).
		Where(dbquery.Melody.ID.Eq(melodyID)).
		First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "melody not found")
	}
	if !canViewMelody(c, melody) {
		return sendError(c, fiber.StatusNotFound, "melody not found")
	}

	rows, err := gorm.G[model.FileVariant](h.db).
		Where(dbquery.FileVariant.MelodyID.Eq(melodyID)).
		Order(dbquery.FileVariant.IsPrimary.Desc()).
		Order(dbquery.FileVariant.CreatedAt.Asc()).
		Find(c.Context())
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, rows)
}

func (h *Handler) GetVariant(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	variant, err := gorm.G[model.FileVariant](h.db).
		Preload(dbquery.FileVariant.Melody.Name()+"."+dbquery.Melody.Group.Name(), nil).
		Where(dbquery.FileVariant.ID.Eq(id)).
		First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "variant not found")
	}
	if !canViewMelody(c, variant.Melody) {
		return sendError(c, fiber.StatusNotFound, "variant not found")
	}
	return sendData(c, fiber.StatusOK, variant)
}

func (h *Handler) UploadVariant(c fiber.Ctx) error {
	melodyID, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	melody, err := gorm.G[model.Melody](h.db).
		Preload(dbquery.Melody.Group.Name(), nil).
		Where(dbquery.Melody.ID.Eq(melodyID)).
		First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "melody not found")
	}

	header, err := c.FormFile("file")
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, "file field is required")
	}

	format := storage.NormalizeFormat(c.FormValue("format"))
	if format == "" {
		format = storage.InferFormat(header.Filename)
	}
	if !storage.IsAllowedFormat(format) {
		return sendError(c, fiber.StatusBadRequest, "unsupported variant format")
	}

	stored, err := h.storage.SaveVariant(melody.Group.Path, melody.FileStem, format, header)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}

	var variant model.FileVariant
	err = h.db.Transaction(func(tx *gorm.DB) error {
		if _, err := gorm.G[model.Melody](tx, clause.Locking{Strength: "UPDATE"}).
			Where(dbquery.Melody.ID.Eq(melody.ID)).
			First(c.Context()); err != nil {
			return err
		}
		activeVariants, err := gorm.G[model.FileVariant](tx).
			Where(dbquery.FileVariant.MelodyID.Eq(melody.ID)).
			Count(c.Context(), "*")
		if err != nil {
			return err
		}
		variant, err = gorm.G[model.FileVariant](tx).
			Scopes(dbquery.Unscoped).
			Where(dbquery.FileVariant.StoragePath.Eq(stored.StoragePath)).
			First(c.Context())
		if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
			return err
		}
		if errors.Is(err, gorm.ErrRecordNotFound) {
			variant = model.FileVariant{StoragePath: stored.StoragePath}
		}
		variant.MelodyID = melody.ID
		variant.Format = format
		variant.OriginalName = stored.OriginalName
		variant.StoredName = stored.StoredName
		variant.MimeType = stored.MimeType
		variant.SizeBytes = stored.SizeBytes
		variant.FileModTime = stored.FileModTime
		variant.ChecksumSHA = stored.ChecksumSHA
		variant.IsPrimary = activeVariants == 0
		variant.ScanID = "upload"
		variant.DeletedAt = gorm.DeletedAt{}
		if variant.ID == 0 {
			return gorm.G[model.FileVariant](tx).Omit(dbquery.FileVariant.Melody.Name()).Create(c.Context(), &variant)
		}
		_, err = gorm.G[model.FileVariant](tx).
			Scopes(dbquery.Unscoped).
			Where(dbquery.FileVariant.ID.Eq(variant.ID)).
			Select("*").
			Omit(dbquery.FileVariant.Melody.Name()).
			Updates(c.Context(), variant)
		return err
	})
	if err != nil {
		_ = h.storage.Delete(stored.StoragePath)
		return err
	}
	variant, err = gorm.G[model.FileVariant](h.db).
		Where(dbquery.FileVariant.ID.Eq(variant.ID)).
		First(c.Context())
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusCreated, variant)
}

func (h *Handler) UpdateVariant(c fiber.Ctx) error {
	var req updateVariantRequest
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

	variant, err := gorm.G[model.FileVariant](h.db).
		Preload(dbquery.FileVariant.Melody.Name()+"."+dbquery.Melody.Group.Name(), nil).
		Where(dbquery.FileVariant.ID.Eq(id)).
		First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "variant not found")
	}
	if !canViewMelody(c, variant.Melody) {
		return sendError(c, fiber.StatusNotFound, "variant not found")
	}

	if req.IsPrimary == nil {
		return sendData(c, fiber.StatusOK, variant)
	}

	err = h.db.Transaction(func(tx *gorm.DB) error {
		if *req.IsPrimary {
			if _, err := gorm.G[model.FileVariant](tx).
				Where("melody_id = ? AND id <> ?", variant.MelodyID, variant.ID).
				Update(c.Context(), "is_primary", false); err != nil {
				return err
			}
		}
		_, err := gorm.G[model.FileVariant](tx).
			Where(dbquery.FileVariant.ID.Eq(variant.ID)).
			Update(c.Context(), "is_primary", *req.IsPrimary)
		return err
	})
	if err != nil {
		return err
	}

	variant, err = gorm.G[model.FileVariant](h.db).
		Where(dbquery.FileVariant.ID.Eq(variant.ID)).
		First(c.Context())
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, variant)
}

func (h *Handler) DownloadVariant(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	variant, err := gorm.G[model.FileVariant](h.db).
		Where(dbquery.FileVariant.ID.Eq(id)).
		First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "variant not found")
	}

	path, err := h.storage.AbsolutePath(variant.StoragePath)
	if err != nil {
		return err
	}
	return c.Download(path, variant.OriginalName)
}

func (h *Handler) DeleteVariant(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	variant, err := gorm.G[model.FileVariant](h.db).
		Where(dbquery.FileVariant.ID.Eq(id)).
		First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "variant not found")
	}

	pending, err := h.storage.StageDelete(variant.StoragePath)
	if err != nil {
		return err
	}
	if _, err := gorm.G[model.FileVariant](h.db).
		Where(dbquery.FileVariant.ID.Eq(variant.ID)).
		Delete(c.Context()); err != nil {
		_ = pending.Rollback()
		return err
	}
	if err := pending.Commit(); err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) RescanCatalog(c fiber.Ctx) error {
	result, err := h.catalog.Scan(c.Context())
	if err != nil {
		if errors.Is(err, catalog.ErrScanInProgress) {
			return sendError(c, fiber.StatusConflict, err.Error())
		}
		return err
	}
	return sendData(c, fiber.StatusOK, result)
}

func canViewMelody(c fiber.Ctx, melody model.Melody) bool {
	return includePrivate(c) || (melody.IsPublic && melody.Group.IsPublic)
}
