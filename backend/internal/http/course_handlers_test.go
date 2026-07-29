package httpapi

import (
	"encoding/json"
	"fmt"
	"io"
	nethttp "net/http"
	"sync/atomic"
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

func TestBlueprintCourseSummaryUsesBoundedAggregateQueries(t *testing.T) {
	app, db := newTestServer(t)
	root := model.LibraryGroup{Path: "", Name: "Library", Slug: "library", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &root); err != nil {
		t.Fatal(err)
	}
	courseGroup := model.LibraryGroup{ParentID: &root.ID, Path: "large-blueprint", Name: "Large", Slug: "large", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &courseGroup); err != nil {
		t.Fatal(err)
	}
	createMIDIMelody(t, db, courseGroup, "default", "Default", 0)

	var firstChild model.LibraryGroup
	for index := 0; index < 30; index++ {
		child := model.LibraryGroup{
			ParentID: &courseGroup.ID, Path: fmt.Sprintf("large-blueprint/tab-%02d", index),
			Name: fmt.Sprintf("Tab %02d", index), Slug: fmt.Sprintf("tab-%02d", index), IsPublic: true,
		}
		if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &child); err != nil {
			t.Fatal(err)
		}
		createMIDIMelody(t, db, child, "level", "Level", 0)
		if index == 0 {
			firstChild = child
		}
	}
	nested := model.LibraryGroup{
		ParentID: &firstChild.ID, Path: firstChild.Path + "/nested", Name: "Nested", Slug: "nested", IsPublic: true,
	}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &nested); err != nil {
		t.Fatal(err)
	}
	createMIDIMelody(t, db, nested, "nested", "Nested", 0)

	var queryCount atomic.Int64
	callbackName := "test:count_blueprint_summary_queries"
	if err := db.Callback().Query().Before("gorm:query").Register(callbackName, func(*gorm.DB) {
		queryCount.Add(1)
	}); err != nil {
		t.Fatal(err)
	}
	defer func() { _ = db.Callback().Query().Remove(callbackName) }()

	response := testRequest(t, app, nethttp.MethodGet, fmt.Sprintf("/api/v1/courses/%d", courseGroup.ID), "", "")
	var payload struct {
		Data courseResponse `json:"data"`
	}
	decodeResponse(t, response, &payload)
	if queryCount.Load() > 7 {
		t.Fatalf("blueprint detail executed %d SELECT queries, want a count independent of its 30 tabs", queryCount.Load())
	}
	if len(payload.Data.Modes) != 1 || payload.Data.Modes[0].GroupCount != 31 || payload.Data.Modes[0].LevelCount != 32 {
		t.Fatalf("blueprint aggregate summary = %#v", payload.Data)
	}
	groups := payload.Data.Modes[0].Groups
	if len(groups) != 31 || groups[1].LibraryGroupID != firstChild.ID || groups[1].LevelCount != 2 || groups[1].SectionCount != 1 {
		t.Fatalf("blueprint aggregate groups = %#v", groups)
	}
}

