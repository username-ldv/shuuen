package httpapi

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	nethttp "net/http"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/gofiber/fiber/v3"
	"github.com/ncruces/go-sqlite3/gormlite"
	"gorm.io/gorm"

	"shuuen-backend/internal/auth"
	"shuuen-backend/internal/catalog"
	"shuuen-backend/internal/config"
	"shuuen-backend/internal/database"
	"shuuen-backend/internal/model"
	dbquery "shuuen-backend/internal/query"
	"shuuen-backend/internal/storage"
)

func TestRegisteredUserCannotMutateCatalogButAdminCan(t *testing.T) {
	app, db := newTestServer(t)
	userToken := registerTestUser(t, app, "regular_user", "regular-password")
	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/library/rescan", "", userToken)
	if response.StatusCode != fiber.StatusForbidden {
		t.Fatalf("regular user rescan status = %d, want 403", response.StatusCode)
	}
	_ = response.Body.Close()

	hash, err := auth.HashPassword("admin-password")
	if err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.User](db).Create(t.Context(), &model.User{Username: "Admin", UsernameKey: "admin", PasswordHash: hash, Role: "admin"}); err != nil {
		t.Fatal(err)
	}
	adminToken := loginTestUser(t, app, "Admin", "admin-password")
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/library/rescan", "", adminToken)
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusOK {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("admin rescan status = %d, want 200: %s", response.StatusCode, body)
	}
}

func TestPrivateGroupsRequireAdminIncludePrivateScope(t *testing.T) {
	app, db := newTestServer(t)
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &model.LibraryGroup{Path: "private", Name: "Private", Slug: "private", IsPublic: false}); err != nil {
		t.Fatal(err)
	}

	response := testRequest(t, app, nethttp.MethodGet, "/api/v1/library/groups", "", "")
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("public group listing status = %d", response.StatusCode)
	}
	var publicPayload struct {
		Data []model.LibraryGroup `json:"data"`
	}
	decodeResponse(t, response, &publicPayload)
	if len(publicPayload.Data) != 0 {
		t.Fatalf("public listing exposed private groups: %#v", publicPayload.Data)
	}

	hash, err := auth.HashPassword("admin-password")
	if err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.User](db).Create(t.Context(), &model.User{Username: "Admin", UsernameKey: "admin", PasswordHash: hash, Role: "admin"}); err != nil {
		t.Fatal(err)
	}
	adminToken := loginTestUser(t, app, "Admin", "admin-password")
	response = testRequest(t, app, nethttp.MethodGet, "/api/v1/library/groups?include_private=true", "", adminToken)
	var adminPayload struct {
		Data []model.LibraryGroup `json:"data"`
	}
	decodeResponse(t, response, &adminPayload)
	if len(adminPayload.Data) != 1 || adminPayload.Data[0].IsPublic {
		t.Fatalf("admin private listing = %#v", adminPayload.Data)
	}
}

func TestGroupTreePaginatesLargeMelodyCollections(t *testing.T) {
	app, db := newTestServer(t)
	group := model.LibraryGroup{Path: "group", Name: "Group", Slug: "group", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &group); err != nil {
		t.Fatal(err)
	}
	melodies := make([]model.Melody, 250)
	for index := range melodies {
		name := fmt.Sprintf("song-%03d", index)
		melodies[index] = model.Melody{
			GroupID: group.ID, SourcePath: "group/" + name, FileStem: name,
			Title: name, Slug: name, IsPublic: true,
		}
	}
	if err := gorm.G[model.Melody](db).CreateInBatches(t.Context(), &melodies, 100); err != nil {
		t.Fatal(err)
	}

	response := testRequest(t, app, nethttp.MethodGet, fmt.Sprintf("/api/v1/library/groups/%d?limit=50", group.ID), "", "")
	var payload struct {
		Data struct {
			Melodies     []model.Melody `json:"melodies"`
			MelodiesMeta listMeta       `json:"melodies_meta"`
		} `json:"data"`
	}
	decodeResponse(t, response, &payload)
	if len(payload.Data.Melodies) != 50 || payload.Data.MelodiesMeta.Total != 250 {
		t.Fatalf("melody page size/total = %d/%d, want 50/250", len(payload.Data.Melodies), payload.Data.MelodiesMeta.Total)
	}
}

