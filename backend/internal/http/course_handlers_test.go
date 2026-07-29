package httpapi

import (
	"encoding/json"
	"fmt"
	"io"
	nethttp "net/http"
	"testing"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	"shuuen-backend/internal/auth"
	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
)

func TestFolderBlueprintAppearsAsCourseAndFlattensNestedSections(t *testing.T) {
	app, db := newTestServer(t)
	root, courseGroup, tab, nested := createBlueprintTree(t, db)
	_ = root
	directMelody, _ := createMIDIMelody(t, db, courseGroup, "direct", "Direct lesson", 2)
	nestedMelody, nestedVariant := createMIDIMelody(t, db, nested, "nested", "Nested lesson", 1)

	response := testRequest(t, app, nethttp.MethodGet, "/api/v1/courses", "", "")
	if response.StatusCode != fiber.StatusOK {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("course list status = %d: %s", response.StatusCode, body)
	}
	var list struct {
		Data []courseResponse `json:"data"`
		Meta listMeta         `json:"meta"`
	}
	decodeResponse(t, response, &list)
	if list.Meta.Total != 1 || len(list.Data) != 1 {
		t.Fatalf("course list = %#v", list)
	}
	if list.Data[0].StructureSource != model.CourseStructureBlueprint || list.Data[0].LevelCount != 2 || list.Data[0].ProgressionGroups != 2 {
		t.Fatalf("blueprint summary = %#v", list.Data[0])
	}

	response = testRequest(t, app, nethttp.MethodGet, fmt.Sprintf("/api/v1/courses/%d", courseGroup.ID), "", "")
	var detail struct {
		Data courseResponse `json:"data"`
	}
	decodeResponse(t, response, &detail)
	if len(detail.Data.Modes) != 1 || len(detail.Data.Modes[0].Groups) != 2 {
		t.Fatalf("blueprint detail = %#v", detail.Data)
	}

	response = testRequest(t, app, nethttp.MethodGet,
		fmt.Sprintf("/api/v1/courses/%d/melodies/levels?group_id=library-%d&limit=20", courseGroup.ID, tab.ID), "", "")
	var page struct {
		Data []courseLevelResponse `json:"data"`
		Meta listMeta              `json:"meta"`
	}
	decodeResponse(t, response, &page)
	if page.Meta.Total != 1 || len(page.Data) != 1 || page.Data[0].ID != blueprintLevelID(nestedMelody.ID) {
		t.Fatalf("nested blueprint page = %#v", page)
	}
	if len(page.Data[0].Sections) != 1 || page.Data[0].Sections[0].LibraryGroupID != nested.ID {
		t.Fatalf("nested section trail = %#v", page.Data[0].Sections)
	}
	if page.Data[0].MIDI == nil || page.Data[0].MIDI.VariantID != nestedVariant.ID {
		t.Fatalf("nested MIDI resource = %#v", page.Data[0].MIDI)
	}

	response = testRequest(t, app, nethttp.MethodGet,
		fmt.Sprintf("/api/v1/courses/%d/melodies/levels?ids=%s,%s", courseGroup.ID, blueprintLevelID(nestedMelody.ID), blueprintLevelID(directMelody.ID)), "", "")
	decodeResponse(t, response, &page)
	if len(page.Data) != 2 || page.Data[0].ID != blueprintLevelID(nestedMelody.ID) || page.Data[1].ID != blueprintLevelID(directMelody.ID) {
		t.Fatalf("id query did not preserve requested order: %#v", page.Data)
	}
}

