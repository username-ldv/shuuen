package httpapi

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	nethttp "net/http"
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
	if err := db.Create(&model.User{Username: "Admin", UsernameKey: "admin", PasswordHash: hash, Role: "admin"}).Error; err != nil {
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
	if err := db.Create(&model.LibraryGroup{Path: "private", Name: "Private", Slug: "private", IsPublic: false}).Error; err != nil {
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
	if err := db.Create(&model.User{Username: "Admin", UsernameKey: "admin", PasswordHash: hash, Role: "admin"}).Error; err != nil {
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
	if err := db.Create(&group).Error; err != nil {
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
	if err := db.CreateInBatches(&melodies, 100).Error; err != nil {
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

func TestVariantUploadIndexesDirectlyWithoutFullRescan(t *testing.T) {
	app, db := newTestServer(t)
	hash, err := auth.HashPassword("admin-password")
	if err != nil {
		t.Fatal(err)
	}
	if err := db.Create(&model.User{Username: "Admin", UsernameKey: "admin", PasswordHash: hash, Role: "admin"}).Error; err != nil {
		t.Fatal(err)
	}
	token := loginTestUser(t, app, "Admin", "admin-password")
	group := model.LibraryGroup{Path: "group", Name: "Group", Slug: "group", IsPublic: true}
	if err := db.Create(&group).Error; err != nil {
		t.Fatal(err)
	}
	melody := model.Melody{GroupID: group.ID, SourcePath: "group/song", FileStem: "song", Title: "Song", Slug: "song", IsPublic: true}
	if err := db.Create(&melody).Error; err != nil {
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
	var variant model.FileVariant
	if err := db.Where("melody_id = ?", melody.ID).First(&variant).Error; err != nil {
		t.Fatal(err)
	}
	if variant.ScanID != "upload" || !variant.IsPrimary {
		t.Fatalf("unexpected directly indexed variant: %#v", variant)
	}
	var groupCount int64
	if err := db.Model(&model.LibraryGroup{}).Count(&groupCount).Error; err != nil {
		t.Fatal(err)
	}
	if groupCount != 1 {
		t.Fatalf("upload triggered catalog rescan; group count = %d", groupCount)
	}
}

func newTestServer(t *testing.T) (*fiber.App, *gorm.DB) {
	t.Helper()
	db, err := gorm.Open(gormlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	if err := database.Migrate(db); err != nil {
		t.Fatal(err)
	}
	catalogConfig := config.CatalogConfig{
		Root: t.TempDir(), FolderMetadataFile: ".shuuen.json", MelodyMetadataSuffix: ".shuuen.json", MaxUploadBytes: 1024 * 1024,
	}
	store, err := storage.NewFileStore(catalogConfig)
	if err != nil {
		t.Fatal(err)
	}
	scanner, err := catalog.NewScanner(db, catalogConfig)
	if err != nil {
		t.Fatal(err)
	}
	authConfig := config.AuthConfig{JWTSecret: "test-secret-that-is-long-enough", JWTIssuer: "test", AccessTokenTTL: time.Hour, RegistrationEnabled: true}
	cfg := config.Config{
		Auth: authConfig,
		HTTP: config.HTTPConfig{
			BodyLimitBytes: 2 * 1024 * 1024,
			AuthRateLimit:  config.RateLimitConfig{Max: 100, Window: time.Minute},
			AdminRateLimit: config.RateLimitConfig{Max: 100, Window: time.Minute},
		},
	}
	app := NewServer(ServerDeps{Config: cfg, DB: db, Auth: auth.NewService(authConfig), Storage: store, Catalog: scanner})
	t.Cleanup(func() { _ = app.Shutdown() })
	return app, db
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
	response, err := app.Test(request)
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
