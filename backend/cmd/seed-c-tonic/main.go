package main

import (
	"context"

	"github.com/joho/godotenv"
	"github.com/rs/zerolog/log"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/database"
	"shuuen-backend/internal/seed"
)

func main() {
	_ = godotenv.Load()
	cfg, err := config.Load()
	if err != nil {
		log.Fatal().Err(err).Msg("failed to load config")
	}
	db, err := database.Connect(cfg.Database)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to connect database")
	}
	sqlDB, err := db.DB()
	if err != nil {
		log.Fatal().Err(err).Msg("failed to access database pool")
	}
	defer sqlDB.Close()

	ctx := context.Background()
	if err := database.Migrate(ctx, db); err != nil {
		log.Fatal().Err(err).Msg("failed to run migrations")
	}
	result, err := seed.SeedAllFixedKeys(ctx, db, cfg.Catalog)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to seed fixed-key courses")
	}
	log.Info().
		Int("courses", result.Courses).
		Int("groups", result.Groups).
		Int("levels", result.Levels).
		Msg("fixed-key courses seeded")

	randomResult, err := seed.SeedRandomTonic(ctx, db, cfg.Catalog)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to seed Random tonic course")
	}
	log.Info().
		Uint("course_id", randomResult.CourseID).
		Int("groups", randomResult.Groups).
		Int("levels", randomResult.Levels).
		Msg("Random tonic course seeded")
}
