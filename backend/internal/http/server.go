package httpapi

import (
	"strings"
	"time"

	"github.com/go-playground/validator/v10"
	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/middleware/compress"
	"github.com/gofiber/fiber/v3/middleware/cors"
	"github.com/gofiber/fiber/v3/middleware/limiter"
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
	db                  *gorm.DB
	auth                *auth.Service
	storage             *storage.FileStore
	catalog             *catalog.Scanner
	validate            *validator.Validate
	registrationEnabled bool
	folderMetadataFile  string
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
	app.Use(compress.New(compress.Config{
		Next: func(c fiber.Ctx) bool {
			return strings.HasSuffix(c.Path(), "/download")
		},
	}))
	if len(deps.Config.HTTP.CORSOrigins) > 0 {
		app.Use(cors.New(cors.Config{
			AllowOrigins: deps.Config.HTTP.CORSOrigins,
			AllowHeaders: []string{"Origin", "Content-Type", "Accept", "Authorization"},
			AllowMethods: []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
			MaxAge:       int((12 * time.Hour).Seconds()),
		}))
	}
	app.Use(logger.New(logger.Config{
		Format: "[${time}] ${status} ${method} ${path} ${latency} ${ip} request_id=${locals:requestid} ${error}\n",
	}))

	h := &Handler{
		db:                  deps.DB,
		auth:                deps.Auth,
		storage:             deps.Storage,
		catalog:             deps.Catalog,
		validate:            validator.New(),
		registrationEnabled: deps.Config.Auth.RegistrationEnabled,
		folderMetadataFile:  deps.Config.Catalog.FolderMetadataFile,
	}
	if h.folderMetadataFile == "" {
		h.folderMetadataFile = ".shuuen.json"
	}

	app.Get("/healthz", h.Health)

	api := app.Group("/api/v1")
	api.Get("/health", h.Health)

	authRoutes := api.Group("/auth")
	authLimiter := limiter.New(limiter.Config{
		Max:        deps.Config.HTTP.AuthRateLimit.Max,
		Expiration: deps.Config.HTTP.AuthRateLimit.Window,
		LimitReached: func(c fiber.Ctx) error {
			return sendError(c, fiber.StatusTooManyRequests, "too many authentication attempts")
		},
	})
	authRoutes.Post("/register", authLimiter, h.Register)
	authRoutes.Post("/login", authLimiter, h.Login)
	authRoutes.Get("/me", AuthRequired(deps.Auth, deps.DB), h.Me)
	authRoutes.Post("/password", authLimiter, AuthRequired(deps.Auth, deps.DB), h.ChangePassword)

	syncRoutes := api.Group("/sync", AuthRequired(deps.Auth, deps.DB))
	syncRoutes.Post("/levels", h.SyncLevels)
	syncRoutes.Post("/training-sessions", h.SyncTrainingSessions)

	library := api.Group("/library", OptionalAuth(deps.Auth, deps.DB), VisibilityScope)
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

	protected := library.Group("", AuthenticatedRequired, AdminRequired)
	adminLimiter := limiter.New(limiter.Config{
		Max:        deps.Config.HTTP.AdminRateLimit.Max,
		Expiration: deps.Config.HTTP.AdminRateLimit.Window,
		LimitReached: func(c fiber.Ctx) error {
			return sendError(c, fiber.StatusTooManyRequests, "too many administrative requests")
		},
	})
	protected.Post("/rescan", adminLimiter, h.RescanCatalog)
	protected.Post("/melodies/:id/variants", h.UploadVariant)
	protected.Delete("/melodies/:id", h.DeleteMelody)
	protected.Patch("/variants/:id", h.UpdateVariant)
	protected.Delete("/variants/:id", h.DeleteVariant)
	protected.Post("/tags", h.CreateTag)
	protected.Patch("/tags/:id", h.UpdateTag)
	protected.Delete("/tags/:id", h.DeleteTag)

	courses := api.Group("/courses", OptionalAuth(deps.Auth, deps.DB), VisibilityScope)
	courses.Get("", h.ListCourses)
	courses.Get("/:course_id", h.GetCourse)
	courses.Get("/:course_id/:mode", h.GetCourseMode)
	courses.Get("/:course_id/:mode/levels", h.ListCourseLevels)
	courses.Post("/:course_id/:mode/levels/query", h.QueryCourseLevels)
	courses.Get("/:course_id/:mode/levels/:level_id", h.GetCourseLevel)

	courseAdmin := courses.Group("", AuthenticatedRequired, AdminRequired)
	courseAdmin.Post("", h.CreateCourse)
	courseAdmin.Put("/:course_id", h.UpdateCourse)
	courseAdmin.Post("/:course_id/modes", h.CreateCourseMode)
	courseAdmin.Put("/:course_id/:mode", h.UpdateCourseMode)
	courseAdmin.Put("/:course_id/:mode/position", h.PositionCourseMode)
	courseAdmin.Post("/:course_id/:mode/groups", h.CreateProgressionGroup)
	courseAdmin.Put("/:course_id/:mode/groups/:group_id", h.UpdateProgressionGroup)
	courseAdmin.Put("/:course_id/:mode/groups/:group_id/position", h.PositionProgressionGroup)
	courseAdmin.Post("/:course_id/:mode/levels", h.CreateCourseLevel)
	courseAdmin.Put("/:course_id/:mode/levels/:level_id", h.UpdateCourseLevel)
	courseAdmin.Put("/:course_id/:mode/levels/:level_id/position", h.PositionCourseLevel)
	courseAdmin.Delete("/:course_id/:mode/levels/:level_id", h.DeleteCourseLevel)

	app.Get("/api/*", OptionalAuth(deps.Auth, deps.DB), VisibilityScope, h.GetGroupByDynamicPath)

	return app
}
