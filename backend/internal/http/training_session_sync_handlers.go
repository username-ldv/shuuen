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

	"shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
)

const (
	maxTrainingSessionSyncChanges = 500
	maxQuestionResultsPerSession  = 10_000
)

type trainingSessionSyncRequest struct {
	SinceRevision int64                            `json:"since_revision"`
	Changes       []trainingSessionSyncChangeInput `json:"changes"`
}

type trainingQuestionResult struct {
	QuestionNumber int `json:"question_number"`
	NoteCount      int `json:"note_count"`
	MissedCount    int `json:"missed_count"`
}

type trainingSessionSyncChangeInput struct {
	ID                     string                   `json:"id"`
	BaseRevision           int64                    `json:"base_revision"`
	Deleted                bool                     `json:"deleted"`
	Flow                   string                   `json:"flow"`
	LevelID                string                   `json:"level_id"`
	LevelName              string                   `json:"level_name"`
	CompletedAtEpochMillis int64                    `json:"completed_at_epoch_millis"`
	FinishedEarly          bool                     `json:"finished_early"`
	QuestionsAnswered      int                      `json:"questions_answered"`
	NotesTotal             int                      `json:"notes_total"`
	CorrectNotes           int                      `json:"correct_notes"`
	MissedNotes            int                      `json:"missed_notes"`
	Replays                int                      `json:"replays"`
	DurationMillis         int64                    `json:"duration_millis"`
	AvgAnswerMillis        *int64                   `json:"avg_answer_millis"`
	AvgDeltaMillis         *int64                   `json:"avg_delta_millis"`
	BestStreak             int                      `json:"best_streak"`
	KeysPracticed          int                      `json:"keys_practiced"`
	QuestionResults        []trainingQuestionResult `json:"question_results"`
}

type trainingSessionSyncChangeOutput struct {
	ID                     string          `json:"id"`
	Revision               int64           `json:"revision"`
	Deleted                bool            `json:"deleted"`
	Flow                   string          `json:"flow,omitempty"`
	LevelID                string          `json:"level_id,omitempty"`
	LevelName              string          `json:"level_name,omitempty"`
	CompletedAtEpochMillis int64           `json:"completed_at_epoch_millis,omitempty"`
	FinishedEarly          bool            `json:"finished_early,omitempty"`
	QuestionsAnswered      int             `json:"questions_answered,omitempty"`
	NotesTotal             int             `json:"notes_total,omitempty"`
	CorrectNotes           int             `json:"correct_notes,omitempty"`
	MissedNotes            int             `json:"missed_notes,omitempty"`
	Replays                int             `json:"replays,omitempty"`
	DurationMillis         int64           `json:"duration_millis,omitempty"`
	AvgAnswerMillis        *int64          `json:"avg_answer_millis,omitempty"`
	AvgDeltaMillis         *int64          `json:"avg_delta_millis,omitempty"`
	BestStreak             int             `json:"best_streak,omitempty"`
	KeysPracticed          int             `json:"keys_practiced,omitempty"`
	QuestionResults        json.RawMessage `json:"question_results,omitempty"`
}

type trainingSessionSyncResult struct {
	Revision  int64                             `json:"revision"`
	Applied   int                               `json:"applied"`
	Conflicts int                               `json:"conflicts"`
	Changes   []trainingSessionSyncChangeOutput `json:"changes"`
}

