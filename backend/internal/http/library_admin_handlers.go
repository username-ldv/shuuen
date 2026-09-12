package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"

	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
	dbquery "shuuen-backend/internal/query"
)

// updateMelodyRequest patches the editable, sidecar-backed melody metadata.
// A missing field is left unchanged; `"key": null` clears the label.
type updateMelodyRequest struct {
	Title *string         `json:"title"`
	Key   json.RawMessage `json:"key"`
}

type updateMelodyKeysRequest struct {
	MelodyIDs []uint          `json:"melody_ids"`
	Key       json.RawMessage `json:"key"`
}

type updateLibraryGroupRequest struct {
	Name        *string `json:"name"`
	Description *string `json:"description"`
}

const maxBatchMelodyKeys = 500

// melodyKeyPatch is the decoded form of a `key` field: unchanged, cleared, or set.
type melodyKeyPatch struct {
	present bool
	key     *coursedomain.MelodyKey
}

func parseMelodyKeyPatch(raw json.RawMessage) (melodyKeyPatch, error) {
	if len(raw) == 0 {
		return melodyKeyPatch{}, nil
	}
	if strings.TrimSpace(string(raw)) == "null" {
		return melodyKeyPatch{present: true}, nil
	}
	var key coursedomain.MelodyKey
	if err := json.Unmarshal(raw, &key); err != nil {
		return melodyKeyPatch{}, errors.New("key is invalid")
	}
	normalized, err := coursedomain.ParseMelodyKey(key, "key")
	if err != nil {
		return melodyKeyPatch{}, err
	}
	return melodyKeyPatch{present: true, key: &normalized}, nil
}

func (patch melodyKeyPatch) document() (model.JSONDocument, error) {
	if patch.key == nil {
		return nil, nil
	}
	return model.NewJSONDocument(*patch.key)
}

// UpdateMelody edits a melody's title and labelled key. Both live in the
// `<stem>.shuuen.json` sidecar and in the indexed row; the sidecar is written
// first so a database failure can restore it.
func (h *Handler) UpdateMelody(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}
	var request updateMelodyRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	keyPatch, err := parseMelodyKeyPatch(request.Key)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid melody key", err.Error())
	}
	var title *string
	if request.Title != nil {
		trimmed := strings.TrimSpace(*request.Title)
		if trimmed == "" || runeLen(trimmed) > 220 {
			return sendError(c, fiber.StatusBadRequest, "title must be 1-220 characters")
		}
		title = &trimmed
	}
	if title == nil && !keyPatch.present {
		return sendError(c, fiber.StatusBadRequest, "nothing to update")
	}

	melody, err := gorm.G[model.Melody](h.db).
		Where(dbquery.Melody.ID.Eq(id)).
		First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "melody not found")
	}
	if err := h.applyMelodyPatch(c, &melody, title, keyPatch); err != nil {
		return err
	}
	updated, err := gorm.G[model.Melody](h.db).
		Preload(dbquery.Melody.Tags.Name(), nil).
		Preload(dbquery.Melody.Variants.Name(), nil).
		Where(dbquery.Melody.ID.Eq(melody.ID)).
		First(c.Context())
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, updated)
}

// UpdateMelodyKeys applies one key label (or clears it) to many melodies at
// once. Melodies are processed in request order and the first failure stops the
// batch; the response reports how many were written.
func (h *Handler) UpdateMelodyKeys(c fiber.Ctx) error {
	var request updateMelodyKeysRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	if len(request.MelodyIDs) == 0 || len(request.MelodyIDs) > maxBatchMelodyKeys {
		return sendError(c, fiber.StatusBadRequest, fmt.Sprintf("melody_ids must contain between 1 and %d ids", maxBatchMelodyKeys))
	}
	keyPatch, err := parseMelodyKeyPatch(request.Key)
	if err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid melody key", err.Error())
	}
	if !keyPatch.present {
		return sendError(c, fiber.StatusBadRequest, "key is required (use null to clear)")
	}
	seen := make(map[uint]bool, len(request.MelodyIDs))
	ids := make([]uint, 0, len(request.MelodyIDs))
	for _, id := range request.MelodyIDs {
		if id == 0 || seen[id] {
			return sendError(c, fiber.StatusBadRequest, "melody_ids must be positive and unique")
		}
		seen[id] = true
		ids = append(ids, id)
	}
	melodies, err := gorm.G[model.Melody](h.db).Where("id IN ?", ids).Find(c.Context())
	if err != nil {
		return err
	}
	if len(melodies) != len(ids) {
		return sendError(c, fiber.StatusNotFound, "one or more melodies were not found")
	}
	byID := make(map[uint]model.Melody, len(melodies))
	for _, melody := range melodies {
		byID[melody.ID] = melody
	}
	updated := 0
	for _, id := range ids {
		melody := byID[id]
		if err := h.applyMelodyPatch(c, &melody, nil, keyPatch); err != nil {
			return sendError(c, fiber.StatusInternalServerError, fmt.Sprintf("failed after updating %d melodies", updated), err.Error())
		}
		updated++
	}
	return sendData(c, fiber.StatusOK, fiber.Map{"updated": updated})
}

