package httpapi

import (
	"encoding/json"
	"fmt"
	"io"
	nethttp "net/http"
	"os"
	"path/filepath"
	"testing"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	"shuuen-backend/internal/model"
)

const aFlatMajorKey = `{"tonic":"GSharp","spelling":"flats","degrees":["D5","D1","D2","D3","D4","D6","D7"],"scale_name":"Ionian"}`

func TestAdminLabelsMelodyKeyAndBlueprintLevelsExposeIt(t *testing.T) {
	app, db, root := newTestServerWithStorage(t)
	_, courseGroup, tab, nested := createBlueprintTree(t, db)
	melody, _ := createMIDIMelody(t, db, nested, "song", "Song", 1)
	sidecarDir := filepath.Join(root, "course", "tab", "nested")
	if err := os.MkdirAll(sidecarDir, 0o755); err != nil {
		t.Fatal(err)
	}

	userToken := registerTestUser(t, app, "labeler", "labeler-password")
	response := testRequest(t, app, nethttp.MethodPatch, fmt.Sprintf("/api/v1/library/melodies/%d", melody.ID), `{"key":`+aFlatMajorKey+`}`, userToken)
	if response.StatusCode != fiber.StatusForbidden {
		t.Fatalf("regular user patch status = %d, want 403", response.StatusCode)
	}
	_ = response.Body.Close()

	adminToken := createAdminToken(t, app, db)
	response = testRequest(t, app, nethttp.MethodPatch, fmt.Sprintf("/api/v1/library/melodies/%d", melody.ID), `{"key":`+aFlatMajorKey+`,"title":"  Song in A flat  "}`, adminToken)
	if response.StatusCode != fiber.StatusOK {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("admin patch status = %d: %s", response.StatusCode, body)
	}
	var updated struct {
		Data struct {
			Title string          `json:"title"`
			Key   json.RawMessage `json:"key"`
		} `json:"data"`
	}
	decodeResponse(t, response, &updated)
	if updated.Data.Title != "Song in A flat" {
		t.Fatalf("title = %q", updated.Data.Title)
	}
	var key struct {
		Tonic     string   `json:"tonic"`
		Spelling  string   `json:"spelling"`
		Degrees   []string `json:"degrees"`
		ScaleType string   `json:"scale_type"`
		ScaleName string   `json:"scale_name"`
	}
	if err := json.Unmarshal(updated.Data.Key, &key); err != nil {
		t.Fatalf("key decode: %v (%s)", err, updated.Data.Key)
	}
	if key.Tonic != "GSharp" || key.Spelling != "flats" || key.ScaleType != "Major" || key.ScaleName != "Ionian" {
		t.Fatalf("key = %#v", key)
	}
	if len(key.Degrees) != 7 || key.Degrees[0] != "D1" || key.Degrees[4] != "D5" {
		t.Fatalf("degrees were not canonicalized: %v", key.Degrees)
	}

	sidecar, err := os.ReadFile(filepath.Join(sidecarDir, "song.shuuen.json"))
	if err != nil {
		t.Fatalf("sidecar was not written: %v", err)
	}
	var sidecarPayload map[string]json.RawMessage
	if err := json.Unmarshal(sidecar, &sidecarPayload); err != nil {
		t.Fatal(err)
	}
	if _, ok := sidecarPayload["key"]; !ok || string(sidecarPayload["title"]) != `"Song in A flat"` {
		t.Fatalf("sidecar = %s", sidecar)
	}

	response = testRequest(t, app, nethttp.MethodGet,
		fmt.Sprintf("/api/v1/courses/%d/melodies/levels?group_id=library-%d", courseGroup.ID, tab.ID), "", "")
	var page struct {
		Data []courseLevelResponse `json:"data"`
	}
	decodeResponse(t, response, &page)
	if len(page.Data) != 1 || page.Data[0].Name != "Song in A flat" {
		t.Fatalf("level page = %#v", page.Data)
	}
	var definition struct {
		Config struct {
			Type string `json:"type"`
			Key  *struct {
				Tonic     string `json:"tonic"`
				ScaleType string `json:"scale_type"`
			} `json:"key"`
		} `json:"config"`
	}
	if err := json.Unmarshal(page.Data[0].Definition, &definition); err != nil {
		t.Fatal(err)
	}
	if definition.Config.Type != "midi" || definition.Config.Key == nil || definition.Config.Key.Tonic != "GSharp" || definition.Config.Key.ScaleType != "Major" {
		t.Fatalf("level definition did not carry the key: %s", page.Data[0].Definition)
	}

	// Clearing the label removes it from the row, the sidecar, and the level.
	response = testRequest(t, app, nethttp.MethodPatch, fmt.Sprintf("/api/v1/library/melodies/%d", melody.ID), `{"key":null}`, adminToken)
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("clear status = %d", response.StatusCode)
	}
	decodeResponse(t, response, &updated)
	if string(updated.Data.Key) != "null" {
		t.Fatalf("cleared key = %s", updated.Data.Key)
	}
	// Clearing the label leaves only the title, so the sidecar stays with just that.
	sidecar, err = os.ReadFile(filepath.Join(sidecarDir, "song.shuuen.json"))
	if err != nil {
		t.Fatalf("sidecar should keep the title: %v", err)
	}
	sidecarPayload = map[string]json.RawMessage{}
	if err := json.Unmarshal(sidecar, &sidecarPayload); err != nil {
		t.Fatal(err)
	}
	if _, ok := sidecarPayload["key"]; ok || len(sidecarPayload) != 1 {
		t.Fatalf("sidecar after clear = %s", sidecar)
	}
}

