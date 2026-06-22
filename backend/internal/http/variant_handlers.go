package httpapi

import (
	"strconv"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	"shuuen-backend/internal/model"
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
	if err := h.db.First(&model.Melody{}, melodyID).Error; err != nil {
		return notFoundOrError(c, err, "melody not found")
	}

	var rows []model.FileVariant
	if err := h.db.Where("melody_id = ?", melodyID).Order("is_primary desc, created_at asc").Find(&rows).Error; err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, rows)
}

func (h *Handler) GetVariant(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	var variant model.FileVariant
	if err := h.db.Preload("Melody").First(&variant, id).Error; err != nil {
		return notFoundOrError(c, err, "variant not found")
	}
	return sendData(c, fiber.StatusOK, variant)
}

func (h *Handler) UploadVariant(c fiber.Ctx) error {
	melodyID, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	var melody model.Melody
	if err := h.db.Preload("Group").First(&melody, melodyID).Error; err != nil {
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

	if _, err := h.catalog.Scan(c.Context()); err != nil {
		_ = h.storage.Delete(stored.StoragePath)
		return err
	}

	var variant model.FileVariant
	if err := h.db.Where("storage_path = ?", stored.StoragePath).First(&variant).Error; err != nil {
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

	var variant model.FileVariant
	if err := h.db.First(&variant, id).Error; err != nil {
		return notFoundOrError(c, err, "variant not found")
	}

	if req.IsPrimary == nil {
		return sendData(c, fiber.StatusOK, variant)
	}

	err = h.db.Transaction(func(tx *gorm.DB) error {
		if *req.IsPrimary {
			if err := tx.Model(&model.FileVariant{}).
				Where("melody_id = ? AND id <> ?", variant.MelodyID, variant.ID).
				Update("is_primary", false).Error; err != nil {
				return err
			}
		}
		return tx.Model(&variant).Update("is_primary", *req.IsPrimary).Error
	})
	if err != nil {
		return err
	}

	if err := h.db.First(&variant, variant.ID).Error; err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, variant)
}

func (h *Handler) DownloadVariant(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	var variant model.FileVariant
	if err := h.db.First(&variant, id).Error; err != nil {
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

	var variant model.FileVariant
	if err := h.db.First(&variant, id).Error; err != nil {
		return notFoundOrError(c, err, "variant not found")
	}

	if err := h.storage.Delete(variant.StoragePath); err != nil {
		return err
	}
	if err := h.db.Delete(&variant).Error; err != nil {
		return err
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) RescanCatalog(c fiber.Ctx) error {
	result, err := h.catalog.Scan(c.Context())
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, result)
}

func parseFormBool(value string, fallback bool) bool {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(trimmed)
	if err != nil {
		return fallback
	}
	return parsed
}
