package config

import (
	"strings"
	"testing"
)

func TestLoadRejectsMalformedEnvironmentValues(t *testing.T) {
	t.Setenv("APP_ENV", "development")
	t.Setenv("HTTP_READ_TIMEOUT", "not-a-duration")
	_, err := Load()
	if err == nil || !strings.Contains(err.Error(), "HTTP_READ_TIMEOUT") {
		t.Fatalf("Load() error = %v, want invalid HTTP_READ_TIMEOUT", err)
	}
}

func TestProductionDefaultsDisableRegistrationAndCORS(t *testing.T) {
	t.Setenv("APP_ENV", "production")
	t.Setenv("JWT_SECRET", "a-production-secret-that-is-at-least-32-bytes")
	t.Setenv("CORS_ALLOWED_ORIGINS", "")
	t.Setenv("REGISTRATION_ENABLED", "")
	t.Setenv("HTTP_READ_TIMEOUT", "")

	cfg, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Auth.RegistrationEnabled {
		t.Fatal("production registration should default to disabled")
	}
	if len(cfg.HTTP.CORSOrigins) != 0 {
		t.Fatalf("production CORS origins = %#v, want none by default", cfg.HTTP.CORSOrigins)
	}
}
