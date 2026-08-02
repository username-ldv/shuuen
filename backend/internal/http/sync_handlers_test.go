package httpapi

import (
	"encoding/json"
	"io"
	nethttp "net/http"
	"testing"

	"github.com/gofiber/fiber/v3"
)

func TestLevelSyncIsIncrementalAndServerWinsStaleConflicts(t *testing.T) {
	app, _ := newTestServer(t)
	token := registerTestUser(t, app, "sync_user", "sync-password")

	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/levels", syncBody(0,
		levelSyncChangeInput{
			Kind: "singles", ID: "level-1", Name: "First", Source: "user",
			Definition: validSinglesDefinition(),
		},
	), token)
	first := decodeSyncResult(t, response)
	if first.Revision != 1 || first.Applied != 1 || first.Conflicts != 0 || len(first.Changes) != 1 {
		t.Fatalf("first sync = %#v", first)
	}

	// Repeating the exact record with its current base does not rewrite it or
	// advance the cursor, but still returns the touched record for retry safety.
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/levels", syncBody(1,
		levelSyncChangeInput{
			Kind: "singles", ID: "level-1", BaseRevision: 1, Name: "First", Source: "user",
			Definition: validSinglesDefinition(),
		},
	), token)
	repeated := decodeSyncResult(t, response)
	if repeated.Revision != 1 || repeated.Applied != 0 || len(repeated.Changes) != 1 {
		t.Fatalf("repeated sync = %#v", repeated)
	}

	// A stale device cannot replace revision 1 with a base of 0. It receives
	// the server copy even though its since cursor is already current.
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/levels", syncBody(1,
		levelSyncChangeInput{
			Kind: "singles", ID: "level-1", Name: "Stale edit", Source: "user",
			Definition: validSinglesDefinition(),
		},
	), token)
	conflict := decodeSyncResult(t, response)
	if conflict.Revision != 1 || conflict.Applied != 0 || conflict.Conflicts != 1 ||
		len(conflict.Changes) != 1 || conflict.Changes[0].Name != "First" {
		t.Fatalf("conflict sync = %#v", conflict)
	}

	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/levels", syncBody(1,
		levelSyncChangeInput{Kind: "singles", ID: "level-1", BaseRevision: 1, Deleted: true},
	), token)
	deleted := decodeSyncResult(t, response)
	if deleted.Revision != 2 || deleted.Applied != 1 || len(deleted.Changes) != 1 ||
		!deleted.Changes[0].Deleted || len(deleted.Changes[0].Definition) != 0 {
		t.Fatalf("delete sync = %#v", deleted)
	}
}

func TestLevelSyncRequiresAuthenticationAndIsPerUser(t *testing.T) {
	app, _ := newTestServer(t)
	response := testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/levels", syncBody(0), "")
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusUnauthorized {
		t.Fatalf("anonymous sync status = %d, want 401", response.StatusCode)
	}

	firstToken := registerTestUser(t, app, "sync_first", "sync-password")
	secondToken := registerTestUser(t, app, "sync_second", "sync-password")
	change := levelSyncChangeInput{
		Kind: "singles", ID: "private-level", Name: "Private", Source: "user",
		Definition: validSinglesDefinition(),
	}
	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/levels", syncBody(0, change), firstToken)
	_ = decodeSyncResult(t, response)

	response = testRequest(t, app, nethttp.MethodPost, "/api/v1/sync/levels", syncBody(0), secondToken)
	second := decodeSyncResult(t, response)
	if second.Revision != 0 || len(second.Changes) != 0 {
		t.Fatalf("second user saw first user's levels: %#v", second)
	}
}

func syncBody(since int64, changes ...levelSyncChangeInput) string {
	encoded, _ := json.Marshal(levelSyncRequest{SinceRevision: since, Changes: changes})
	return string(encoded)
}

func validSinglesDefinition() json.RawMessage {
	return json.RawMessage(`{
		"level_config":{
			"type":"absolute",
			"scales":[{"root":"C","scale_type":"Major","pitch_states":[{"pitch":"C","active":true}]}],
			"tune_inconsistency_cents":0
		},
		"context":null,
		"questions_number":10,
		"range":{"from":{"midi_index":60},"to":{"midi_index":72}}
	}`)
}

func decodeSyncResult(t *testing.T, response *nethttp.Response) levelSyncResult {
	t.Helper()
	if response.StatusCode != fiber.StatusOK {
		defer response.Body.Close()
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("sync status = %d, want 200: %s", response.StatusCode, body)
	}
	var payload struct {
		Data levelSyncResult `json:"data"`
	}
	decodeResponse(t, response, &payload)
	return payload.Data
}
