package httpapi

import (
	"encoding/json"
	"io"
	nethttp "net/http"
	"testing"

	"github.com/gofiber/fiber/v3"

	"shuuen-backend/internal/model"
)

func TestTrainingSessionSyncIsIncrementalQueryableAndServerWinsStaleConflicts(t *testing.T) {
	app, db := newTestServer(t)
	token := registerTestUser(t, app, "training_sync_user", "sync-password")
	change := validTrainingSessionChange("session-1")

	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/training-sessions", trainingSessionSyncBody(0, change), token)
	first := decodeTrainingSessionSyncResult(t, response)
	if first.Revision != 1 || first.Applied != 1 || first.Conflicts != 0 || len(first.Changes) != 1 {
		t.Fatalf("first sync = %#v", first)
	}

	var stored model.UserTrainingSession
	if err := db.Where("session_id = ?", change.ID).First(&stored).Error; err != nil {
		t.Fatal(err)
	}
	if stored.Flow != "melodies" || stored.LevelID != "course:7:melodies:level-3" ||
		stored.CorrectNotes != 11 || stored.NotesTotal != 12 || stored.FinishedEarly {
		t.Fatalf("queryable stored session = %#v", stored)
	}
	if string(stored.QuestionResults) != `[{"question_number":1,"note_count":4,"missed_count":5}]` {
		t.Fatalf("stored question results = %s", stored.QuestionResults)
	}

	change.BaseRevision = 1
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/training-sessions", trainingSessionSyncBody(1, change), token)
	repeated := decodeTrainingSessionSyncResult(t, response)
	if repeated.Revision != 1 || repeated.Applied != 0 || len(repeated.Changes) != 1 {
		t.Fatalf("repeated sync = %#v", repeated)
	}

	stale := change
	stale.BaseRevision = 0
	stale.LevelName = "Stale name"
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/training-sessions", trainingSessionSyncBody(1, stale), token)
	conflict := decodeTrainingSessionSyncResult(t, response)
	if conflict.Revision != 1 || conflict.Applied != 0 || conflict.Conflicts != 1 ||
		len(conflict.Changes) != 1 || conflict.Changes[0].LevelName != "Interval run" {
		t.Fatalf("conflict sync = %#v", conflict)
	}

	deleted := trainingSessionSyncChangeInput{ID: change.ID, BaseRevision: 1, Deleted: true}
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/training-sessions", trainingSessionSyncBody(1, deleted), token)
	deletion := decodeTrainingSessionSyncResult(t, response)
	if deletion.Revision != 2 || deletion.Applied != 1 || len(deletion.Changes) != 1 ||
		!deletion.Changes[0].Deleted || len(deletion.Changes[0].QuestionResults) != 0 {
		t.Fatalf("delete sync = %#v", deletion)
	}
}

func TestTrainingSessionSyncRequiresAuthenticationAndIsPerUser(t *testing.T) {
	app, _ := newTestServer(t)
	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/training-sessions", trainingSessionSyncBody(0), "")
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusUnauthorized {
		t.Fatalf("anonymous sync status = %d, want 401", response.StatusCode)
	}

	firstToken := registerTestUser(t, app, "training_first", "sync-password")
	secondToken := registerTestUser(t, app, "training_second", "sync-password")
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/training-sessions", trainingSessionSyncBody(0, validTrainingSessionChange("private-session")), firstToken)
	_ = decodeTrainingSessionSyncResult(t, response)

	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/training-sessions", trainingSessionSyncBody(0), secondToken)
	second := decodeTrainingSessionSyncResult(t, response)
	if second.Revision != 0 || len(second.Changes) != 0 {
		t.Fatalf("second user saw first user's sessions: %#v", second)
	}
}

func TestTrainingSessionSyncStoresMissingQuestionResultsAsAnEmptyArray(t *testing.T) {
	app, _ := newTestServer(t)
	token := registerTestUser(t, app, "training_empty", "sync-password")
	change := validTrainingSessionChange("empty-results")
	change.QuestionResults = nil

	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/training-sessions", trainingSessionSyncBody(0, change), token)
	result := decodeTrainingSessionSyncResult(t, response)

	if len(result.Changes) != 1 || string(result.Changes[0].QuestionResults) != "[]" {
		t.Fatalf("normalized question results = %s", result.Changes[0].QuestionResults)
	}
}

func validTrainingSessionChange(id string) trainingSessionSyncChangeInput {
	avgDelta := int64(250)
	return trainingSessionSyncChangeInput{
		ID:                     id,
		Flow:                   "melodies",
		LevelID:                "course:7:melodies:level-3",
		LevelName:              "Interval run",
		CompletedAtEpochMillis: 1_786_000_000_000,
		QuestionsAnswered:      3,
		NotesTotal:             12,
		CorrectNotes:           11,
		MissedNotes:            5,
		Replays:                2,
		DurationMillis:         45_000,
		AvgDeltaMillis:         &avgDelta,
		BestStreak:             8,
		KeysPracticed:          2,
		QuestionResults: []trainingQuestionResult{
			{QuestionNumber: 1, NoteCount: 4, MissedCount: 5},
		},
	}
}

func trainingSessionSyncBody(since int64, changes ...trainingSessionSyncChangeInput) string {
	encoded, _ := json.Marshal(trainingSessionSyncRequest{SinceRevision: since, Changes: changes})
	return string(encoded)
}

func decodeTrainingSessionSyncResult(t *testing.T, response *nethttp.Response) trainingSessionSyncResult {
	t.Helper()
	if response.StatusCode != fiber.StatusOK {
		defer response.Body.Close()
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("training session sync status = %d, want 200: %s", response.StatusCode, body)
	}
	var payload struct {
		Data trainingSessionSyncResult `json:"data"`
	}
	decodeResponse(t, response, &payload)
	return payload.Data
}
