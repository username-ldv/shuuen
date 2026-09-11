package auth

import (
	"testing"

	"github.com/ncruces/go-sqlite3/gormlite"
	"gorm.io/gorm"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
	"shuuen-backend/internal/query"
)

func TestEnsureBootstrapAdminCreatesAndDoesNotResetExistingPassword(t *testing.T) {
	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := db.AutoMigrate(&model.User{}); err != nil {
		t.Fatal(err)
	}
	cfg := config.AuthConfig{
		BootstrapAdminUsername: "Admin",
		BootstrapAdminPassword: "initial-password",
		BootstrapAdminName:     "Administrator",
	}
	if _, err := EnsureBootstrapAdmin(t.Context(), db, cfg); err != nil {
		t.Fatal(err)
	}
	user, err := gorm.G[model.User](db).
		Where(query.User.UsernameKey.Eq("admin")).
		First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if user.Role != "admin" || !CheckPassword(user.PasswordHash, "initial-password") {
		t.Fatalf("unexpected bootstrap administrator: %#v", user)
	}

	cfg.BootstrapAdminPassword = "replacement-password"
	if _, err := EnsureBootstrapAdmin(t.Context(), db, cfg); err != nil {
		t.Fatal(err)
	}
	user, err = gorm.G[model.User](db).Where(query.User.ID.Eq(user.ID)).First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if !CheckPassword(user.PasswordHash, "initial-password") || CheckPassword(user.PasswordHash, "replacement-password") {
		t.Fatal("bootstrap rerun unexpectedly reset the existing password")
	}
}