func TestClearingTheOnlySidecarFieldRemovesTheFile(t *testing.T) {
	app, db, root := newTestServerWithStorage(t)
	_, _, tab, _ := createBlueprintTree(t, db)
	melody, _ := createMIDIMelody(t, db, tab, "solo", "Solo", 1)
	tabDir := filepath.Join(root, "course", "tab")
	if err := os.MkdirAll(tabDir, 0o755); err != nil {
		t.Fatal(err)
	}
	adminToken := createAdminToken(t, app, db)
	sidecarPath := filepath.Join(tabDir, "solo.shuuen.json")

	response := testRequest(t, app, nethttp.MethodPatch, fmt.Sprintf("/api/v1/library/melodies/%d", melody.ID), `{"key":`+aFlatMajorKey+`}`, adminToken)
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("label status = %d", response.StatusCode)
	}
	_ = response.Body.Close()
	if _, err := os.Stat(sidecarPath); err != nil {
		t.Fatalf("sidecar was not written: %v", err)
	}
	response = testRequest(t, app, nethttp.MethodPatch, fmt.Sprintf("/api/v1/library/melodies/%d", melody.ID), `{"key":null}`, adminToken)
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("clear status = %d", response.StatusCode)
	}
	_ = response.Body.Close()
	if _, err := os.Stat(sidecarPath); !os.IsNotExist(err) {
		t.Fatalf("empty sidecar should be removed, stat err = %v", err)
	}
}

