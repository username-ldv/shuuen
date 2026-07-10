package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

const developmentJWTSecret = "dev-secret-change-me"

type Config struct {
	AppEnv   string
	HTTP     HTTPConfig
	Database DatabaseConfig
	Auth     AuthConfig
	Catalog  CatalogConfig
}

type HTTPConfig struct {
	Host            string
	Port            string
	ReadTimeout     time.Duration
	WriteTimeout    time.Duration
	IdleTimeout     time.Duration
	ShutdownTimeout time.Duration
	BodyLimitBytes  int
}

type DatabaseConfig struct {
	Driver      string
	DSN         string
	AutoMigrate bool
}

type AuthConfig struct {
	JWTSecret      string
	JWTIssuer      string
	AccessTokenTTL time.Duration
}

type CatalogConfig struct {
	Root                 string
	FolderMetadataFile   string
	MelodyMetadataSuffix string
	MaxUploadBytes       int64
}

func Load() (Config, error) {
	cfg := Config{
		AppEnv: getEnv("APP_ENV", "development"),
		HTTP: HTTPConfig{
			Host:            getEnv("HTTP_HOST", "0.0.0.0"),
			Port:            getEnv("HTTP_PORT", "9999"),
			ReadTimeout:     getDuration("HTTP_READ_TIMEOUT", 5*time.Second),
			WriteTimeout:    getDuration("HTTP_WRITE_TIMEOUT", 10*time.Second),
			IdleTimeout:     getDuration("HTTP_IDLE_TIMEOUT", 30*time.Second),
			ShutdownTimeout: getDuration("HTTP_SHUTDOWN_TIMEOUT", 10*time.Second),
			BodyLimitBytes:  getInt("HTTP_BODY_LIMIT_BYTES", 64*1024*1024),
		},
		Database: DatabaseConfig{
			Driver:      strings.ToLower(getEnv("DATABASE_DRIVER", "sqlite")),
			DSN:         getEnv("DATABASE_DSN", "data/shuuen.db"),
			AutoMigrate: getBool("AUTO_MIGRATE", true),
		},
		Auth: AuthConfig{
			JWTSecret:      getEnv("JWT_SECRET", developmentJWTSecret),
			JWTIssuer:      getEnv("JWT_ISSUER", "shuuen-backend"),
			AccessTokenTTL: getDuration("ACCESS_TOKEN_TTL", 24*time.Hour),
		},
		Catalog: CatalogConfig{
			Root:                 getEnv("DATA_ROOT", "data"),
			FolderMetadataFile:   getEnv("FOLDER_METADATA_FILE", ".shuuen.json"),
			MelodyMetadataSuffix: getEnv("MELODY_METADATA_SUFFIX", ".shuuen.json"),
			MaxUploadBytes:       int64(getInt("MAX_UPLOAD_SIZE", 50*1024*1024)),
		},
	}

	if err := cfg.validate(); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func (c Config) validate() error {
	if c.Database.Driver != "sqlite" && c.Database.Driver != "postgres" {
		return fmt.Errorf("unsupported DATABASE_DRIVER %q", c.Database.Driver)
	}
	if c.Database.DSN == "" {
		return errors.New("DATABASE_DSN is required")
	}
	if c.Catalog.Root == "" {
		return errors.New("DATA_ROOT is required")
	}
	if c.Catalog.FolderMetadataFile == "" {
		return errors.New("FOLDER_METADATA_FILE is required")
	}
	if c.Catalog.MelodyMetadataSuffix == "" {
		return errors.New("MELODY_METADATA_SUFFIX is required")
	}
	if c.Catalog.MaxUploadBytes <= 0 {
		return errors.New("MAX_UPLOAD_SIZE must be greater than zero")
	}
	if c.Auth.AccessTokenTTL <= 0 {
		return errors.New("ACCESS_TOKEN_TTL must be greater than zero")
	}
	if c.AppEnv == "production" {
		if c.Auth.JWTSecret == "" || c.Auth.JWTSecret == developmentJWTSecret {
			return errors.New("JWT_SECRET must be set to a strong production value")
		}
		if len(c.Auth.JWTSecret) < 32 {
			return errors.New("JWT_SECRET must be at least 32 characters in production")
		}
	}
	if c.HTTP.Port == "" {
		return errors.New("HTTP_PORT is required")
	}
	return nil
}

func (h HTTPConfig) Address() string {
	return h.Host + ":" + h.Port
}

func getEnv(key string, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func getDuration(key string, fallback time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getBool(key string, fallback bool) bool {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}
