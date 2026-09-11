package httpapi

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
)

const maxLevelSyncChanges = 500

var validLevelSources = map[string]bool{
	"built_in": true,
	"user":     true,
	"imported": true,
}

type levelSyncRequest struct {
	SinceRevision int64                  `json:"since_revision"`
	Changes       []levelSyncChangeInput `json:"changes"`
}

type levelSyncChangeInput struct {
	Kind         string          `json:"kind"`
	ID           string          `json:"id"`
	BaseRevision int64           `json:"base_revision"`
	Deleted      bool            `json:"deleted"`
	Name         string          `json:"name"`
	Source       string          `json:"source"`
	Definition   json.RawMessage `json:"definition"`
}

type levelSyncChangeOutput struct {
	Kind       string          `json:"kind"`
	ID         string          `json:"id"`
	Revision   int64           `json:"revision"`
	Deleted    bool            `json:"deleted"`
	Name       string          `json:"name,omitempty"`
	Source     string          `json:"source,omitempty"`
	Definition json.RawMessage `json:"definition,omitempty"`
}

type levelSyncResult struct {
	Revision  int64                   `json:"revision"`
	Applied   int                     `json:"applied"`
	Conflicts int                     `json:"conflicts"`
	Changes   []levelSyncChangeOutput `json:"changes"`
}

// SyncLevels applies optimistic per-record mutations and returns only records
// changed after the device cursor (plus touched conflicts/no-ops). A stale
// base_revision never overwrites newer server data: the server copy is returned
// and becomes the client's new baseline.
func (h *Handler) SyncLevels(c fiber.Ctx) error {
	var req levelSyncRequest
	if err := c.Bind().Body(&req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	if err := validateLevelSyncRequest(req); err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}

	result := levelSyncResult{Changes: []levelSyncChangeOutput{}}
	err := h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		userID := currentUserID(c)
		state, err := lockUserSyncState(tx, userID)
		if err != nil {
			return err
		}
		if req.SinceRevision > state.Revision {
			return fiber.NewError(fiber.StatusConflict, "sync cursor is ahead of the server revision")
		}

		touched := make(map[string]bool, len(req.Changes))
		for _, change := range req.Changes {
			key := levelSyncKey(change.Kind, change.ID)
			touched[key] = true

			var existing model.UserLevel
			err := tx.Where("user_id = ? AND kind = ? AND level_id = ?", userID, change.Kind, change.ID).
				First(&existing).Error
			found := err == nil
			if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
			serverRevision := int64(0)
			if found {
				serverRevision = existing.Revision
			}
			if serverRevision != change.BaseRevision {
				result.Conflicts++
				continue
			}
			// Deleting a record that has never existed is already satisfied and
			// should not manufacture an empty tombstone.
			if change.Deleted && !found {
				continue
			}

			if found && levelChangeMatches(existing, change) {
				continue
			}

			state.Revision++
			if !found {
				existing = model.UserLevel{
					UserID:  userID,
					Kind:    change.Kind,
					LevelID: change.ID,
				}
			}
			if !change.Deleted {
				existing.Name = strings.TrimSpace(change.Name)
				existing.Source = change.Source
				existing.Definition = append(existing.Definition[:0], change.Definition...)
			}
			existing.Deleted = change.Deleted
			existing.Revision = state.Revision
			if found {
				if err := tx.Save(&existing).Error; err != nil {
					return err
				}
			} else if err := tx.Create(&existing).Error; err != nil {
				return err
			}
			result.Applied++
		}

		if result.Applied > 0 {
			if err := tx.Save(&state).Error; err != nil {
				return err
			}
		}
		result.Revision = state.Revision

		var levels []model.UserLevel
		query := tx.Where("user_id = ? AND revision > ?", userID, req.SinceRevision)
		if err := query.Order("revision ASC").Find(&levels).Error; err != nil {
			return err
		}
		byKey := make(map[string]model.UserLevel, len(levels)+len(touched))
		for _, level := range levels {
			byKey[levelSyncKey(level.Kind, level.LevelID)] = level
		}
		for key := range touched {
			if _, ok := byKey[key]; ok {
				continue
			}
			kind, id := splitLevelSyncKey(key)
			var level model.UserLevel
			if err := tx.Where("user_id = ? AND kind = ? AND level_id = ?", userID, kind, id).
				First(&level).Error; err == nil {
				byKey[key] = level
			} else if !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
		}
		levels = levels[:0]
		for _, level := range byKey {
			levels = append(levels, level)
		}
		sort.Slice(levels, func(i, j int) bool { return levels[i].Revision < levels[j].Revision })
		for _, level := range levels {
			result.Changes = append(result.Changes, levelSyncOutput(level))
		}
		return nil
	})
	if err != nil {
		var fiberError *fiber.Error
		if errors.As(err, &fiberError) {
			return sendError(c, fiberError.Code, fiberError.Message)
		}
		return err
	}
	return sendData(c, fiber.StatusOK, result)
}

