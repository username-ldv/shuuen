package seed

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"shuuen-backend/internal/config"
	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/database"
	"shuuen-backend/internal/model"
)

func TestSeedRandomTonicCreatesCompleteIdempotentCourse(t *testing.T) {
	db, err := database.Connect(config.DatabaseConfig{Driver: "sqlite", DSN: ":memory:"})
	if err != nil {
		t.Fatal(err)
	}
	if err := database.Migrate(t.Context(), db); err != nil {
		t.Fatal(err)
	}
	catalogConfig := config.CatalogConfig{
		Root: t.TempDir(), FolderMetadataFile: ".shuuen.json",
		MelodyMetadataSuffix: ".shuuen.json", MaxUploadBytes: 1024,
	}

	result, err := SeedRandomTonic(t.Context(), db, catalogConfig)
	if err != nil {
		t.Fatal(err)
	}
	if result.CourseID == 0 || result.Groups != 6 || result.Levels != 66 {
		t.Fatalf("unexpected seed result: %+v", result)
	}

	var courseRecord model.Course
	if err := db.Where("id = ?", result.CourseID).First(&courseRecord).Error; err != nil {
		t.Fatal(err)
	}
	if courseRecord.Name != "Random tonic" || !courseRecord.IsPublic || courseRecord.SortOrder != 1 || courseRecord.StructureSource != model.CourseStructureManaged {
		t.Fatalf("unexpected course metadata: %+v", courseRecord)
	}
	var mode model.CourseMode
	if err := db.Where("course_id = ? AND mode = ?", result.CourseID, coursedomain.ModeMelodies).First(&mode).Error; err != nil {
		t.Fatal(err)
	}
	var groups []model.CourseProgressionGroup
	if err := db.Where("course_mode_id = ?", mode.ID).Order("sort_order asc").Find(&groups).Error; err != nil {
		t.Fatal(err)
	}
	wantNames := []string{"Diatonic", "♯4", "♭2", "♭6", "♭3", "♭7"}
	wantAddedDegrees := []coursedomain.Degree{"DS4", "DF2", "DF6", "DF3", "DF7"}
	if len(groups) != len(wantNames) {
		t.Fatalf("progression groups = %d, want %d", len(groups), len(wantNames))
	}
	activeDegrees := majorDegreeSet()
	for groupIndex, group := range groups {
		if group.Name != wantNames[groupIndex] {
			t.Fatalf("group %d name = %q, want %q", groupIndex, group.Name, wantNames[groupIndex])
		}
		if groupIndex > 0 {
			activeDegrees[wantAddedDegrees[groupIndex-1]] = true
		}
		var levels []model.CourseLevel
		if err := db.Where("progression_group_id = ?", group.ID).Order("sort_order asc").Find(&levels).Error; err != nil {
			t.Fatal(err)
		}
		if len(levels) != 11 {
			t.Fatalf("group %q levels = %d, want 11", group.Name, len(levels))
		}
		for levelIndex, level := range levels {
			var definition seededRelativeMelodyDefinition
			if err := json.Unmarshal(level.Definition, &definition); err != nil {
				t.Fatal(err)
			}
			if _, err := coursedomain.ValidateDefinition(coursedomain.ModeMelodies, json.RawMessage(level.Definition), true); err != nil {
				t.Fatalf("level %q is invalid: %v", level.Name, err)
			}
			if definition.Config.ScaleConfig.Type != "relative" {
				t.Fatalf("level %q scale config type = %q", level.Name, definition.Config.ScaleConfig.Type)
			}
			if definition.Config.Tempo != 60+levelIndex*10 {
				t.Fatalf("level %q tempo = %d", level.Name, definition.Config.Tempo)
			}
			if definition.Config.QuestionsNumber == nil || *definition.Config.QuestionsNumber != 20 ||
				definition.Config.NotesPerSequence == nil || *definition.Config.NotesPerSequence != 5 ||
				definition.Config.RotateEveryQuestions == nil || *definition.Config.RotateEveryQuestions != 5 {
				t.Fatalf("level %q has wrong questions, sequence length, or rotation", level.Name)
			}
			if definition.Config.Range.From.MIDIIndex != 36 || definition.Config.Range.To.MIDIIndex != 96 {
				t.Fatalf("level %q has wrong C2-C7 range", level.Name)
			}
			if definition.Config.MelodyStyle.ID != "steady-quarters" {
				t.Fatalf("level %q has wrong melody style", level.Name)
			}
			states := definition.Config.ScaleConfig.DegreeStates
			if len(states) != 12 {
				t.Fatalf("group %q degree states = %d, want 12", group.Name, len(states))
			}
			activeCount := 0
			for _, state := range states {
				if state.Active {
					activeCount++
				}
				if state.Active != activeDegrees[state.Degree] {
					t.Fatalf("group %q degree %s active = %t, want %t", group.Name, state.Degree, state.Active, activeDegrees[state.Degree])
				}
			}
			if activeCount != 7+groupIndex {
				t.Fatalf("group %q active degrees = %d, want %d", group.Name, activeCount, 7+groupIndex)
			}
			if groupIndex == 0 && definition.Config.ScaleConfig.ScaleType != "Major" {
				t.Fatalf("base group scale type = %q", definition.Config.ScaleConfig.ScaleType)
			}
			if groupIndex > 0 && definition.Config.ScaleConfig.ScaleType != "Chromatic" {
				t.Fatalf("chromatic group scale type = %q", definition.Config.ScaleConfig.ScaleType)
			}
			assertCTonicContext(t, definition.Context)
		}
	}

	if _, err := SeedRandomTonic(t.Context(), db, catalogConfig); err != nil {
		t.Fatalf("second seed failed: %v", err)
	}
	var groupCount, levelCount int64
	if err := db.Model(&model.CourseProgressionGroup{}).Where("course_mode_id = ?", mode.ID).Count(&groupCount).Error; err != nil {
		t.Fatal(err)
	}
	if err := db.Model(&model.CourseLevel{}).
		Joins("JOIN course_progression_groups AS pg ON pg.id = course_levels.progression_group_id").
		Where("pg.course_mode_id = ?", mode.ID).Count(&levelCount).Error; err != nil {
		t.Fatal(err)
	}
	if groupCount != 6 || levelCount != 66 {
		t.Fatalf("second seed duplicated rows: groups=%d levels=%d", groupCount, levelCount)
	}
	for _, spec := range randomTonicGroupSpecs {
		metadataPath := filepath.Join(catalogConfig.Root, filepath.FromSlash(randomTonicModePath), spec.Slug, catalogConfig.FolderMetadataFile)
		if _, err := os.Stat(metadataPath); err != nil {
			t.Fatalf("seed metadata %q was not created: %v", metadataPath, err)
		}
	}
}
