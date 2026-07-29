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
	result, err := seed.SeedCTonic(ctx, db, cfg.Catalog)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to seed C tonic course")
	}
	log.Info().
		Uint("course_id", result.CourseID).
		Int("groups", result.Groups).
		Int("levels", result.Levels).
		Msg("C tonic course seeded")
}