func TestAdminBatchLabelsMelodyKeys(t *testing.T) {
	app, db, root := newTestServerWithStorage(t)
	_, _, tab, _ := createBlueprintTree(t, db)
	first, _ := createMIDIMelody(t, db, tab, "one", "One", 1)
	second, _ := createMIDIMelody(t, db, tab, "two", "Two", 2)
	if err := os.MkdirAll(filepath.Join(root, "course", "tab"), 0o755); err != nil {
		t.Fatal(err)
	}
	adminToken := createAdminToken(t, app, db)

	response := testRequest(t, app, nethttp.MethodPut, "/api/v1/library/melodies/keys",
		fmt.Sprintf(`{"melody_ids":[%d,%d],"key":{"tonic":"A","spelling":"sharps","degrees":["D1","D2","DF3","D4","D5","DF6","D7"],"scale_name":"Harmonic minor"}}`, first.ID, second.ID), adminToken)
	if response.StatusCode != fiber.StatusOK {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("batch status = %d: %s", response.StatusCode, body)
	}
	var result struct {
		Data struct {
			Updated int `json:"updated"`
		} `json:"data"`
	}
	decodeResponse(t, response, &result)
	if result.Data.Updated != 2 {
		t.Fatalf("updated = %d", result.Data.Updated)
	}
	melodies, err := gorm.G[model.Melody](db).Where("id IN ?", []uint{first.ID, second.ID}).Find(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	for _, melody := range melodies {
		var key struct {
			ScaleType string `json:"scale_type"`
		}
		if err := json.Unmarshal(melody.Key, &key); err != nil || key.ScaleType != "Custom" {
			t.Fatalf("melody %d key = %s (%v)", melody.ID, melody.Key, err)
		}
	}

	response = testRequest(t, app, nethttp.MethodPut, "/api/v1/library/melodies/keys",
		fmt.Sprintf(`{"melody_ids":[%d,999999],"key":null}`, first.ID), adminToken)
	if response.StatusCode != fiber.StatusNotFound {
		t.Fatalf("unknown id status = %d, want 404", response.StatusCode)
	}
	_ = response.Body.Close()
}

func TestAdminRenamesLibraryGroupWithoutMaterializingBlueprint(t *testing.T) {
	app, db, root := newTestServerWithStorage(t)
	_, courseGroup, tab, _ := createBlueprintTree(t, db)
	createMIDIMelody(t, db, tab, "song", "Song", 1)
	tabDir := filepath.Join(root, "course", "tab")
	if err := os.MkdirAll(tabDir, 0o755); err != nil {
		t.Fatal(err)
	}
	adminToken := createAdminToken(t, app, db)

	response := testRequest(t, app, nethttp.MethodPatch, fmt.Sprintf("/api/v1/library/groups/%d", tab.ID), `{"name":"Lesson one"}`, adminToken)
	if response.StatusCode != fiber.StatusOK {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("rename status = %d: %s", response.StatusCode, body)
	}
	var updated struct {
		Data model.LibraryGroup `json:"data"`
	}
	decodeResponse(t, response, &updated)
	if updated.Data.Name != "Lesson one" || updated.Data.Description != "" {
		t.Fatalf("group = %#v", updated.Data)
	}
	metadata, err := os.ReadFile(filepath.Join(tabDir, ".shuuen.json"))
	if err != nil {
		t.Fatalf("folder metadata was not written: %v", err)
	}
	var payload map[string]any
	if err := json.Unmarshal(metadata, &payload); err != nil {
		t.Fatal(err)
	}
	if payload["name"] != "Lesson one" || payload["is_public"] != true {
		t.Fatalf("folder metadata = %s", metadata)
	}

	response = testRequest(t, app, nethttp.MethodGet, fmt.Sprintf("/api/v1/courses/%d", courseGroup.ID), "", "")
	var detail struct {
		Data courseResponse `json:"data"`
	}
	decodeResponse(t, response, &detail)
	if detail.Data.StructureSource != model.CourseStructureBlueprint {
		t.Fatalf("course was materialized: %#v", detail.Data)
	}
	if len(detail.Data.Modes) != 1 || len(detail.Data.Modes[0].Groups) != 1 || detail.Data.Modes[0].Groups[0].Name != "Lesson one" {
		t.Fatalf("course groups = %#v", detail.Data.Modes)
	}

	libraryRoot, err := gorm.G[model.LibraryGroup](db).Where("path = ?", "").First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	response = testRequest(t, app, nethttp.MethodPatch, fmt.Sprintf("/api/v1/library/groups/%d", libraryRoot.ID), `{"name":"Nope"}`, adminToken)
	if response.StatusCode != fiber.StatusBadRequest {
		t.Fatalf("root rename status = %d, want 400", response.StatusCode)
	}
	_ = response.Body.Close()
}
