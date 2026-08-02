package model

import "time"

// UserSyncState owns the independent monotonically increasing revisions for one
// user. Per-resource counters keep cursors compact and prevent one collection's
// activity from making another collection download an empty revision range.
type UserSyncState struct {
	UserID                  uint      `gorm:"primaryKey" json:"user_id"`
	User                    User      `gorm:"constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"-"`
	Revision                int64     `gorm:"not null;default:0" json:"revision"`
	TrainingSessionRevision int64     `gorm:"not null;default:0" json:"training_session_revision"`
	CreatedAt               time.Time `json:"created_at"`
	UpdatedAt               time.Time `json:"updated_at"`
}

// UserLevel is the latest queryable state of one user-owned level. Deleted rows
// remain as tombstones so devices that have been offline can still learn about
// the deletion. Definition uses native JSON/JSONB rather than an opaque blob so
// later web/statistics features can query the musical configuration directly.
type UserLevel struct {
	ID         uint         `gorm:"primaryKey" json:"-"`
	UserID     uint         `gorm:"not null;uniqueIndex:idx_user_levels_identity,priority:1;index:idx_user_levels_changes,priority:1" json:"-"`
	User       User         `gorm:"constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"-"`
	Kind       string       `gorm:"size:20;not null;uniqueIndex:idx_user_levels_identity,priority:2;index:idx_user_levels_kind,priority:2" json:"kind"`
	LevelID    string       `gorm:"size:128;not null;uniqueIndex:idx_user_levels_identity,priority:3" json:"id"`
	Name       string       `gorm:"size:220;not null" json:"name"`
	Source     string       `gorm:"size:24;not null" json:"source"`
	Definition JSONDocument `gorm:"not null" json:"definition,omitempty"`
	Revision   int64        `gorm:"not null;index:idx_user_levels_changes,priority:2" json:"revision"`
	Deleted    bool         `gorm:"not null;default:false;index:idx_user_levels_kind,priority:3" json:"deleted"`
	CreatedAt  time.Time    `json:"created_at"`
	UpdatedAt  time.Time    `json:"updated_at"`
}

// UserTrainingSession is the latest queryable state of one completed training
// session. Accuracy, progress, and history are derived from these rows. Scalar
// statistics stay in indexed columns for web queries; the per-question detail
// remains native JSON/JSONB. Deleted rows are durable sync tombstones.
type UserTrainingSession struct {
	ID                     uint         `gorm:"primaryKey" json:"-"`
	UserID                 uint         `gorm:"not null;uniqueIndex:idx_user_training_sessions_identity,priority:1;index:idx_user_training_sessions_changes,priority:1" json:"-"`
	User                   User         `gorm:"constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"-"`
	SessionID              string       `gorm:"size:128;not null;uniqueIndex:idx_user_training_sessions_identity,priority:2" json:"id"`
	Flow                   string       `gorm:"size:20;not null" json:"flow"`
	LevelID                string       `gorm:"size:640;not null" json:"level_id"`
	LevelName              string       `gorm:"size:220;not null" json:"level_name"`
	CompletedAtEpochMillis int64        `gorm:"not null" json:"completed_at_epoch_millis"`
	FinishedEarly          bool         `gorm:"not null;default:false" json:"finished_early"`
	QuestionsAnswered      int          `gorm:"not null;default:0" json:"questions_answered"`
	NotesTotal             int          `gorm:"not null;default:0" json:"notes_total"`
	CorrectNotes           int          `gorm:"not null;default:0" json:"correct_notes"`
	MissedNotes            int          `gorm:"not null;default:0" json:"missed_notes"`
	Replays                int          `gorm:"not null;default:0" json:"replays"`
	DurationMillis         int64        `gorm:"not null;default:0" json:"duration_millis"`
	AvgAnswerMillis        *int64       `json:"avg_answer_millis"`
	AvgDeltaMillis         *int64       `json:"avg_delta_millis"`
	BestStreak             int          `gorm:"not null;default:0" json:"best_streak"`
	KeysPracticed          int          `gorm:"not null;default:0" json:"keys_practiced"`
	QuestionResults        JSONDocument `gorm:"not null" json:"question_results"`
	Revision               int64        `gorm:"not null;index:idx_user_training_sessions_changes,priority:2" json:"revision"`
	Deleted                bool         `gorm:"not null;default:false" json:"deleted"`
	CreatedAt              time.Time    `json:"created_at"`
	UpdatedAt              time.Time    `json:"updated_at"`
}
