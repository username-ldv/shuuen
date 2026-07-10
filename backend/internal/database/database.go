package database

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/ncruces/go-sqlite3/gormlite"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
)

func Connect(cfg config.DatabaseConfig) (*gorm.DB, error) {
	gormConfig := &gorm.Config{
		Logger: logger.Default.LogMode(logger.Warn),
	}

	switch cfg.Driver {
	case "postgres":
		return gorm.Open(postgres.Open(cfg.DSN), gormConfig)
	case "sqlite":
		if err := ensureSQLiteDir(cfg.DSN); err != nil {
			return nil, err
		}
		return gorm.Open(gormlite.Open(cfg.DSN), gormConfig)
	default:
		return nil, fmt.Errorf("unsupported database driver %q", cfg.Driver)
	}
}

func Migrate(db *gorm.DB) error {
	return db.AutoMigrate(
		&model.User{},
		&model.LibraryGroup{},
		&model.Tag{},
		&model.Melody{},
		&model.FileVariant{},
	)
}

func ensureSQLiteDir(dsn string) error {
	if dsn == "" || dsn == ":memory:" {
		return nil
	}
	dir := filepath.Dir(dsn)
	if dir == "." || dir == "" {
		return nil
	}
	return os.MkdirAll(dir, 0o755)
}
