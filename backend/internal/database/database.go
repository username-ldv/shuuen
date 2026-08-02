package database

import (
	"context"
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
	apply   func(context.Context, *gorm.DB) error
}

var migrations = []migration{
	{version: 1, apply: migrateInitialSchema},
	{version: 2, apply: addCatalogQueryIndexes},
	{version: 3, apply: enforcePrimaryVariantInvariant},
	{version: 4, apply: addUserTokenVersion},
	{version: 5, apply: constrainPostgresCatalogPaths},
	{version: 6, apply: addCourseSchema},
	{version: 7, apply: addVariantLookupIndex},
	{version: 8, apply: addUserLevelSyncSchema},
	{version: 9, apply: addTrainingSessionSyncSchema},
}

func Migrate(ctx context.Context, db *gorm.DB) error {
	if err := db.AutoMigrate(&schemaMigration{}); err != nil {
		return err
	}
	for _, item := range migrations {
		count, err := gorm.G[schemaMigration](db).
			Where("version = ?", item.version).
			Count(ctx, "*")
		if err != nil {
			return err
		}
		if count > 0 {
			continue
		}
		if err := db.Transaction(func(tx *gorm.DB) error {
			if err := item.apply(ctx, tx); err != nil {
				return fmt.Errorf("migration %d: %w", item.version, err)
			}
			return gorm.G[schemaMigration](tx).Create(ctx, &schemaMigration{Version: item.version, AppliedAt: time.Now().UTC()})
		}); err != nil {
			return err
		}
	}
	return nil
}

func migrateInitialSchema(ctx context.Context, db *gorm.DB) error {
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
	if err := migrateVisibilityColumn(ctx, db, "library_groups", "is_active"); err != nil {
		return err
	}
	if err := migrateVisibilityColumn(ctx, db, "melodies", "is_published"); err != nil {
		return err
	}
	if !db.Migrator().HasColumn("file_variants", "file_mod_time") {
		if err := db.Migrator().AddColumn(&model.FileVariant{}, "FileModTime"); err != nil {
			return err
		}
	}
	return nil
}

func migrateVisibilityColumn(ctx context.Context, db *gorm.DB, table string, legacyColumn string) error {
	if !db.Migrator().HasTable(table) || !db.Migrator().HasColumn(table, legacyColumn) {
		return nil
	}
	if !db.Migrator().HasColumn(table, "is_public") {
		return gorm.G[any](db).Exec(ctx, fmt.Sprintf("ALTER TABLE %s RENAME COLUMN %s TO is_public", table, legacyColumn))
	}
	return gorm.G[any](db).Exec(ctx, fmt.Sprintf("UPDATE %s SET is_public = %s", table, legacyColumn))
}

func addCatalogQueryIndexes(ctx context.Context, db *gorm.DB) error {
	statements := []string{
		"CREATE INDEX IF NOT EXISTS idx_library_groups_public_parent_sort ON library_groups (deleted_at, is_public, parent_id, sort_order, name, id)",
		"CREATE INDEX IF NOT EXISTS idx_melodies_public_sort ON melodies (deleted_at, is_public, sort_order, title, id)",
		"CREATE INDEX IF NOT EXISTS idx_melodies_group_public_sort ON melodies (deleted_at, group_id, is_public, sort_order, title, id)",
		"CREATE INDEX IF NOT EXISTS idx_melodies_created_sort ON melodies (deleted_at, created_at, id)",
		"CREATE INDEX IF NOT EXISTS idx_melodies_updated_sort ON melodies (deleted_at, updated_at, id)",
		"CREATE INDEX IF NOT EXISTS idx_file_variants_melody_primary ON file_variants (deleted_at, melody_id, is_primary, created_at, id)",
	}
	for _, statement := range statements {
		if err := gorm.G[any](db).Exec(ctx, statement); err != nil {
			return err
		}
	}
	return nil
}

func addVariantLookupIndex(ctx context.Context, db *gorm.DB) error {
	if err := gorm.G[any](db).Exec(ctx, `
		CREATE INDEX IF NOT EXISTS idx_file_variants_melody_format_order
		ON file_variants (melody_id, format, deleted_at, is_primary DESC, created_at ASC, id ASC)
	`); err != nil {
		return err
	}
	if db.Dialector.Name() == "sqlite" {
		return gorm.G[any](db).Exec(ctx, "ANALYZE file_variants")
	}
	return nil
}

func addUserLevelSyncSchema(ctx context.Context, db *gorm.DB) error {
	// These are new tables. Create them independently so AutoMigrate cannot
	// rebuild an established users table through the belongs-to association.
	for _, table := range []any{&model.UserSyncState{}, &model.UserLevel{}} {
		if db.Migrator().HasTable(table) {
			continue
		}
		if err := db.Migrator().CreateTable(table); err != nil {
			return err
		}
	}
	statements := []string{
		"CREATE UNIQUE INDEX IF NOT EXISTS idx_user_levels_identity ON user_levels (user_id, kind, level_id)",
		"CREATE INDEX IF NOT EXISTS idx_user_levels_changes ON user_levels (user_id, revision)",
		"CREATE INDEX IF NOT EXISTS idx_user_levels_kind ON user_levels (user_id, kind, deleted)",
	}
	for _, statement := range statements {
		if err := gorm.G[any](db).Exec(ctx, statement); err != nil {
			return err
		}
	}
	return nil
}

