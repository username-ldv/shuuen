package main

import (
	"context"
	"errors"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/joho/godotenv"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"

	"shuuen-backend/internal/auth"
	"shuuen-backend/internal/catalog"
	"shuuen-backend/internal/config"
	"shuuen-backend/internal/database"
	httpapi "shuuen-backend/internal/http"
	"shuuen-backend/internal/storage"
)

func main() {
	_ = godotenv.Load()
	ctx := context.Background()

	cfg, err := config.Load()
	if err != nil {
		log.Fatal().Err(err).Msg("failed to load config")
	}

	configureLogger(cfg.AppEnv)

	db, err := database.Connect(cfg.Database)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to connect database")
	}
	sqlDB, err := db.DB()
	if err != nil {
		log.Fatal().Err(err).Msg("failed to access database pool")
	}
	defer sqlDB.Close()

	if cfg.Database.AutoMigrate {
		if err := database.Migrate(ctx, db); err != nil {
			log.Fatal().Err(err).Msg("failed to run migrations")
		}
	}

	if configured, err := auth.EnsureBootstrapAdmin(ctx, db, cfg.Auth); err != nil {
		log.Fatal().Err(err).Msg("failed to bootstrap administrator")
	} else if configured {
		log.Info().Str("username", cfg.Auth.BootstrapAdminUsername).Msg("bootstrap administrator is available")
	}

	fileStore, err := storage.NewFileStore(cfg.Catalog)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to initialize file storage")
	}
	if err := catalog.RecoverPendingDeletes(ctx, db, fileStore.Root()); err != nil {
		log.Fatal().Err(err).Msg("failed to recover pending file deletions")
	}

	catalogScanner, err := catalog.NewScanner(db, cfg.Catalog)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to initialize catalog scanner")
	}
	if cfg.Catalog.ScanOnStartup {
		scanResult, err := catalogScanner.Scan(ctx)
		if err != nil {
			log.Fatal().Err(err).Msg("failed to scan catalog data")
		}
		log.Info().
			Str("scan_id", scanResult.ScanID).
			Int("groups", scanResult.GroupsIndexed).
			Int("melodies", scanResult.MelodiesFound).
			Int("variants", scanResult.VariantsFound).
			Msg("catalog scan complete")
	} else {
		log.Info().Msg("startup catalog scan disabled")
	}

	authService := auth.NewService(cfg.Auth)
	app := httpapi.NewServer(httpapi.ServerDeps{
		Config:  cfg,
		DB:      db,
		Auth:    authService,
		Storage: fileStore,
		Catalog: catalogScanner,
	})

	errs := make(chan error, 1)
	go func() {
		addr := cfg.HTTP.Address()
		log.Info().Str("address", addr).Msg("starting api")
		errs <- app.Listen(addr)
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)

	select {
	case err := <-errs:
		if err != nil {
			log.Fatal().Err(err).Msg("api stopped unexpectedly")
		}
	case sig := <-stop:
		log.Info().Str("signal", sig.String()).Msg("shutdown requested")
		ctx, cancel := context.WithTimeout(context.Background(), cfg.HTTP.ShutdownTimeout)
		defer cancel()

		done := make(chan error, 1)
		go func() {
			done <- app.Shutdown()
		}()

		select {
		case err := <-done:
			if err != nil && !errors.Is(err, context.Canceled) {
				log.Error().Err(err).Msg("graceful shutdown failed")
			}
		case <-ctx.Done():
			log.Error().Err(ctx.Err()).Msg("graceful shutdown timed out")
		}
	}
}

func configureLogger(env string) {
	zerolog.TimeFieldFormat = time.RFC3339
	if env == "development" {
		log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr, TimeFormat: time.RFC3339})
	}
}
