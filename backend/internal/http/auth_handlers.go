package httpapi

import (
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	"shuuen-backend/internal/auth"
	"shuuen-backend/internal/model"
)

type registerRequest struct {
	Email       string `json:"email" validate:"required,email,max=320"`
	Password    string `json:"password" validate:"required,min=8,max=200"`
	DisplayName string `json:"display_name" validate:"max=160"`
}

type loginRequest struct {
	Email    string `json:"email" validate:"required,email,max=320"`
	Password string `json:"password" validate:"required"`
}

func (h *Handler) Register(c fiber.Ctx) error {
	var req registerRequest
	if err := c.Bind().Body(&req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	req.Email = strings.ToLower(strings.TrimSpace(req.Email))
	req.DisplayName = strings.TrimSpace(req.DisplayName)
	if err := h.validate.Struct(req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "validation failed", err.Error())
	}

	passwordHash, err := auth.HashPassword(req.Password)
	if err != nil {
		return err
	}

	user := model.User{
		Email:        req.Email,
		DisplayName:  req.DisplayName,
		PasswordHash: passwordHash,
		Role:         "user",
	}
	if err := h.db.Create(&user).Error; err != nil {
		if isUniqueConstraint(err) {
			return sendError(c, fiber.StatusConflict, "email is already registered")
		}
		return err
	}

	return h.authResponse(c, fiber.StatusCreated, user)
}

func (h *Handler) Login(c fiber.Ctx) error {
	var req loginRequest
	if err := c.Bind().Body(&req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	req.Email = strings.ToLower(strings.TrimSpace(req.Email))
	if err := h.validate.Struct(req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "validation failed", err.Error())
	}

	var user model.User
	err := h.db.Where("email = ?", req.Email).First(&user).Error
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return sendError(c, fiber.StatusUnauthorized, "invalid email or password")
		}
		return err
	}
	if !auth.CheckPassword(user.PasswordHash, req.Password) {
		return sendError(c, fiber.StatusUnauthorized, "invalid email or password")
	}

	return h.authResponse(c, fiber.StatusOK, user)
}

func (h *Handler) Me(c fiber.Ctx) error {
	var user model.User
	if err := h.db.First(&user, currentUserID(c)).Error; err != nil {
		return notFoundOrError(c, err, "user not found")
	}
	return sendData(c, fiber.StatusOK, user)
}

func (h *Handler) authResponse(c fiber.Ctx, status int, user model.User) error {
	token, expiresAt, err := h.auth.GenerateAccessToken(user)
	if err != nil {
		return err
	}
	return sendData(c, status, fiber.Map{
		"user":         user,
		"access_token": token,
		"token_type":   "Bearer",
		"expires_at":   expiresAt,
	})
}
