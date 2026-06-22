package httpapi

import (
	"strings"

	"github.com/gofiber/fiber/v3"

	"shuuen-backend/internal/auth"
)

func AuthRequired(authService *auth.Service) fiber.Handler {
	return func(c fiber.Ctx) error {
		header := strings.TrimSpace(c.Get("Authorization"))
		if header == "" {
			return sendError(c, fiber.StatusUnauthorized, "missing authorization header")
		}

		parts := strings.SplitN(header, " ", 2)
		if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
			return sendError(c, fiber.StatusUnauthorized, "authorization header must use Bearer token")
		}

		claims, err := authService.ParseAccessToken(strings.TrimSpace(parts[1]))
		if err != nil {
			return sendError(c, fiber.StatusUnauthorized, "invalid or expired token")
		}

		c.Locals("user_id", claims.UserID)
		c.Locals("user_email", claims.Email)
		c.Locals("user_role", claims.Role)
		return c.Next()
	}
}

func currentUserID(c fiber.Ctx) uint {
	value, ok := c.Locals("user_id").(uint)
	if !ok {
		return 0
	}
	return value
}