func TestRefreshIssuesAWorkingTokenThatAPasswordChangeRevokes(t *testing.T) {
	app, _ := newTestServer(t)
	token := registerTestUser(t, app, "refresh_user", "old-password")

	refresh := func(token string) (int, string) {
		response := testRequest(t, app, nethttp.MethodPost, "/api/v1/auth/refresh", "", token)
		var payload struct {
			Data struct {
				AccessToken string `json:"access_token"`
			} `json:"data"`
		}
		if response.StatusCode != fiber.StatusOK {
			_ = response.Body.Close()
			return response.StatusCode, ""
		}
		decodeResponse(t, response, &payload)
		return response.StatusCode, payload.Data.AccessToken
	}

	status, refreshed := refresh(token)
	if status != fiber.StatusOK || refreshed == "" {
		t.Fatalf("refresh status = %d, token = %q", status, refreshed)
	}
	response := testRequest(t, app, nethttp.MethodGet, "/api/v1/auth/me", "", refreshed)
	_ = response.Body.Close()
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("refreshed token status = %d, want 200", response.StatusCode)
	}

	body := `{"current_password":"old-password","new_password":"new-password"}`
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/auth/password", body, refreshed)
	_ = response.Body.Close()
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("password change status = %d, want 200", response.StatusCode)
	}
	if status, _ := refresh(refreshed); status != fiber.StatusUnauthorized {
		t.Fatalf("refresh with a revoked token status = %d, want 401", status)
	}
	if status, _ := refresh(""); status != fiber.StatusUnauthorized {
		t.Fatalf("refresh without a token status = %d, want 401", status)
	}
}

func TestPasswordChangeRevokesExistingTokens(t *testing.T) {
	app, _ := newTestServer(t)
	oldToken := registerTestUser(t, app, "password_user", "old-password")
	body := `{"current_password":"old-password","new_password":"new-password"}`
	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/auth/password", body, oldToken)
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("password change status = %d, want 200", response.StatusCode)
	}
	var payload struct {
		Data struct {
			AccessToken string `json:"access_token"`
		} `json:"data"`
	}
	decodeResponse(t, response, &payload)
	if payload.Data.AccessToken == "" {
		t.Fatal("password change did not return a replacement token")
	}

	response = testRequest(t, app, nethttp.MethodGet, "/api/v1/auth/me", "", oldToken)
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusUnauthorized {
		t.Fatalf("old token status = %d, want 401", response.StatusCode)
	}
	response = testRequest(t, app, nethttp.MethodGet, "/api/v1/auth/me", "", payload.Data.AccessToken)
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("replacement token status = %d, want 200", response.StatusCode)
	}
}

func TestPrivateVariantDownloadRequiresAdminIncludePrivateScope(t *testing.T) {
	// Not t.TempDir: Fiber keeps served files open for a few seconds, and on
	// Windows that makes the strict TempDir cleanup fail.
	root, err := os.MkdirTemp("", "shuuen-download-test")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.RemoveAll(root) })
	app, db, _ := newConfiguredTestServer(t, func(cfg *config.Config) { cfg.Catalog.Root = root })
	userToken := registerTestUser(t, app, "regular_user", "regular-password")
	hash, err := auth.HashPassword("admin-password")
	if err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.User](db).Create(t.Context(), &model.User{Username: "Admin", UsernameKey: "admin", PasswordHash: hash, Role: "admin"}); err != nil {
		t.Fatal(err)
	}
	adminToken := loginTestUser(t, app, "Admin", "admin-password")

	variantIDs := map[bool]uint{}
	for _, isPublic := range []bool{true, false} {
		name := "private"
		if isPublic {
			name = "public"
		}
		group := model.LibraryGroup{Path: name, Name: name, Slug: name, IsPublic: isPublic}
		if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &group); err != nil {
			t.Fatal(err)
		}
		melody := model.Melody{GroupID: group.ID, SourcePath: name + "/song", FileStem: "song", Title: "Song", Slug: "song", IsPublic: isPublic}
		if err := gorm.G[model.Melody](db).Create(t.Context(), &melody); err != nil {
			t.Fatal(err)
		}
		if err := os.MkdirAll(filepath.Join(root, name), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(root, name, "song.mid"), []byte("MThd-test"), 0o644); err != nil {
			t.Fatal(err)
		}
		variant := model.FileVariant{
			MelodyID: melody.ID, Format: "midi", OriginalName: "song.mid", StoredName: "song.mid",
			StoragePath: name + "/song.mid", SizeBytes: 9, IsPrimary: true,
		}
		if err := gorm.G[model.FileVariant](db).Omit(dbquery.FileVariant.Melody.Name()).Create(t.Context(), &variant); err != nil {
			t.Fatal(err)
		}
		variantIDs[isPublic] = variant.ID
	}

	for _, tc := range []struct {
		name   string
		public bool
		query  string
		token  string
		want   int
	}{
		{"public anonymous", true, "", "", fiber.StatusOK},
		{"private anonymous", false, "", "", fiber.StatusNotFound},
		{"private regular user", false, "", userToken, fiber.StatusNotFound},
		{"private admin without scope", false, "", adminToken, fiber.StatusNotFound},
		{"private admin with scope", false, "?include_private=true", adminToken, fiber.StatusOK},
	} {
		target := fmt.Sprintf("/api/v1/library/variants/%d/download%s", variantIDs[tc.public], tc.query)
		response := testRequest(t, app, nethttp.MethodGet, target, "", tc.token)
		_ = response.Body.Close()
		if response.StatusCode != tc.want {
			t.Errorf("%s: download status = %d, want %d", tc.name, response.StatusCode, tc.want)
		}
	}
}