func TestBlueprintLevelDetailNavigationMatchesVisibleProgressionGroupOrder(t *testing.T) {
	app, db := newTestServer(t)
	_, courseGroup, tab, nested := createBlueprintTree(t, db)
	first, _ := createMIDIMelody(t, db, tab, "1", "Level 1", 0)
	private, _ := createMIDIMelody(t, db, tab, "2", "Private level", 1)
	unavailable := model.Melody{
		GroupID: tab.ID, SourcePath: "course/tab/unavailable", FileStem: "unavailable",
		Title: "Unavailable level", Slug: "unavailable", SortOrder: 2, IsPublic: true,
	}
	if err := gorm.G[model.Melody](db).Create(t.Context(), &unavailable); err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.FileVariant](db).Create(t.Context(), &model.FileVariant{
		MelodyID: unavailable.ID, Format: "musicxml", OriginalName: "unavailable.musicxml",
		StoredName: "unavailable.musicxml", StoragePath: "course/tab/unavailable.musicxml",
		ChecksumSHA: "unavailable-checksum", IsPrimary: true,
	}); err != nil {
		t.Fatal(err)
	}
	second, _ := createMIDIMelody(t, db, tab, "3", "Level 3", 3)
	last, _ := createMIDIMelody(t, db, nested, "4", "Nested level", 0)
	if _, err := gorm.G[model.Melody](db).Where("id = ?", private.ID).Update(t.Context(), "is_public", false); err != nil {
		t.Fatal(err)
	}
	otherTab := model.LibraryGroup{ParentID: &courseGroup.ID, Path: "course/other", Name: "Other", Slug: "other", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &otherTab); err != nil {
		t.Fatal(err)
	}
	createMIDIMelody(t, db, otherTab, "5", "Other tab level", 0)

	response := testRequest(t, app, nethttp.MethodGet,
		fmt.Sprintf("/api/v1/courses/%d/melodies/levels?group_id=%s", courseGroup.ID, blueprintGroupID(tab.ID)), "", "")
	var page struct {
		Data []courseLevelResponse `json:"data"`
		Meta listMeta              `json:"meta"`
	}
	decodeResponse(t, response, &page)
	wantIDs := []string{blueprintLevelID(first.ID), blueprintLevelID(second.ID), blueprintLevelID(last.ID)}
	if page.Meta.Total != 3 || len(page.Data) != len(wantIDs) {
		t.Fatalf("blueprint level page = %#v", page)
	}
	for index, level := range page.Data {
		if level.ID != wantIDs[index] || level.Navigation != nil {
			t.Fatalf("blueprint list level %d = %#v, want id %q without navigation", index, level, wantIDs[index])
		}
	}

	assertLevelNavigation(t, app, courseGroup.ID, "melodies", wantIDs[0], "", wantIDs[1], 0, 3)
	assertLevelNavigation(t, app, courseGroup.ID, "melodies", wantIDs[1], wantIDs[0], wantIDs[2], 1, 3)
	assertLevelNavigation(t, app, courseGroup.ID, "melodies", wantIDs[2], wantIDs[1], "", 2, 3)
}

