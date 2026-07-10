package httpapi

import (
	"errors"
	"strconv"
	"strings"

	"github.com/gofiber/fiber/v3"
	"github.com/rs/zerolog/log"
	"gorm.io/gorm"
)

type apiError struct {
	Error   string `json:"error"`
	Details any    `json:"details,omitempty"`
}

type listResponse struct {
	Data any      `json:"data"`
	Meta listMeta `json:"meta"`
}

type listMeta struct {
	Limit  int   `json:"limit"`
	Offset int   `json:"offset"`
	Total  int64 `json:"total"`
}

func errorHandler(c fiber.Ctx, err error) error {
	status := fiber.StatusInternalServerError
	message := "internal server error"

	var fiberErr *fiber.Error
	if errors.As(err, &fiberErr) {
		status = fiberErr.Code
		message = fiberErr.Message
	}
	if status >= fiber.StatusInternalServerError {
		requestID, _ := c.Locals("requestid").(string)
		log.Error().Err(err).Str("request_id", requestID).Str("method", c.Method()).Str("path", c.Path()).Msg("request failed")
	}

	return c.Status(status).JSON(apiError{Error: message})
}

func sendError(c fiber.Ctx, status int, message string, details ...any) error {
	var detail any
	if len(details) > 0 {
		detail = details[0]
	}
	return c.Status(status).JSON(apiError{Error: message, Details: detail})
}

func sendData(c fiber.Ctx, status int, data any) error {
	return c.Status(status).JSON(fiber.Map{"data": data})
}

func notFoundOrError(c fiber.Ctx, err error, message string) error {
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return sendError(c, fiber.StatusNotFound, message)
	}
	return err
}

func isUniqueConstraint(err error) bool {
	if err == nil {
		return false
	}
	message := strings.ToLower(err.Error())
	return strings.Contains(message, "unique") || strings.Contains(message, "duplicate")
}

func parsePagination(c fiber.Ctx) (int, int) {
	limit := parseQueryInt(c, "limit", 50)
	offset := parseQueryInt(c, "offset", 0)
	if limit <= 0 {
		limit = 50
	}
	if limit > 200 {
		limit = 200
	}
	if offset < 0 {
		offset = 0
	}
	return limit, offset
}

func parseBoolQuery(c fiber.Ctx, key string, fallback bool) bool {
	raw := strings.ToLower(strings.TrimSpace(c.Query(key)))
	switch raw {
	case "true", "1", "yes", "y":
		return true
	case "false", "0", "no", "n":
		return false
	default:
		return fallback
	}
}

func parseOptionalBool(c fiber.Ctx, key string) (*bool, error) {
	raw := strings.ToLower(strings.TrimSpace(c.Query(key)))
	if raw == "" {
		return nil, nil
	}
	value, err := strconv.ParseBool(raw)
	if err != nil {
		return nil, fiber.NewError(fiber.StatusBadRequest, key+" must be true or false")
	}
	return &value, nil
}

func parseQueryInt(c fiber.Ctx, key string, fallback int) int {
	raw := strings.TrimSpace(c.Query(key))
	if raw == "" {
		return fallback
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		return fallback
	}
	return value
}

func parseParamUint(c fiber.Ctx, key string) (uint, error) {
	raw := strings.TrimSpace(c.Params(key))
	value, err := strconv.ParseUint(raw, 10, 64)
	if err != nil || value == 0 {
		return 0, fiber.NewError(fiber.StatusBadRequest, "invalid "+key)
	}
	return uint(value), nil
}

func cleanURLPath(value string) string {
	value = strings.TrimSpace(value)
	value = strings.TrimPrefix(value, "/")
	value = strings.TrimSuffix(value, "/")
	if value == "." {
		return ""
	}
	return value
}

func containsPattern(value string) string {
	return "%" + escapeLike(value) + "%"
}

func descendantPattern(value string) string {
	return escapeLike(value) + "/%"
}

func escapeLike(value string) string {
	value = strings.ReplaceAll(value, `\`, `\\`)
	value = strings.ReplaceAll(value, "%", `\%`)
	value = strings.ReplaceAll(value, "_", `\_`)
	return value
}