func TestAuthRateLimitKeysOnForwardedClientBehindTrustedProxy(t *testing.T) {
	app, _, _ := newConfiguredTestServer(t, func(cfg *config.Config) {
		cfg.HTTP.AuthRateLimit = config.RateLimitConfig{Max: 2, Window: time.Minute}
		// app.Test connects from 0.0.0.0, which stands in for Caddy here.
		cfg.HTTP.TrustedProxies = []string{"0.0.0.0"}
	})
	login := func(clientIP string) int {
		request, err := nethttp.NewRequest(nethttp.MethodPost, "/api/v1/auth/login",
			bytes.NewBufferString(`{"username":"nobody","password":"wrong-password"}`))
		if err != nil {
			t.Fatal(err)
		}
		request.Header.Set("Content-Type", "application/json")
		request.Header.Set("X-Forwarded-For", clientIP)
		response, err := app.Test(request)
		if err != nil {
			t.Fatal(err)
		}
		_ = response.Body.Close()
		return response.StatusCode
	}

	for attempt := range 2 {
		if status := login("203.0.113.1"); status != fiber.StatusUnauthorized {
			t.Fatalf("attempt %d status = %d, want 401", attempt+1, status)
		}
	}
	if status := login("203.0.113.1"); status != fiber.StatusTooManyRequests {
		t.Fatalf("third attempt from the same client status = %d, want 429", status)
	}
	if status := login("203.0.113.2"); status != fiber.StatusUnauthorized {
		t.Fatalf("another client status = %d, want 401: clients share one rate-limit bucket", status)
	}
}

func TestVariantUploadIndexesDirectlyWithoutFullRescan(t *testing.T) {
	app, db := newTestServer(t)
	hash, err := auth.HashPassword("admin-password")
	if err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.User](db).Create(t.Context(), &model.User{Username: "Admin", UsernameKey: "admin", PasswordHash: hash, Role: "admin"}); err != nil {
		t.Fatal(err)
	}
	token := loginTestUser(t, app, "Admin", "admin-password")
	group := model.LibraryGroup{Path: "group", Name: "Group", Slug: "group", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &group); err != nil {
		t.Fatal(err)
	}
	melody := model.Melody{GroupID: group.ID, SourcePath: "group/song", FileStem: "song", Title: "Song", Slug: "song", IsPublic: true}
	if err := gorm.G[model.Melody](db).Create(t.Context(), &melody); err != nil {
		t.Fatal(err)
	}

	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	if err := writer.WriteField("format", "midi"); err != nil {
		t.Fatal(err)
	}
	file, err := writer.CreateFormFile("file", "song.mid")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := file.Write([]byte("MThd-test")); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	request, err := nethttp.NewRequest(nethttp.MethodPost, fmt.Sprintf("/api/v1/library/melodies/%d/variants", melody.ID), &body)
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Content-Type", writer.FormDataContentType())
	request.Header.Set("Authorization", "Bearer "+token)
	response, err := app.Test(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusCreated {
		responseBody, _ := io.ReadAll(response.Body)
		t.Fatalf("upload status = %d, want 201: %s", response.StatusCode, responseBody)
	}
	variant, err := gorm.G[model.FileVariant](db).
		Where(dbquery.FileVariant.MelodyID.Eq(melody.ID)).
		First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	if variant.ScanID != "upload" || !variant.IsPrimary {
		t.Fatalf("unexpected directly indexed variant: %#v", variant)
	}
	groupCount, err := gorm.G[model.LibraryGroup](db).Count(t.Context(), "*")
	if err != nil {
		t.Fatal(err)
	}
	if groupCount != 1 {
		t.Fatalf("upload triggered catalog rescan; group count = %d", groupCount)
	}
}