func TestBlueprintLevelCanBeEditedAndMovedWithoutAggregatePayload(t *testing.T) {
	app, db := newTestServer(t)
	_, courseGroup, tab, nested := createBlueprintTree(t, db)
	directMelody, directVariant := createMIDIMelody(t, db, courseGroup, "direct", "Direct lesson", 2)
	createMIDIMelody(t, db, nested, "nested", "Nested lesson", 1)
	token := createAdminToken(t, app, db)

	definition := coursedomain.DefaultMIDIDefinition(directMelody.ID, directVariant.ID, directVariant.OriginalName)
	body, _ := json.Marshal(map[string]any{
		"name": "Edited lesson", "source": "imported", "definition": json.RawMessage(definition), "is_public": true,
	})
	levelID := blueprintLevelID(directMelody.ID)
	response := testRequest(t, app, nethttp.MethodPut,
		fmt.Sprintf("/api/v1/courses/%d/melodies/levels/%s", courseGroup.ID, levelID), string(body), token)
	if response.StatusCode != fiber.StatusOK {
		responseBody, _ := io.ReadAll(response.Body)
		t.Fatalf("blueprint level update status = %d: %s", response.StatusCode, responseBody)
	}
	_ = response.Body.Close()

	courseRecord, err := gorm.G[model.Course](db).Where("id = ?", courseGroup.ID).First(t.Context())
	if err != nil || courseRecord.StructureSource != model.CourseStructureManaged {
		t.Fatalf("blueprint was not materialized: %#v, %v", courseRecord, err)
	}
	level, err := gorm.G[model.CourseLevel](db).Where("id = ?", levelID).First(t.Context())
	if err != nil || level.Name != "Edited lesson" {
		t.Fatalf("edited materialized level = %#v, %v", level, err)
	}

	moveBody := fmt.Sprintf(`{"group_id":"library-%d","position":0}`, tab.ID)
	response = testRequest(t, app, nethttp.MethodPut,
		fmt.Sprintf("/api/v1/courses/%d/melodies/levels/%s/position", courseGroup.ID, levelID), moveBody, token)
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusNoContent {
		responseBody, _ := io.ReadAll(response.Body)
		t.Fatalf("blueprint level move status = %d: %s", response.StatusCode, responseBody)
	}
	target, err := gorm.G[model.CourseProgressionGroup](db).Where("public_id = ?", blueprintGroupID(tab.ID)).First(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	level, err = gorm.G[model.CourseLevel](db).Where("id = ?", levelID).First(t.Context())
	if err != nil || level.ProgressionGroupID != target.ID || level.SortOrder != 0 {
		t.Fatalf("moved level = %#v, %v", level, err)
	}
	ordered, err := gorm.G[model.CourseLevel](db).Where("progression_group_id = ?", target.ID).Order("sort_order asc").Find(t.Context())
	if err != nil || len(ordered) != 2 || ordered[0].ID != levelID || ordered[1].SortOrder != 1 {
		t.Fatalf("target ordering after move = %#v, %v", ordered, err)
	}
}

func TestManagedCourseGranularCreationAndPublicMIDIValidation(t *testing.T) {
	app, db := newTestServer(t)
	root := model.LibraryGroup{Path: "", Name: "Library", Slug: "library", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &root); err != nil {
		t.Fatal(err)
	}
	melody, variant := createMIDIMelody(t, db, root, "reference", "Reference", 0)
	token := createAdminToken(t, app, db)

	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/courses", `{"name":"Ear training","slug":"ear-training","author":"LdV","is_public":true}`, token)
	if response.StatusCode != fiber.StatusCreated {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("create course status = %d: %s", response.StatusCode, body)
	}
	var created struct {
		Data courseResponse `json:"data"`
	}
	decodeResponse(t, response, &created)

	response = testRequest(t, app, nethttp.MethodPost, fmt.Sprintf("/api/v1/courses/%d/modes", created.Data.ID), `{"mode":"melodies"}`, token)
	if response.StatusCode != fiber.StatusCreated {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("create mode status = %d: %s", response.StatusCode, body)
	}
	_ = response.Body.Close()

	response = testRequest(t, app, nethttp.MethodPost, fmt.Sprintf("/api/v1/courses/%d/melodies/groups", created.Data.ID), `{"name":"C tonic","slug":"c-tonic"}`, token)
	if response.StatusCode != fiber.StatusCreated {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("create group status = %d: %s", response.StatusCode, body)
	}
	var groupPayload struct {
		Data progressionGroupResponse `json:"data"`
	}
	decodeResponse(t, response, &groupPayload)

	definition := coursedomain.DefaultMIDIDefinition(melody.ID, variant.ID, variant.OriginalName)
	levelBody, _ := json.Marshal(map[string]any{
		"group_id": groupPayload.Data.ID, "name": "Level 1", "source": "imported",
		"definition": json.RawMessage(definition), "is_public": true,
	})
	response = testRequest(t, app, nethttp.MethodPost, fmt.Sprintf("/api/v1/courses/%d/melodies/levels", created.Data.ID), string(levelBody), token)
	if response.StatusCode != fiber.StatusCreated {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("create level status = %d: %s", response.StatusCode, body)
	}
	_ = response.Body.Close()

	response = testRequest(t, app, nethttp.MethodGet, fmt.Sprintf("/api/v1/courses/%d/melodies/levels?group_id=%s", created.Data.ID, groupPayload.Data.ID), "", "")
	var page struct {
		Data []courseLevelResponse `json:"data"`
		Meta listMeta              `json:"meta"`
	}
	decodeResponse(t, response, &page)
	if page.Meta.Total != 1 || len(page.Data) != 1 || page.Data[0].MIDI == nil {
		t.Fatalf("managed level page = %#v", page)
	}

	localDefinition := json.RawMessage(`{"config":{"type":"midi","file":{"type":"local","path":"D:/private.mid","file_name":"private.mid"},"use_original_velocities":false},"context":null}`)
	levelBody, _ = json.Marshal(map[string]any{
		"group_id": groupPayload.Data.ID, "name": "Invalid public local", "source": "user",
		"definition": localDefinition, "is_public": true,
	})
	response = testRequest(t, app, nethttp.MethodPost, fmt.Sprintf("/api/v1/courses/%d/melodies/levels", created.Data.ID), string(levelBody), token)
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusBadRequest {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("public local MIDI status = %d, want 400: %s", response.StatusCode, body)
	}
}

func createBlueprintTree(t *testing.T, db *gorm.DB) (model.LibraryGroup, model.LibraryGroup, model.LibraryGroup, model.LibraryGroup) {
	t.Helper()
	root := model.LibraryGroup{Path: "", Name: "Library", Slug: "library", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &root); err != nil {
		t.Fatal(err)
	}
	courseGroup := model.LibraryGroup{ParentID: &root.ID, Path: "course", Name: "Course", Slug: "course", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &courseGroup); err != nil {
		t.Fatal(err)
	}
	tab := model.LibraryGroup{ParentID: &courseGroup.ID, Path: "course/tab", Name: "Tab", Slug: "tab", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &tab); err != nil {
		t.Fatal(err)
	}
	nested := model.LibraryGroup{ParentID: &tab.ID, Path: "course/tab/nested", Name: "Nested", Slug: "nested", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &nested); err != nil {
		t.Fatal(err)
	}
	return root, courseGroup, tab, nested
}

func createMIDIMelody(t *testing.T, db *gorm.DB, group model.LibraryGroup, stem string, title string, order int) (model.Melody, model.FileVariant) {
	t.Helper()
	sourcePath := stem
	if group.Path != "" {
		sourcePath = group.Path + "/" + stem
	}
	melody := model.Melody{GroupID: group.ID, SourcePath: sourcePath, FileStem: stem, Title: title, Slug: stem, SortOrder: order, IsPublic: true}
	if err := gorm.G[model.Melody](db).Create(t.Context(), &melody); err != nil {
		t.Fatal(err)
	}
	variant := model.FileVariant{
		MelodyID: melody.ID, Format: "midi", OriginalName: stem + ".mid", StoredName: stem + ".mid",
		StoragePath: sourcePath + ".mid", ChecksumSHA: fmt.Sprintf("checksum-%d", melody.ID), IsPrimary: true,
	}
	if err := gorm.G[model.FileVariant](db).Create(t.Context(), &variant); err != nil {
		t.Fatal(err)
	}
	return melody, variant
}

func createAdminToken(t *testing.T, app *fiber.App, db *gorm.DB) string {
	t.Helper()
	hash, err := auth.HashPassword("admin-password")
	if err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.User](db).Create(t.Context(), &model.User{Username: "CourseAdmin", UsernameKey: "courseadmin", PasswordHash: hash, Role: "admin"}); err != nil {
		t.Fatal(err)
	}
	return loginTestUser(t, app, "CourseAdmin", "admin-password")
}