func validateLevelSyncRequest(req levelSyncRequest) error {
	if req.SinceRevision < 0 {
		return errors.New("since_revision must not be negative")
	}
	if len(req.Changes) > maxLevelSyncChanges {
		return fmt.Errorf("changes must contain at most %d records", maxLevelSyncChanges)
	}
	seen := make(map[string]bool, len(req.Changes))
	for index := range req.Changes {
		change := &req.Changes[index]
		change.Kind = strings.ToLower(strings.TrimSpace(change.Kind))
		change.ID = strings.TrimSpace(change.ID)
		change.Source = strings.ToLower(strings.TrimSpace(change.Source))
		if !course.IsValidMode(change.Kind) {
			return fmt.Errorf("changes[%d].kind must be singles, melodies, or chords", index)
		}
		if change.ID == "" || len(change.ID) > 128 {
			return fmt.Errorf("changes[%d].id is required and must be at most 128 characters", index)
		}
		if change.BaseRevision < 0 {
			return fmt.Errorf("changes[%d].base_revision must not be negative", index)
		}
		key := levelSyncKey(change.Kind, change.ID)
		if seen[key] {
			return fmt.Errorf("changes[%d] duplicates %s level %q", index, change.Kind, change.ID)
		}
		seen[key] = true
		if change.Deleted {
			continue
		}
		change.Name = strings.TrimSpace(change.Name)
		if change.Name == "" || len(change.Name) > 220 {
			return fmt.Errorf("changes[%d].name is required and must be at most 220 characters", index)
		}
		if !validLevelSources[change.Source] {
			return fmt.Errorf("changes[%d].source is invalid", index)
		}
		if len(change.Definition) == 0 || !json.Valid(change.Definition) || bytes.Equal(bytes.TrimSpace(change.Definition), []byte("null")) {
			return fmt.Errorf("changes[%d].definition must be a JSON value", index)
		}
		normalized, err := canonicalJSON(change.Definition)
		if err != nil {
			return fmt.Errorf("changes[%d].definition must be valid JSON", index)
		}
		change.Definition = normalized
		if _, err := course.ValidateDefinition(change.Kind, change.Definition, false); err != nil {
			return fmt.Errorf("changes[%d].definition: %w", index, err)
		}
	}
	return nil
}

func canonicalJSON(raw json.RawMessage) (json.RawMessage, error) {
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.UseNumber()
	var value any
	if err := decoder.Decode(&value); err != nil {
		return nil, err
	}
	encoded, err := json.Marshal(value)
	return json.RawMessage(encoded), err
}

func lockUserSyncState(tx *gorm.DB, userID uint) (model.UserSyncState, error) {
	state := model.UserSyncState{UserID: userID}
	if err := tx.Clauses(clause.OnConflict{DoNothing: true}).Create(&state).Error; err != nil {
		return state, err
	}
	if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
		Where("user_id = ?", userID).First(&state).Error; err != nil {
		return state, err
	}
	return state, nil
}

func levelChangeMatches(existing model.UserLevel, change levelSyncChangeInput) bool {
	if existing.Deleted != change.Deleted {
		return false
	}
	if change.Deleted {
		return true
	}
	return existing.Name == change.Name && existing.Source == change.Source &&
		bytes.Equal(bytes.TrimSpace(existing.Definition), bytes.TrimSpace(change.Definition))
}

func levelSyncOutput(level model.UserLevel) levelSyncChangeOutput {
	result := levelSyncChangeOutput{
		Kind:     level.Kind,
		ID:       level.LevelID,
		Revision: level.Revision,
		Deleted:  level.Deleted,
	}
	if !level.Deleted {
		result.Name = level.Name
		result.Source = level.Source
		result.Definition = append(json.RawMessage(nil), level.Definition...)
	}
	return result
}

func levelSyncKey(kind, id string) string { return kind + "\x00" + id }

func splitLevelSyncKey(key string) (string, string) {
	parts := strings.SplitN(key, "\x00", 2)
	return parts[0], parts[1]
}