func newTestServer(t *testing.T) (*fiber.App, *gorm.DB) {
	t.Helper()
	app, db, _ := newTestServerWithStorage(t)
	return app, db
}

// newTestServerWithStorage also returns the catalog root so tests can place
// files where sidecar-writing handlers expect them.
func newTestServerWithStorage(t *testing.T) (*fiber.App, *gorm.DB, string) {
	t.Helper()
	return newConfiguredTestServer(t, nil)
}

// newConfiguredTestServer lets configure adjust the settings, such as rate
// limits, trusted proxies, or the catalog root, before the server is built.
func newConfiguredTestServer(t *testing.T, configure func(*config.Config)) (*fiber.App, *gorm.DB, string) {
	t.Helper()
	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := database.Migrate(t.Context(), db); err != nil {
		t.Fatal(err)
	}
	cfg := config.Config{
		Auth: config.AuthConfig{JWTSecret: "test-secret-that-is-long-enough", JWTIssuer: "test", AccessTokenTTL: time.Hour, RegistrationEnabled: true},
		HTTP: config.HTTPConfig{
			BodyLimitBytes: 2 * 1024 * 1024,
			AuthRateLimit:  config.RateLimitConfig{Max: 100, Window: time.Minute},
			AdminRateLimit: config.RateLimitConfig{Max: 100, Window: time.Minute},
		},
		Catalog: config.CatalogConfig{
			FolderMetadataFile: ".shuuen.json", MelodyMetadataSuffix: ".shuuen.json", MaxUploadBytes: 1024 * 1024,
		},
	}
	if configure != nil {
		configure(&cfg)
	}
	if cfg.Catalog.Root == "" {
		cfg.Catalog.Root = t.TempDir()
	}
	store, err := storage.NewFileStore(cfg.Catalog)
	if err != nil {
		t.Fatal(err)
	}
	scanner, err := catalog.NewScanner(db, cfg.Catalog)
	if err != nil {
		t.Fatal(err)
	}
	app := NewServer(ServerDeps{Config: cfg, DB: db, Auth: auth.NewService(cfg.Auth), Storage: store, Catalog: scanner})
	t.Cleanup(func() { _ = app.Shutdown() })
	return app, db, cfg.Catalog.Root
}

func registerTestUser(t *testing.T, app *fiber.App, username string, password string) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{"username": username, "password": password})
	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/auth/register", string(body), "")
	var payload struct {
		Data struct {
			AccessToken string `json:"access_token"`
		} `json:"data"`
	}
	decodeResponse(t, response, &payload)
	if payload.Data.AccessToken == "" {
		t.Fatal("register response did not contain an access token")
	}
	return payload.Data.AccessToken
}

func loginTestUser(t *testing.T, app *fiber.App, username string, password string) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{"username": username, "password": password})
	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/auth/login", string(body), "")
	var payload struct {
		Data struct {
			AccessToken string `json:"access_token"`
		} `json:"data"`
	}
	decodeResponse(t, response, &payload)
	return payload.Data.AccessToken
}

func testRequest(t *testing.T, app *fiber.App, method string, target string, body string, token string) *nethttp.Response {
	t.Helper()
	request, err := nethttp.NewRequest(method, target, bytes.NewBufferString(body))
	if err != nil {
		t.Fatal(err)
	}
	if body != "" {
		request.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	response, err := app.Test(request, fiber.TestConfig{
		Timeout:       5 * time.Second,
		FailOnTimeout: true,
	})
	if err != nil {
		t.Fatal(err)
	}
	return response
}

func decodeResponse(t *testing.T, response *nethttp.Response, target any) {
	t.Helper()
	defer response.Body.Close()
	if err := json.NewDecoder(response.Body).Decode(target); err != nil {
		t.Fatal(err)
	}
}
