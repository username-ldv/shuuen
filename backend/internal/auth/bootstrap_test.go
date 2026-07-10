package auth

import (
	"testing"

	"github.com/ncruces/go-sqlite3/gormlite"
	"gorm.io/gorm"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
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
	if _, err := EnsureBootstrapAdmin(db, cfg); err != nil {
		t.Fatal(err)
	}
	var user model.User
	if err := db.Where("username_key = ?", "admin").First(&user).Error; err != nil {
		t.Fatal(err)
	}
	if user.Role != "admin" || !CheckPassword(user.PasswordHash, "initial-password") {
		t.Fatalf("unexpected bootstrap administrator: %#v", user)
	}

	cfg.BootstrapAdminPassword = "replacement-password"
	if _, err := EnsureBootstrapAdmin(db, cfg); err != nil {
		t.Fatal(err)
	}
	if err := db.First(&user, user.ID).Error; err != nil {
		t.Fatal(err)
	}
	if !CheckPassword(user.PasswordHash, "initial-password") || CheckPassword(user.PasswordHash, "replacement-password") {
		t.Fatal("bootstrap rerun unexpectedly reset the existing password")
	}
}
