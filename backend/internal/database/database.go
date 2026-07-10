package database

import (
	"fmt"
	stdlog "log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/ncruces/go-sqlite3/gormlite"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
)

func Connect(cfg config.DatabaseConfig) (*gorm.DB, error) {
	gormConfig := &gorm.Config{
		Logger: logger.New(stdlog.New(os.Stderr, "", stdlog.LstdFlags), logger.Config{
			SlowThreshold:             500 * time.Millisecond,
			LogLevel:                  logger.Warn,
			IgnoreRecordNotFoundError: true,
			ParameterizedQueries:      true,
			Colorful:                  false,
		}),
	}

	switch cfg.Driver {
	case "postgres":
		db, err := gorm.Open(postgres.Open(cfg.DSN), gormConfig)
		if err != nil {
			return nil, err
		}
		return configurePool(db, cfg)
	case "sqlite":
		if err := ensureSQLiteDir(cfg.DSN); err != nil {
			return nil, err
		}
		db, err := gorm.Open(gormlite.Open(sqliteConnectionDSN(cfg.DSN)), gormConfig)
		if err != nil {
			return nil, err
		}
		return configurePool(db, cfg)
	default:
		return nil, fmt.Errorf("unsupported database driver %q", cfg.Driver)
	}
}

type schemaMigration struct {
	Version   uint      `gorm:"primaryKey"`
	AppliedAt time.Time `gorm:"not null"`
}

type migration struct {
	version uint
	apply   func(*gorm.DB) error
}

var migrations = []migration{
	{version: 1, apply: migrateInitialSchema},
	{version: 2, apply: addCatalogQueryIndexes},
	{version: 3, apply: enforcePrimaryVariantInvariant},
	{version: 4, apply: addUserTokenVersion},
	{version: 5, apply: constrainPostgresCatalogPaths},
}

func Migrate(db *gorm.DB) error {
	if err := db.AutoMigrate(&schemaMigration{}); err != nil {
		return err
	}
	for _, item := range migrations {
		var count int64
		if err := db.Model(&schemaMigration{}).Where("version = ?", item.version).Count(&count).Error; err != nil {
			return err
		}
		if count > 0 {
			continue
		}
		if err := db.Transaction(func(tx *gorm.DB) error {
			if err := item.apply(tx); err != nil {
				return fmt.Errorf("migration %d: %w", item.version, err)
			}
			return tx.Create(&schemaMigration{Version: item.version, AppliedAt: time.Now().UTC()}).Error
		}); err != nil {
			return err
		}
	}
	return nil
}

func migrateInitialSchema(db *gorm.DB) error {
	coreTablesExist := db.Migrator().HasTable("library_groups") && db.Migrator().HasTable("melodies") && db.Migrator().HasTable("file_variants")
	if !coreTablesExist {
		return db.AutoMigrate(
			&model.User{},
			&model.LibraryGroup{},
			&model.Tag{},
			&model.Melody{},
			&model.FileVariant{},
		)
	}
	if err := migrateVisibilityColumn(db, "library_groups", "is_active"); err != nil {
		return err
	}
	if err := migrateVisibilityColumn(db, "melodies", "is_published"); err != nil {
		return err
	}
	if !db.Migrator().HasColumn("file_variants", "file_mod_time") {
		if err := db.Migrator().AddColumn(&model.FileVariant{}, "FileModTime"); err != nil {
			return err
		}
	}
	return nil
}

func migrateVisibilityColumn(db *gorm.DB, table string, legacyColumn string) error {
	if !db.Migrator().HasTable(table) || !db.Migrator().HasColumn(table, legacyColumn) {
		return nil
	}
	if !db.Migrator().HasColumn(table, "is_public") {
		return db.Exec(fmt.Sprintf("ALTER TABLE %s RENAME COLUMN %s TO is_public", table, legacyColumn)).Error
	}
	return db.Exec(fmt.Sprintf("UPDATE %s SET is_public = %s", table, legacyColumn)).Error
}

