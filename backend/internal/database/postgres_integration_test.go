package database

import (
	"os"
	"testing"
	"time"

	"shuuen-backend/internal/config"
)

func TestPostgresMigrations(t *testing.T) {
	dsn := os.Getenv("TEST_POSTGRES_DSN")
	if dsn == "" {
		t.Skip("TEST_POSTGRES_DSN is not set")
	}
	db, err := Connect(config.DatabaseConfig{
		Driver: "postgres", DSN: dsn,
		MaxOpenConns: 4, MaxIdleConns: 2,
		ConnMaxLifetime: time.Minute, ConnMaxIdleTime: time.Minute,
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := Migrate(db); err != nil {
		t.Fatal(err)
	}
	if err := Migrate(db); err != nil {
		t.Fatalf("Postgres migrations are not idempotent: %v", err)
	}
}