func (h *Handler) applyMelodyPatch(c fiber.Ctx, melody *model.Melody, title *string, keyPatch melodyKeyPatch) error {
	updates := map[string]any{}
	sidecar := map[string]any{}
	if title != nil {
		updates["title"] = *title
		sidecar["title"] = *title
	}
	if keyPatch.present {
		document, err := keyPatch.document()
		if err != nil {
			return err
		}
		updates["music_key"] = document
		if keyPatch.key != nil {
			sidecar["key"] = *keyPatch.key
		} else {
			sidecar["key"] = nil
		}
	}
	restore, err := h.writeMelodySidecar(*melody, sidecar)
	if err != nil {
		return err
	}
	if err := h.db.WithContext(c.Context()).Model(&model.Melody{}).Where("id = ?", melody.ID).Updates(updates).Error; err != nil {
		_ = restore()
		return err
	}
	if title != nil {
		melody.Title = *title
	}
	if keyPatch.present {
		melody.Key, _ = keyPatch.document()
	}
	return nil
}

// writeMelodySidecar merges patch into the melody's `<stem>.shuuen.json`. A nil
// patch value removes the field from the file. The returned function restores
// the previous file content (or absence).
func (h *Handler) writeMelodySidecar(melody model.Melody, patch map[string]any) (func() error, error) {
	absolute, err := h.storage.AbsolutePath(melody.SourcePath + h.melodyMetadataSuffix)
	if err != nil {
		return nil, err
	}
	payload := map[string]any{}
	previous, readErr := os.ReadFile(absolute)
	existed := readErr == nil
	if existed {
		if err := json.Unmarshal(previous, &payload); err != nil {
			return nil, fmt.Errorf("read existing melody metadata: %w", err)
		}
	} else if !errors.Is(readErr, os.ErrNotExist) {
		return nil, readErr
	}
	for field, value := range patch {
		if value == nil {
			delete(payload, field)
			continue
		}
		payload[field] = value
	}
	delete(payload, "is_published")
	restore := func() error {
		if !existed {
			err := os.Remove(absolute)
			if errors.Is(err, os.ErrNotExist) {
				return nil
			}
			return err
		}
		return os.WriteFile(absolute, previous, 0o644)
	}
	// Clearing the last field leaves nothing worth a sidecar; drop the file so
	// folders stay as tidy as they were before labelling.
	if len(payload) == 0 {
		if existed {
			if err := os.Remove(absolute); err != nil {
				return nil, err
			}
		}
		return restore, nil
	}
	encoded, err := json.MarshalIndent(payload, "", "  ")
	if err != nil {
		return nil, err
	}
	encoded = append(encoded, '\n')
	if err := os.WriteFile(absolute, encoded, 0o644); err != nil {
		return nil, err
	}
	return restore, nil
}

