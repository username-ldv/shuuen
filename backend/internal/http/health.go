package httpapi

import (
	"context"
	"time"

	"github.com/gofiber/fiber/v3"
)

func (h *Handler) Health(c fiber.Ctx) error {
	if err := h.storage.Check(); err != nil {
		return sendError(c, fiber.StatusServiceUnavailable, "storage unavailable")
	}
	sqlDB, err := h.db.DB()
	if err != nil {
		return sendError(c, fiber.StatusServiceUnavailable, "database unavailable")
	}
	ctx, cancel := context.WithTimeout(c.Context(), 2*time.Second)
	defer cancel()
	if err := sqlDB.PingContext(ctx); err != nil {
		return sendError(c, fiber.StatusServiceUnavailable, "database unavailable")
	}
	return sendData(c, fiber.StatusOK, fiber.Map{"status": "ok"})
}
