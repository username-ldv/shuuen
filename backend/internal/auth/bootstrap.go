package auth

import (
	"context"
	"errors"
	"regexp"
	"strings"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
	"shuuen-backend/internal/query"
)

var accountUsernamePattern = regexp.MustCompile(`^[A-Za-z0-9_]{3,20}$`)

// EnsureBootstrapAdmin creates the configured initial administrator once. If
// the administrator already exists, its password is left unchanged.
func EnsureBootstrapAdmin(ctx context.Context, db *gorm.DB, cfg config.AuthConfig) (bool, error) {
	if cfg.BootstrapAdminUsername == "" {
		return false, nil
	}
	username := strings.TrimSpace(cfg.BootstrapAdminUsername)
	if !accountUsernamePattern.MatchString(username) {
		return false, errors.New("BOOTSTRAP_ADMIN_USERNAME must be 3-20 letters, numbers, or underscores")
	}
	usernameKey := strings.ToLower(username)

	user, err := gorm.G[model.User](db).
		Scopes(query.Unscoped).
		Where(query.User.UsernameKey.Eq(usernameKey)).
		First(ctx)
	if err == nil {
		if user.Role != "admin" {
			return false, errors.New("BOOTSTRAP_ADMIN_USERNAME belongs to an existing non-admin account")
		}
		updates := []clause.Assigner{query.User.DeletedAt.Set(gorm.DeletedAt{})}
		if strings.TrimSpace(user.DisplayName) == "" {
			updates = append(updates, query.User.DisplayName.Set(strings.TrimSpace(cfg.BootstrapAdminName)))
		}
		_, err = gorm.G[model.User](db).
			Scopes(query.Unscoped).
			Where(query.User.ID.Eq(user.ID)).
			Set(updates...).
			Update(ctx)
		return true, err
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
	return true, gorm.G[model.User](db).Create(ctx, &user)
}
