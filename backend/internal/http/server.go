package httpapi

import (
	"time"

	"github.com/go-playground/validator/v10"
	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/middleware/compress"
	"github.com/gofiber/fiber/v3/middleware/cors"
	"github.com/gofiber/fiber/v3/middleware/logger"
	"github.com/gofiber/fiber/v3/middleware/recover"
	"github.com/gofiber/fiber/v3/middleware/requestid"
	"gorm.io/gorm"

	"shuuen-backend/internal/auth"
	"shuuen-backend/internal/catalog"
	"shuuen-backend/internal/config"
	"shuuen-backend/internal/storage"
)

type ServerDeps struct {
	Config  config.Config
	DB      *gorm.DB
	Auth    *auth.Service
	Storage *storage.FileStore
	Catalog *catalog.Scanner
}

type Handler struct {
	db       *gorm.DB
	auth     *auth.Service
	storage  *storage.FileStore
	catalog  *catalog.Scanner
	validate *validator.Validate
}

func NewServer(deps ServerDeps) *fiber.App {
	app := fiber.New(fiber.Config{
		AppName:      "shuuen-backend",
		BodyLimit:    deps.Config.HTTP.BodyLimitBytes,
		ReadTimeout:  deps.Config.HTTP.ReadTimeout,
		WriteTimeout: deps.Config.HTTP.WriteTimeout,
		IdleTimeout:  deps.Config.HTTP.IdleTimeout,
		ErrorHandler: errorHandler,
	})

	app.Use(recover.New())
	app.Use(requestid.New())
	app.Use(compress.New())
	app.Use(cors.New(cors.Config{
		AllowOrigins: []string{"*"},
		AllowHeaders: []string{"Origin", "Content-Type", "Accept", "Authorization"},
		AllowMethods: []string{"GET", "POST", "PATCH", "DELETE", "OPTIONS"},
		MaxAge:       int((12 * time.Hour).Seconds()),
	}))
	app.Use(logger.New(logger.Config{
		Format: "[${time}] ${status} ${method} ${path} ${latency} ${ip} ${error}\n",
	}))

	h := &Handler{
		db:       deps.DB,
		auth:     deps.Auth,
		storage:  deps.Storage,
		catalog:  deps.Catalog,
		validate: validator.New(),
	}

	app.Get("/healthz", h.Health)

	api := app.Group("/api/v1")
	api.Get("/health", h.Health)

	authRoutes := api.Group("/auth")
	authRoutes.Post("/register", h.Register)
	authRoutes.Post("/login", h.Login)
	authRoutes.Get("/me", AuthRequired(deps.Auth), h.Me)

	library := api.Group("/library")
	library.Get("/groups", h.ListGroups)
	library.Get("/groups/:id", h.GetGroup)
	library.Get("/path/*", h.GetGroupByVersionedPath)
	library.Get("/tags", h.ListTags)
	library.Get("/tags/:id", h.GetTag)
	library.Get("/melodies", h.ListMelodies)
	library.Get("/melodies/:id", h.GetMelody)
	library.Get("/melodies/:id/variants", h.ListVariants)
	library.Get("/variants/:id", h.GetVariant)
	library.Get("/variants/:id/download", h.DownloadVariant)

	protected := library.Group("", AuthRequired(deps.Auth))
	protected.Post("/rescan", h.RescanCatalog)
	protected.Post("/melodies/:id/variants", h.UploadVariant)
	protected.Delete("/melodies/:id", h.DeleteMelody)
	protected.Patch("/variants/:id", h.UpdateVariant)
	protected.Delete("/variants/:id", h.DeleteVariant)
	protected.Post("/tags", h.CreateTag)
	protected.Patch("/tags/:id", h.UpdateTag)
	protected.Delete("/tags/:id", h.DeleteTag)

	app.Get("/api/*", h.GetGroupByDynamicPath)

	return app
}
