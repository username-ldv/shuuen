package auth

import (
	"errors"
	"regexp"
	"strings"

	"gorm.io/gorm"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
)

var accountUsernamePattern = regexp.MustCompile(`^[A-Za-z0-9_]{3,20}$`)

// EnsureBootstrapAdmin creates the configured initial administrator once. If
// the administrator already exists, its password is left unchanged.
func EnsureBootstrapAdmin(db *gorm.DB, cfg config.AuthConfig) (bool, error) {
	if cfg.BootstrapAdminUsername == "" {
		return false, nil
	}
	username := strings.TrimSpace(cfg.BootstrapAdminUsername)
	if !accountUsernamePattern.MatchString(username) {
		return false, errors.New("BOOTSTRAP_ADMIN_USERNAME must be 3-20 letters, numbers, or underscores")
	}
	usernameKey := strings.ToLower(username)

	var user model.User
	err := db.Unscoped().Where("username_key = ?", usernameKey).First(&user).Error
	if err == nil {
		if user.Role != "admin" {
			return false, errors.New("BOOTSTRAP_ADMIN_USERNAME belongs to an existing non-admin account")
		}
		updates := map[string]any{"deleted_at": nil}
		if strings.TrimSpace(user.DisplayName) == "" {
			updates["display_name"] = strings.TrimSpace(cfg.BootstrapAdminName)
		}
		return true, db.Unscoped().Model(&user).Updates(updates).Error
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return false, err
	}

	passwordHash, err := HashPassword(cfg.BootstrapAdminPassword)
	if err != nil {
		return false, err
	}
	user = model.User{
		Username:     username,
		UsernameKey:  usernameKey,
		DisplayName:  strings.TrimSpace(cfg.BootstrapAdminName),
		PasswordHash: passwordHash,
		Role:         "admin",
	}
	return true, db.Create(&user).Error
}
