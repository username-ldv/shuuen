package httpapi

import (
	"errors"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	"shuuen-backend/internal/auth"
	"shuuen-backend/internal/model"
)

func AuthRequired(authService *auth.Service, db *gorm.DB) fiber.Handler {
	return func(c fiber.Ctx) error {
		return authenticate(c, authService, db, true)
	}
}

func OptionalAuth(authService *auth.Service, db *gorm.DB) fiber.Handler {
	return func(c fiber.Ctx) error {
		return authenticate(c, authService, db, false)
	}
}

func authenticate(c fiber.Ctx, authService *auth.Service, db *gorm.DB, required bool) error {
	header := strings.TrimSpace(c.Get("Authorization"))
	if header == "" {
		if required {
			return sendError(c, fiber.StatusUnauthorized, "missing authorization header")
		}
		return c.Next()
	}

	parts := strings.SplitN(header, " ", 2)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
		return sendError(c, fiber.StatusUnauthorized, "authorization header must use Bearer token")
	}
	claims, err := authService.ParseAccessToken(strings.TrimSpace(parts[1]))
	if err != nil {
		return sendError(c, fiber.StatusUnauthorized, "invalid or expired token")
	}

	var user model.User
	if err := db.Select("id", "username", "role", "token_version").First(&user, claims.UserID).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return sendError(c, fiber.StatusUnauthorized, "account is no longer available")
		}
		return err
	}
	if claims.TokenVersion != user.TokenVersion {
		return sendError(c, fiber.StatusUnauthorized, "token has been revoked")
	}
	c.Locals("user_id", user.ID)
	c.Locals("username", user.Username)
	c.Locals("user_role", user.Role)
	return c.Next()
}

func AdminRequired(c fiber.Ctx) error {
	if currentUserRole(c) != "admin" {
		return sendError(c, fiber.StatusForbidden, "administrator access is required")
	}
	return c.Next()
}

func AuthenticatedRequired(c fiber.Ctx) error {
	if currentUserID(c) == 0 {
		return sendError(c, fiber.StatusUnauthorized, "missing authorization header")
	}
	return c.Next()
}

func VisibilityScope(c fiber.Ctx) error {
	requested, err := parseOptionalBool(c, "include_private")
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}
	if requested != nil && *requested {
		if currentUserRole(c) != "admin" {
			return sendError(c, fiber.StatusForbidden, "administrator access is required to include private resources")
		}
		c.Locals("include_private", true)
	}
	return c.Next()
}

func currentUserID(c fiber.Ctx) uint {
	value, ok := c.Locals("user_id").(uint)
	if !ok {
		return 0
	}
	return value
}

func currentUserRole(c fiber.Ctx) string {
	value, _ := c.Locals("user_role").(string)
	return value
}

func includePrivate(c fiber.Ctx) bool {
	value, _ := c.Locals("include_private").(bool)
	return value
}
