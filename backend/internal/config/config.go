package config

import (
	"errors"
	"fmt"
	"os"
	"slices"
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
	CORSOrigins     []string
	AuthRateLimit   RateLimitConfig
	AdminRateLimit  RateLimitConfig
}

type RateLimitConfig struct {
	Max    int
	Window time.Duration
}

type DatabaseConfig struct {
	Driver          string
	DSN             string
	AutoMigrate     bool
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxLifetime time.Duration
	ConnMaxIdleTime time.Duration
}

type AuthConfig struct {
	JWTSecret              string
	JWTIssuer              string
	AccessTokenTTL         time.Duration
	RegistrationEnabled    bool
	BootstrapAdminUsername string
	BootstrapAdminPassword string
	BootstrapAdminName     string
}

type CatalogConfig struct {
	Root                 string
	FolderMetadataFile   string
	MelodyMetadataSuffix string
	MaxUploadBytes       int64
	ScanOnStartup        bool
}

func Load() (Config, error) {
	loader := envLoader{}
	appEnv := strings.ToLower(getEnv("APP_ENV", "development"))
	productionLike := appEnv != "development" && appEnv != "test"
	driver := strings.ToLower(getEnv("DATABASE_DRIVER", "sqlite"))
	maxOpenConns := 20
	maxIdleConns := 10
	if driver == "sqlite" {
		maxOpenConns = 4
		maxIdleConns = 4
	}
	corsFallback := "*"
	if productionLike {
		corsFallback = ""
	}

	cfg := Config{
		AppEnv: appEnv,
		HTTP: HTTPConfig{
			Host:            getEnv("HTTP_HOST", "0.0.0.0"),
			Port:            getEnv("HTTP_PORT", "9999"),
			ReadTimeout:     loader.duration("HTTP_READ_TIMEOUT", 5*time.Second),
			WriteTimeout:    loader.duration("HTTP_WRITE_TIMEOUT", 10*time.Second),
			IdleTimeout:     loader.duration("HTTP_IDLE_TIMEOUT", 30*time.Second),
			ShutdownTimeout: loader.duration("HTTP_SHUTDOWN_TIMEOUT", 10*time.Second),
			BodyLimitBytes:  loader.integer("HTTP_BODY_LIMIT_BYTES", 64*1024*1024),
			CORSOrigins:     splitCSV(getEnv("CORS_ALLOWED_ORIGINS", corsFallback)),
			AuthRateLimit: RateLimitConfig{
				Max:    loader.integer("AUTH_RATE_LIMIT_MAX", 10),
				Window: loader.duration("AUTH_RATE_LIMIT_WINDOW", time.Minute),
			},
			AdminRateLimit: RateLimitConfig{
				Max:    loader.integer("ADMIN_RATE_LIMIT_MAX", 3),
				Window: loader.duration("ADMIN_RATE_LIMIT_WINDOW", time.Minute),
			},
		},
		Database: DatabaseConfig{
			Driver:          driver,
			DSN:             getEnv("DATABASE_DSN", "data/shuuen.db"),
			AutoMigrate:     loader.boolean("AUTO_MIGRATE", true),
			MaxOpenConns:    loader.integer("DATABASE_MAX_OPEN_CONNS", maxOpenConns),
			MaxIdleConns:    loader.integer("DATABASE_MAX_IDLE_CONNS", maxIdleConns),
			ConnMaxLifetime: loader.duration("DATABASE_CONN_MAX_LIFETIME", 30*time.Minute),
			ConnMaxIdleTime: loader.duration("DATABASE_CONN_MAX_IDLE_TIME", 5*time.Minute),
		},
		Auth: AuthConfig{
			JWTSecret:              getEnv("JWT_SECRET", developmentJWTSecret),
			JWTIssuer:              getEnv("JWT_ISSUER", "shuuen-backend"),
			AccessTokenTTL:         loader.duration("ACCESS_TOKEN_TTL", 24*time.Hour),
			RegistrationEnabled:    loader.boolean("REGISTRATION_ENABLED", !productionLike),
			BootstrapAdminUsername: getEnv("BOOTSTRAP_ADMIN_USERNAME", ""),
			BootstrapAdminPassword: getEnv("BOOTSTRAP_ADMIN_PASSWORD", ""),
			BootstrapAdminName:     getEnv("BOOTSTRAP_ADMIN_DISPLAY_NAME", "Administrator"),
		},
		Catalog: CatalogConfig{
			Root:                 getEnv("DATA_ROOT", "data"),
			FolderMetadataFile:   getEnv("FOLDER_METADATA_FILE", ".shuuen.json"),
			MelodyMetadataSuffix: getEnv("MELODY_METADATA_SUFFIX", ".shuuen.json"),
			MaxUploadBytes:       int64(loader.integer("MAX_UPLOAD_SIZE", 50*1024*1024)),
			ScanOnStartup:        loader.boolean("CATALOG_SCAN_ON_STARTUP", true),
		},
	}

	if loader.err != nil {
		return Config{}, loader.err
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
	if c.HTTP.ReadTimeout <= 0 || c.HTTP.WriteTimeout <= 0 || c.HTTP.IdleTimeout <= 0 || c.HTTP.ShutdownTimeout <= 0 {
		return errors.New("HTTP timeouts must be greater than zero")
	}
	if c.HTTP.BodyLimitBytes <= 0 || int64(c.HTTP.BodyLimitBytes) <= c.Catalog.MaxUploadBytes {
		return errors.New("HTTP_BODY_LIMIT_BYTES must be greater than MAX_UPLOAD_SIZE")
	}
	if c.HTTP.AuthRateLimit.Max <= 0 || c.HTTP.AuthRateLimit.Window <= 0 || c.HTTP.AdminRateLimit.Max <= 0 || c.HTTP.AdminRateLimit.Window <= 0 {
		return errors.New("rate limit maxima and windows must be greater than zero")
	}
	if c.Database.MaxOpenConns <= 0 || c.Database.MaxIdleConns < 0 || c.Database.MaxIdleConns > c.Database.MaxOpenConns {
		return errors.New("database pool sizes are invalid")
	}
	if c.Database.ConnMaxLifetime <= 0 || c.Database.ConnMaxIdleTime <= 0 {
		return errors.New("database connection lifetimes must be greater than zero")
	}
	productionLike := c.AppEnv != "development" && c.AppEnv != "test"
	if productionLike {
		if c.Auth.JWTSecret == "" || c.Auth.JWTSecret == developmentJWTSecret {
			return errors.New("JWT_SECRET must be set to a strong production value")
		}
		if len(c.Auth.JWTSecret) < 32 {
			return errors.New("JWT_SECRET must be at least 32 characters in production")
		}
		if slices.Contains(c.HTTP.CORSOrigins, "*") {
			return errors.New("CORS_ALLOWED_ORIGINS must not contain * outside development or test")
		}
	}
	adminUsernameSet := c.Auth.BootstrapAdminUsername != ""
	adminPasswordSet := c.Auth.BootstrapAdminPassword != ""
	if adminUsernameSet != adminPasswordSet {
		return errors.New("BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD must be set together")
	}
	if adminPasswordSet && (len(c.Auth.BootstrapAdminPassword) < 8 || len(c.Auth.BootstrapAdminPassword) > 72) {
		return errors.New("BOOTSTRAP_ADMIN_PASSWORD must be 8-72 bytes")
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

type envLoader struct {
	err error
}

func (l *envLoader) duration(key string, fallback time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(value)
	if err != nil {
		l.setError(key, err)
		return fallback
	}
	return parsed
}

func (l *envLoader) boolean(key string, fallback bool) bool {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		l.setError(key, err)
		return fallback
	}
	return parsed
}

func (l *envLoader) integer(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		l.setError(key, err)
		return fallback
	}
	return parsed
}

func (l *envLoader) setError(key string, err error) {
	if l.err == nil {
		l.err = fmt.Errorf("invalid %s: %w", key, err)
	}
}

func splitCSV(value string) []string {
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part != "" {
			result = append(result, part)
		}
	}
	return result
}