func TestManagedLevelDetailNavigationMatchesVisibleProgressionGroupOrder(t *testing.T) {
	app, db := newTestServer(t)
	root := model.LibraryGroup{Path: "", Name: "Library", Slug: "library", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &root); err != nil {
		t.Fatal(err)
	}
	courseGroup := model.LibraryGroup{ParentID: &root.ID, Path: "managed", Name: "Managed", Slug: "managed", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &courseGroup); err != nil {
		t.Fatal(err)
	}
	modeGroup := model.LibraryGroup{ParentID: &courseGroup.ID, Path: "managed/melodies", Name: "Melodies", Slug: "melodies", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &modeGroup); err != nil {
		t.Fatal(err)
	}
	firstTabGroup := model.LibraryGroup{ParentID: &modeGroup.ID, Path: "managed/melodies/first", Name: "First", Slug: "first", IsPublic: true}
	secondTabGroup := model.LibraryGroup{ParentID: &modeGroup.ID, Path: "managed/melodies/second", Name: "Second", Slug: "second", IsPublic: true}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &firstTabGroup); err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.LibraryGroup](db).Create(t.Context(), &secondTabGroup); err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.Course](db).Create(t.Context(), &model.Course{
		ID: courseGroup.ID, Name: "Managed", IsPublic: true, StructureSource: model.CourseStructureManaged,
	}); err != nil {
		t.Fatal(err)
	}
	mode := model.CourseMode{
		CourseID: courseGroup.ID, Mode: coursedomain.ModeMelodies, LibraryGroupID: modeGroup.ID, Name: "Melodies",
	}
	if err := gorm.G[model.CourseMode](db).Create(t.Context(), &mode); err != nil {
		t.Fatal(err)
	}
	firstTab := model.CourseProgressionGroup{
		CourseModeID: mode.ID, PublicID: "group-first", LibraryGroupID: firstTabGroup.ID, Name: "First",
	}
	secondTab := model.CourseProgressionGroup{
		CourseModeID: mode.ID, PublicID: "group-second", LibraryGroupID: secondTabGroup.ID, Name: "Second", SortOrder: 1,
	}
	if err := gorm.G[model.CourseProgressionGroup](db).Create(t.Context(), &firstTab); err != nil {
		t.Fatal(err)
	}
	if err := gorm.G[model.CourseProgressionGroup](db).Create(t.Context(), &secondTab); err != nil {
		t.Fatal(err)
	}
	unavailableMelody, unavailableVariant := createMIDIMelody(t, db, firstTabGroup, "unavailable", "Unavailable", 0)
	if _, err := gorm.G[model.FileVariant](db).Where("id = ?", unavailableVariant.ID).Delete(t.Context()); err != nil {
		t.Fatal(err)
	}
	levels := []model.CourseLevel{
		{ID: "level-1", ProgressionGroupID: firstTab.ID, Name: "Level 1", Source: "imported", Definition: model.JSONDocument(`{}`), SortOrder: 0, IsPublic: true},
		{ID: "level-unavailable", ProgressionGroupID: firstTab.ID, Name: "Unavailable", Source: "imported", Definition: model.JSONDocument(`{}`), SortOrder: 1, IsPublic: true, LibraryMelodyID: &unavailableMelody.ID, LibraryVariantID: &unavailableVariant.ID},
		{ID: "level-private", ProgressionGroupID: firstTab.ID, Name: "Private", Source: "imported", Definition: model.JSONDocument(`{}`), SortOrder: 2, IsPublic: false},
		{ID: "level-2", ProgressionGroupID: firstTab.ID, Name: "Level 2", Source: "imported", Definition: model.JSONDocument(`{}`), SortOrder: 3, IsPublic: true},
		{ID: "level-3", ProgressionGroupID: firstTab.ID, Name: "Level 3", Source: "imported", Definition: model.JSONDocument(`{}`), SortOrder: 4, IsPublic: true},
		{ID: "other-level", ProgressionGroupID: secondTab.ID, Name: "Other", Source: "imported", Definition: model.JSONDocument(`{}`), SortOrder: 0, IsPublic: true},
	}
	if err := gorm.G[model.CourseLevel](db).CreateInBatches(t.Context(), &levels, len(levels)); err != nil {
		t.Fatal(err)
	}

	response := testRequest(t, app, nethttp.MethodGet,
		fmt.Sprintf("/api/v1/courses/%d/melodies/levels?group_id=%s", courseGroup.ID, firstTab.PublicID), "", "")
	var page struct {
		Data []courseLevelResponse `json:"data"`
		Meta listMeta              `json:"meta"`
	}
	decodeResponse(t, response, &page)
	wantIDs := []string{"level-1", "level-2", "level-3"}
	if page.Meta.Total != 3 || len(page.Data) != len(wantIDs) {
		t.Fatalf("managed level page = %#v", page)
	}
	for index, level := range page.Data {
		if level.ID != wantIDs[index] || level.Navigation != nil {
			t.Fatalf("managed list level %d = %#v, want id %q without navigation", index, level, wantIDs[index])
		}
	}

	assertLevelNavigation(t, app, courseGroup.ID, "melodies", wantIDs[0], "", wantIDs[1], 0, 3)
	assertLevelNavigation(t, app, courseGroup.ID, "melodies", wantIDs[1], wantIDs[0], wantIDs[2], 1, 3)
	assertLevelNavigation(t, app, courseGroup.ID, "melodies", wantIDs[2], wantIDs[1], "", 2, 3)
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

func assertLevelNavigation(t *testing.T, app *fiber.App, courseID uint, mode string, levelID string, previousID string, nextID string, position int64, total int64) {
	t.Helper()
	response := testRequest(t, app, nethttp.MethodGet,
		fmt.Sprintf("/api/v1/courses/%d/%s/levels/%s", courseID, mode, levelID), "", "")
	if response.StatusCode != fiber.StatusOK {
		body, _ := io.ReadAll(response.Body)
		_ = response.Body.Close()
		t.Fatalf("level detail %q status = %d: %s", levelID, response.StatusCode, body)
	}
	var payload struct {
		Data courseLevelResponse `json:"data"`
	}
	decodeResponse(t, response, &payload)
	navigation := payload.Data.Navigation
	if navigation == nil {
		t.Fatalf("level detail %q has no navigation", levelID)
	}
	if navigation.Position != position || navigation.Total != total || navigationValue(navigation.PreviousLevelID) != previousID || navigationValue(navigation.NextLevelID) != nextID {
		t.Fatalf("level detail %q navigation = %#v, want previous=%q next=%q position=%d total=%d", levelID, navigation, previousID, nextID, position, total)
	}
}

func navigationValue(value *string) string {
	if value == nil {
		return ""
	}
	return *value
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