// SyncTrainingSessions applies optimistic per-session mutations and returns
// unseen session revisions. The complete records are the source for the app's
// accuracy, progress, and history views; those derived values are not synced as
// separate mutable copies.
func (h *Handler) SyncTrainingSessions(c fiber.Ctx) error {
	var req trainingSessionSyncRequest
	if err := c.Bind().Body(&req); err != nil {
		return sendError(c, fiber.StatusBadRequest, "invalid request body")
	}
	if err := validateTrainingSessionSyncRequest(req); err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	}

	result := trainingSessionSyncResult{Changes: []trainingSessionSyncChangeOutput{}}
	err := h.db.WithContext(c.Context()).Transaction(func(tx *gorm.DB) error {
		userID := currentUserID(c)
		state, err := lockUserSyncState(tx, userID)
		if err != nil {
			return err
		}
		if req.SinceRevision > state.TrainingSessionRevision {
			return fiber.NewError(fiber.StatusConflict, "sync cursor is ahead of the server revision")
		}

		touched := make(map[string]bool, len(req.Changes))
		for _, change := range req.Changes {
			touched[change.ID] = true
			var existing model.UserTrainingSession
			err := tx.Where("user_id = ? AND session_id = ?", userID, change.ID).
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
			if change.Deleted && !found {
				continue
			}

			questionResults, err := json.Marshal(change.QuestionResults)
			if err != nil {
				return err
			}
			if found && trainingSessionChangeMatches(existing, change, questionResults) {
				continue
			}

			state.TrainingSessionRevision++
			if !found {
				existing = model.UserTrainingSession{UserID: userID, SessionID: change.ID}
			}
			if !change.Deleted {
				existing.Flow = change.Flow
				existing.LevelID = change.LevelID
				existing.LevelName = change.LevelName
				existing.CompletedAtEpochMillis = change.CompletedAtEpochMillis
				existing.FinishedEarly = change.FinishedEarly
				existing.QuestionsAnswered = change.QuestionsAnswered
				existing.NotesTotal = change.NotesTotal
				existing.CorrectNotes = change.CorrectNotes
				existing.MissedNotes = change.MissedNotes
				existing.Replays = change.Replays
				existing.DurationMillis = change.DurationMillis
				existing.AvgAnswerMillis = change.AvgAnswerMillis
				existing.AvgDeltaMillis = change.AvgDeltaMillis
				existing.BestStreak = change.BestStreak
				existing.KeysPracticed = change.KeysPracticed
				existing.QuestionResults = append(existing.QuestionResults[:0], questionResults...)
			}
			existing.Deleted = change.Deleted
			existing.Revision = state.TrainingSessionRevision
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
		result.Revision = state.TrainingSessionRevision

		var sessions []model.UserTrainingSession
		if err := tx.Where("user_id = ? AND revision > ?", userID, req.SinceRevision).
			Order("revision ASC").Find(&sessions).Error; err != nil {
			return err
		}
		byID := make(map[string]model.UserTrainingSession, len(sessions)+len(touched))
		for _, session := range sessions {
			byID[session.SessionID] = session
		}
		for id := range touched {
			if _, ok := byID[id]; ok {
				continue
			}
			var session model.UserTrainingSession
			if err := tx.Where("user_id = ? AND session_id = ?", userID, id).
				First(&session).Error; err == nil {
				byID[id] = session
			} else if !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
		}
		sessions = sessions[:0]
		for _, session := range byID {
			sessions = append(sessions, session)
		}
		sort.Slice(sessions, func(i, j int) bool { return sessions[i].Revision < sessions[j].Revision })
		for _, session := range sessions {
			result.Changes = append(result.Changes, trainingSessionSyncOutput(session))
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

func validateTrainingSessionSyncRequest(req trainingSessionSyncRequest) error {
	if req.SinceRevision < 0 {
		return errors.New("since_revision must not be negative")
	}
	if len(req.Changes) > maxTrainingSessionSyncChanges {
		return fmt.Errorf("changes must contain at most %d records", maxTrainingSessionSyncChanges)
	}
	seen := make(map[string]bool, len(req.Changes))
	for index := range req.Changes {
		change := &req.Changes[index]
		change.ID = strings.TrimSpace(change.ID)
		if change.ID == "" || len(change.ID) > 128 {
			return fmt.Errorf("changes[%d].id is required and must be at most 128 characters", index)
		}
		if seen[change.ID] {
			return fmt.Errorf("changes[%d] duplicates training session %q", index, change.ID)
		}
		seen[change.ID] = true
		if change.BaseRevision < 0 {
			return fmt.Errorf("changes[%d].base_revision must not be negative", index)
		}
		if change.Deleted {
			continue
		}

		change.Flow = strings.ToLower(strings.TrimSpace(change.Flow))
		change.LevelID = strings.TrimSpace(change.LevelID)
		change.LevelName = strings.TrimSpace(change.LevelName)
		if !course.IsValidMode(change.Flow) {
			return fmt.Errorf("changes[%d].flow must be singles, melodies, or chords", index)
		}
		if change.LevelID == "" || len(change.LevelID) > 640 {
			return fmt.Errorf("changes[%d].level_id is required and must be at most 640 characters", index)
		}
		if change.LevelName == "" || len(change.LevelName) > 220 {
			return fmt.Errorf("changes[%d].level_name is required and must be at most 220 characters", index)
		}
		if change.CompletedAtEpochMillis < 0 || change.QuestionsAnswered < 0 ||
			change.NotesTotal < 0 || change.CorrectNotes < 0 || change.MissedNotes < 0 ||
			change.Replays < 0 || change.DurationMillis < 0 || change.BestStreak < 0 ||
			change.KeysPracticed < 0 {
			return fmt.Errorf("changes[%d] contains a negative training statistic", index)
		}
		if change.CorrectNotes > change.NotesTotal {
			return fmt.Errorf("changes[%d].correct_notes must not exceed notes_total", index)
		}
		if change.AvgAnswerMillis != nil && *change.AvgAnswerMillis < 0 ||
			change.AvgDeltaMillis != nil && *change.AvgDeltaMillis < 0 {
			return fmt.Errorf("changes[%d] contains a negative average duration", index)
		}
		if len(change.QuestionResults) > maxQuestionResultsPerSession {
			return fmt.Errorf("changes[%d].question_results must contain at most %d records", index, maxQuestionResultsPerSession)
		}
		if change.QuestionResults == nil {
			change.QuestionResults = []trainingQuestionResult{}
		}
		for resultIndex, result := range change.QuestionResults {
			if result.QuestionNumber < 1 || result.NoteCount < 0 || result.MissedCount < 0 {
				return fmt.Errorf("changes[%d].question_results[%d] is invalid", index, resultIndex)
			}
		}
	}
	return nil
}

func trainingSessionChangeMatches(
	existing model.UserTrainingSession,
	change trainingSessionSyncChangeInput,
	questionResults json.RawMessage,
) bool {
	if existing.Deleted != change.Deleted {
		return false
	}
	if change.Deleted {
		return true
	}
	return existing.Flow == change.Flow && existing.LevelID == change.LevelID &&
		existing.LevelName == change.LevelName &&
		existing.CompletedAtEpochMillis == change.CompletedAtEpochMillis &&
		existing.FinishedEarly == change.FinishedEarly &&
		existing.QuestionsAnswered == change.QuestionsAnswered &&
		existing.NotesTotal == change.NotesTotal && existing.CorrectNotes == change.CorrectNotes &&
		existing.MissedNotes == change.MissedNotes && existing.Replays == change.Replays &&
		existing.DurationMillis == change.DurationMillis &&
		equalOptionalInt64(existing.AvgAnswerMillis, change.AvgAnswerMillis) &&
		equalOptionalInt64(existing.AvgDeltaMillis, change.AvgDeltaMillis) &&
		existing.BestStreak == change.BestStreak && existing.KeysPracticed == change.KeysPracticed &&
		bytes.Equal(bytes.TrimSpace(existing.QuestionResults), bytes.TrimSpace(questionResults))
}

func equalOptionalInt64(left, right *int64) bool {
	if left == nil || right == nil {
		return left == nil && right == nil
	}
	return *left == *right
}

func trainingSessionSyncOutput(session model.UserTrainingSession) trainingSessionSyncChangeOutput {
	result := trainingSessionSyncChangeOutput{
		ID:       session.SessionID,
		Revision: session.Revision,
		Deleted:  session.Deleted,
	}
	if session.Deleted {
		return result
	}
	result.Flow = session.Flow
	result.LevelID = session.LevelID
	result.LevelName = session.LevelName
	result.CompletedAtEpochMillis = session.CompletedAtEpochMillis
	result.FinishedEarly = session.FinishedEarly
	result.QuestionsAnswered = session.QuestionsAnswered
	result.NotesTotal = session.NotesTotal
	result.CorrectNotes = session.CorrectNotes
	result.MissedNotes = session.MissedNotes
	result.Replays = session.Replays
	result.DurationMillis = session.DurationMillis
	result.AvgAnswerMillis = session.AvgAnswerMillis
	result.AvgDeltaMillis = session.AvgDeltaMillis
	result.BestStreak = session.BestStreak
	result.KeysPracticed = session.KeysPracticed
	result.QuestionResults = append(json.RawMessage(nil), session.QuestionResults...)
	return result
}