func addTrainingSessionSyncSchema(ctx context.Context, db *gorm.DB) error {
	if !db.Migrator().HasColumn(&model.UserSyncState{}, "TrainingSessionRevision") {
		if err := db.Migrator().AddColumn(&model.UserSyncState{}, "TrainingSessionRevision"); err != nil {
			return err
		}
	}
	if !db.Migrator().HasTable(&model.UserTrainingSession{}) {
		if err := db.Migrator().CreateTable(&model.UserTrainingSession{}); err != nil {
			return err
		}
	}
	statements := []string{
		"CREATE UNIQUE INDEX IF NOT EXISTS idx_user_training_sessions_identity ON user_training_sessions (user_id, session_id)",
		"CREATE INDEX IF NOT EXISTS idx_user_training_sessions_changes ON user_training_sessions (user_id, revision)",
		"CREATE INDEX IF NOT EXISTS idx_user_training_sessions_history ON user_training_sessions (user_id, deleted, completed_at_epoch_millis DESC, session_id)",
		"CREATE INDEX IF NOT EXISTS idx_user_training_sessions_level_stats ON user_training_sessions (user_id, flow, level_id, deleted, completed_at_epoch_millis DESC)",
		"CREATE INDEX IF NOT EXISTS idx_user_training_sessions_progress ON user_training_sessions (user_id, flow, deleted, finished_early, level_id)",
	}
	for _, statement := range statements {
		if err := gorm.G[any](db).Exec(ctx, statement); err != nil {
			return err
		}
	}
	return nil
}

func enforcePrimaryVariantInvariant(ctx context.Context, db *gorm.DB) error {
	melodyIDs, err := gorm.G[uint](db).
		Table("file_variants").
		Select("melody_id").
		Where("is_primary = ? AND deleted_at IS NULL", true).
		Group("melody_id").
		Having("COUNT(*) > 1").
		Find(ctx)
	if err != nil {
		return err
	}
	for _, melodyID := range melodyIDs {
		keep, err := gorm.G[model.FileVariant](db).
			Where("melody_id = ? AND is_primary = ?", melodyID, true).
			Order("id asc").
			First(ctx)
		if err != nil {
			return err
		}
		if _, err := gorm.G[model.FileVariant](db).
			Where("melody_id = ? AND is_primary = ? AND id <> ?", melodyID, true, keep.ID).
			Update(ctx, "is_primary", false); err != nil {
			return err
		}
	}
	return gorm.G[any](db).Exec(ctx, "CREATE UNIQUE INDEX IF NOT EXISTS idx_file_variants_one_primary ON file_variants (melody_id) WHERE is_primary = true AND deleted_at IS NULL")
}

func addUserTokenVersion(ctx context.Context, db *gorm.DB) error {
	if !db.Migrator().HasColumn("users", "token_version") {
		if err := db.Migrator().AddColumn(&model.User{}, "TokenVersion"); err != nil {
			return err
		}
	}
	_, err := gorm.G[model.User](db).
		Where("token_version = 0 OR token_version IS NULL").
		Update(ctx, "token_version", 1)
	return err
}

func constrainPostgresCatalogPaths(ctx context.Context, db *gorm.DB) error {
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
		query := fmt.Sprintf("SELECT COUNT(*) FROM %s WHERE char_length(%s) > 640", item.table, item.column)
		var count int64
		if err := gorm.G[int64](db).Raw(query).Scan(ctx, &count); err != nil {
			return err
		}
		if count > 0 {
			return fmt.Errorf("%s.%s contains %d values longer than 640 characters", item.table, item.column, count)
		}
		statement := fmt.Sprintf("ALTER TABLE %s ALTER COLUMN %s TYPE varchar(640)", item.table, item.column)
		if err := gorm.G[any](db).Exec(ctx, statement); err != nil {
			return err
		}
	}
	return nil
}

func addCourseSchema(ctx context.Context, db *gorm.DB) error {
	// AutoMigrate follows belongs-to relationships and attempts to migrate the
	// referenced library tables as well. On an existing SQLite catalog, even a
	// harmless model/default difference can make that migration rebuild and drop
	// library_groups while melodies still reference it. Migration 6 introduces
	// only new tables, so create those tables directly and leave the established
	// library schema untouched.
	tables := []any{
		&model.Course{},
		&model.CourseMode{},
		&model.CourseProgressionGroup{},
		&model.CourseLevel{},
	}
	for _, table := range tables {
		if db.Migrator().HasTable(table) {
			continue
		}
		if err := db.Migrator().CreateTable(table); err != nil {
			return err
		}
	}
	statements := []string{
		"CREATE INDEX IF NOT EXISTS idx_courses_visibility_order ON courses (deleted_at, is_public, sort_order, name, id)",
		"CREATE INDEX IF NOT EXISTS idx_course_modes_course_order ON course_modes (deleted_at, course_id, sort_order, mode, id)",
		"CREATE INDEX IF NOT EXISTS idx_course_groups_mode_order ON course_progression_groups (deleted_at, course_mode_id, sort_order, name, id)",
		"CREATE INDEX IF NOT EXISTS idx_course_levels_group_public_order ON course_levels (deleted_at, progression_group_id, is_public, sort_order, name, id)",
	}
	for _, statement := range statements {
		if err := gorm.G[any](db).Exec(ctx, statement); err != nil {
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
