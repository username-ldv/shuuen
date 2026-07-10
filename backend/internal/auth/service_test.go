package auth

import (
	"testing"
	"time"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
)

func TestPasswordHashAndCheck(t *testing.T) {
	hash, err := HashPassword("strong-password")
	if err != nil {
		t.Fatalf("HashPassword returned error: %v", err)
	}

	if !CheckPassword(hash, "strong-password") {
		t.Fatal("expected password to match hash")
	}
	if CheckPassword(hash, "wrong-password") {
		t.Fatal("expected wrong password to fail")
	}
}

func TestGenerateAndParseAccessToken(t *testing.T) {
	service := NewService(config.AuthConfig{
		JWTSecret:      "test-secret-that-is-long-enough",
		JWTIssuer:      "shuuen-test",
		AccessTokenTTL: time.Hour,
	})

	user := model.User{
		Base:         model.Base{ID: 42},
		Username:     "TestUser",
		UsernameKey:  "testuser",
		Role:         "user",
		TokenVersion: 3,
	}

	token, expiresAt, err := service.GenerateAccessToken(user)
	if err != nil {
		t.Fatalf("GenerateAccessToken returned error: %v", err)
	}
	if token == "" {
		t.Fatal("expected token to be non-empty")
	}
	if !expiresAt.After(time.Now()) {
		t.Fatal("expected token expiration to be in the future")
	}

	claims, err := service.ParseAccessToken(token)
	if err != nil {
		t.Fatalf("ParseAccessToken returned error: %v", err)
	}
	if claims.UserID != user.ID || claims.Username != user.Username || claims.Role != user.Role || claims.TokenVersion != user.TokenVersion {
		t.Fatalf("unexpected claims: %#v", claims)
	}
}