func addCatalogQueryIndexes(db *gorm.DB) error {
	statements := []string{
		"CREATE INDEX IF NOT EXISTS idx_library_groups_public_parent_sort ON library_groups (deleted_at, is_public, parent_id, sort_order, name, id)",
		"CREATE INDEX IF NOT EXISTS idx_melodies_public_sort ON melodies (deleted_at, is_public, sort_order, title, id)",
		"CREATE INDEX IF NOT EXISTS idx_melodies_group_public_sort ON melodies (deleted_at, group_id, is_public, sort_order, title, id)",
		"CREATE INDEX IF NOT EXISTS idx_melodies_created_sort ON melodies (deleted_at, created_at, id)",
		"CREATE INDEX IF NOT EXISTS idx_melodies_updated_sort ON melodies (deleted_at, updated_at, id)",
		"CREATE INDEX IF NOT EXISTS idx_file_variants_melody_primary ON file_variants (deleted_at, melody_id, is_primary, created_at, id)",
	}
	for _, statement := range statements {
		if err := db.Exec(statement).Error; err != nil {
			return err
		}
	}
	return nil
}

func enforcePrimaryVariantInvariant(db *gorm.DB) error {
	var melodyIDs []uint
	if err := db.Model(&model.FileVariant{}).
		Select("melody_id").
		Where("is_primary = ?", true).
		Group("melody_id").
		Having("COUNT(*) > 1").
		Scan(&melodyIDs).Error; err != nil {
		return err
	}
	for _, melodyID := range melodyIDs {
		var keep model.FileVariant
		if err := db.Where("melody_id = ? AND is_primary = ?", melodyID, true).Order("id asc").First(&keep).Error; err != nil {
			return err
		}
		if err := db.Model(&model.FileVariant{}).
			Where("melody_id = ? AND is_primary = ? AND id <> ?", melodyID, true, keep.ID).
			Update("is_primary", false).Error; err != nil {
			return err
		}
	}
	return db.Exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_file_variants_one_primary ON file_variants (melody_id) WHERE is_primary = true AND deleted_at IS NULL").Error
}

func addUserTokenVersion(db *gorm.DB) error {
	if !db.Migrator().HasColumn("users", "token_version") {
		if err := db.Migrator().AddColumn(&model.User{}, "TokenVersion"); err != nil {
			return err
		}
	}
	return db.Model(&model.User{}).Where("token_version = 0 OR token_version IS NULL").Update("token_version", 1).Error
}

func constrainPostgresCatalogPaths(db *gorm.DB) error {
	if db.Dialector.Name() != "postgres" {
		return nil
	}
	columns := []struct {
		table  string
		column string
	}{
		{table: "library_groups", column: "path"},
		{table: "melodies", column: "source_path"},
		{table: "file_variants", column: "storage_path"},
	}
	for _, item := range columns {
		var count int64
		query := fmt.Sprintf("SELECT COUNT(*) FROM %s WHERE char_length(%s) > 640", item.table, item.column)
		if err := db.Raw(query).Scan(&count).Error; err != nil {
			return err
		}
		if count > 0 {
			return fmt.Errorf("%s.%s contains %d values longer than 640 characters", item.table, item.column, count)
		}
		statement := fmt.Sprintf("ALTER TABLE %s ALTER COLUMN %s TYPE varchar(640)", item.table, item.column)
		if err := db.Exec(statement).Error; err != nil {
			return err
		}
	}
	return nil
}

func configurePool(db *gorm.DB, cfg config.DatabaseConfig) (*gorm.DB, error) {
	sqlDB, err := db.DB()
	if err != nil {
		return nil, err
	}
	maxOpenConns := cfg.MaxOpenConns
	maxIdleConns := cfg.MaxIdleConns
	if cfg.Driver == "sqlite" && cfg.DSN == ":memory:" {
		maxOpenConns = 1
		maxIdleConns = 1
	}
	sqlDB.SetMaxOpenConns(maxOpenConns)
	sqlDB.SetMaxIdleConns(maxIdleConns)
	sqlDB.SetConnMaxLifetime(cfg.ConnMaxLifetime)
	sqlDB.SetConnMaxIdleTime(cfg.ConnMaxIdleTime)
	return db, nil
}

func ensureSQLiteDir(dsn string) error {
	if dsn == "" || dsn == ":memory:" || strings.HasPrefix(dsn, "file:") {
		return nil
	}
	dir := filepath.Dir(dsn)
	if dir == "." || dir == "" {
		return nil
	}
	return os.MkdirAll(dir, 0o755)
}

func sqliteConnectionDSN(dsn string) string {
	if dsn == ":memory:" {
		return dsn
	}
	if !strings.HasPrefix(dsn, "file:") {
		dsn = "file:" + filepath.ToSlash(dsn)
	}
	separator := "?"
	if strings.Contains(dsn, "?") {
		separator = "&"
	}
	return dsn + separator + "_pragma=busy_timeout(5000)&_pragma=journal_mode(wal)&_pragma=synchronous(normal)&_pragma=foreign_keys(on)"
}