// UpdateLibraryGroup renames a catalog folder's display name and description
// without materializing blueprint courses. Managed course rows that mirror the
// folder name are kept in sync.
func (h *Handler) UpdateLibraryGroup(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}
	var request updateLibraryGroupRequest
	if err := c.Bind().Body(&request); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	if request.Name == nil && request.Description == nil {
		return sendError(c, fiber.StatusBadRequest, "nothing to update")
	}
	group, err := gorm.G[model.LibraryGroup](h.db).Where(dbquery.LibraryGroup.ID.Eq(id)).First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "group not found")
	}
	if group.Path == "" {
		return sendError(c, fiber.StatusBadRequest, "the library root cannot be renamed")
	}
	name, description := group.Name, group.Description
	if request.Name != nil {
		name = strings.TrimSpace(*request.Name)
	}
	if request.Description != nil {
		description = strings.TrimSpace(*request.Description)
	}
	if name == "" || runeLen(name) > 180 || runeLen(description) > 20_000 {
		return sendError(c, fiber.StatusBadRequest, "group metadata is invalid")
	}
	previous := courseGroupMetadata{Name: group.Name, Description: group.Description, SortOrder: group.SortOrder, IsPublic: group.IsPublic}
	if err := h.updateCourseGroupMetadata(group.Path, courseGroupMetadata{
		Name: name, Description: description, SortOrder: group.SortOrder, IsPublic: group.IsPublic,
	}); err != nil {
		return err
	}
	dbUpdated := false
	defer func() {
		if !dbUpdated {
			_ = h.updateCourseGroupMetadata(group.Path, previous)
		}
	}()
	fields := map[string]any{"name": name, "description": description}
	if err := h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&model.LibraryGroup{}).Where("id = ?", group.ID).Updates(fields).Error; err != nil {
			return err
		}
		if err := tx.Model(&model.CourseProgressionGroup{}).Where("library_group_id = ?", group.ID).Updates(fields).Error; err != nil {
			return err
		}
		return tx.Model(&model.Course{}).Where("id = ?", group.ID).Updates(fields).Error
	}); err != nil {
		return err
	}
	dbUpdated = true
	updated, err := gorm.G[model.LibraryGroup](h.db).
		Preload(dbquery.LibraryGroup.Tags.Name(), nil).
		Where(dbquery.LibraryGroup.ID.Eq(group.ID)).
		First(c.Context())
	if err != nil {
		return err
	}
	return sendData(c, fiber.StatusOK, updated)
}

// attachMelodyKeys overlays each MIDI level's catalog key into
// definition.config.key when the stored definition has none, so every client
// sees one source of truth for a melody's key.
func (h *Handler) attachMelodyKeys(c fiber.Ctx, levels []courseLevelResponse) error {
	ids := make([]uint, 0, len(levels))
	for _, level := range levels {
		if level.MIDI != nil {
			ids = append(ids, level.MIDI.MelodyID)
		}
	}
	if len(ids) == 0 {
		return nil
	}
	type keyRow struct {
		ID  uint
		Key model.JSONDocument
	}
	var rows []keyRow
	if err := h.db.WithContext(c.Context()).Table("melodies").
		Select("id, music_key AS key").
		Where("id IN ? AND music_key IS NOT NULL AND deleted_at IS NULL", ids).
		Scan(&rows).Error; err != nil {
		return err
	}
	if len(rows) == 0 {
		return nil
	}
	keys := make(map[uint]model.JSONDocument, len(rows))
	for _, row := range rows {
		keys[row.ID] = row.Key
	}
	for index := range levels {
		level := &levels[index]
		if level.MIDI == nil {
			continue
		}
		key, ok := keys[level.MIDI.MelodyID]
		if !ok || len(key) == 0 {
			continue
		}
		definition, err := overlayMelodyKey(level.Definition, key)
		if err != nil {
			return err
		}
		level.Definition = definition
	}
	return nil
}

func overlayMelodyKey(definition json.RawMessage, key model.JSONDocument) (json.RawMessage, error) {
	var root map[string]json.RawMessage
	if err := json.Unmarshal(definition, &root); err != nil {
		return definition, nil
	}
	rawConfig, ok := root["config"]
	if !ok {
		return definition, nil
	}
	var config map[string]json.RawMessage
	if err := json.Unmarshal(rawConfig, &config); err != nil {
		return definition, nil
	}
	if string(config["type"]) != `"midi"` {
		return definition, nil
	}
	if existing, ok := config["key"]; ok && strings.TrimSpace(string(existing)) != "null" {
		return definition, nil
	}
	config["key"] = json.RawMessage(key)
	encodedConfig, err := json.Marshal(config)
	if err != nil {
		return nil, err
	}
	root["config"] = encodedConfig
	return json.Marshal(root)
}
