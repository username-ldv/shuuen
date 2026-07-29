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

func TestSeedCTonicCreatesCompleteIdempotentCourse(t *testing.T) {
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

	result, err := SeedCTonic(t.Context(), db, catalogConfig)
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
	if courseRecord.Name != "C tonic" || !courseRecord.IsPublic || courseRecord.StructureSource != model.CourseStructureManaged {
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
	wantNames := []string{"C major", "F♯", "C♯ (D♭)", "G♯ (A♭)", "D♯ (E♭)", "A♯ (B♭)"}
	if len(groups) != len(wantNames) {
		t.Fatalf("progression groups = %d, want %d", len(groups), len(wantNames))
	}
	for groupIndex, group := range groups {
		if group.Name != wantNames[groupIndex] {
			t.Fatalf("group %d name = %q, want %q", groupIndex, group.Name, wantNames[groupIndex])
		}
		var levels []model.CourseLevel
		if err := db.Where("progression_group_id = ?", group.ID).Order("sort_order asc").Find(&levels).Error; err != nil {
			t.Fatal(err)
		}
		if len(levels) != 11 {
			t.Fatalf("group %q levels = %d, want 11", group.Name, len(levels))
		}
		for levelIndex, level := range levels {
			var definition seededMelodyDefinition
			if err := json.Unmarshal(level.Definition, &definition); err != nil {
				t.Fatal(err)
			}
			if _, err := coursedomain.ValidateDefinition(coursedomain.ModeMelodies, json.RawMessage(level.Definition), true); err != nil {
				t.Fatalf("level %q is invalid: %v", level.Name, err)
			}
			if definition.Config.Tempo != 60+levelIndex*10 {
				t.Fatalf("level %q tempo = %d", level.Name, definition.Config.Tempo)
			}
			if definition.Config.QuestionsNumber == nil || *definition.Config.QuestionsNumber != 20 ||
				definition.Config.NotesPerSequence == nil || *definition.Config.NotesPerSequence != 8 {
				t.Fatalf("level %q has wrong question or sequence counts", level.Name)
			}
			if definition.Config.Range.From.MIDIIndex != 36 || definition.Config.Range.To.MIDIIndex != 96 {
				t.Fatalf("level %q has wrong C2-C7 range", level.Name)
			}
			if definition.Config.MelodyStyle.ID != "steady-quarters" {
				t.Fatalf("level %q has wrong melody style", level.Name)
			}
			activeCount := 0
			for _, state := range definition.Config.ScaleConfig.PitchStates {
				if state.Active {
					activeCount++
				}
			}
			if activeCount != 7+groupIndex {
				t.Fatalf("group %q active pitches = %d, want %d", group.Name, activeCount, 7+groupIndex)
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

	if _, err := SeedCTonic(t.Context(), db, catalogConfig); err != nil {
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
	for _, spec := range cTonicGroupSpecs {
		metadataPath := filepath.Join(catalogConfig.Root, filepath.FromSlash(cTonicModePath), spec.Slug, catalogConfig.FolderMetadataFile)
		if _, err := os.Stat(metadataPath); err != nil {
			t.Fatalf("seed metadata %q was not created: %v", metadataPath, err)
		}
	}
}

func assertCTonicContext(t *testing.T, context *coursedomain.DegreeContext) {
	t.Helper()
	if context == nil || len(context.Nodes) != 1 {
		t.Fatal("C tonic context must contain exactly one node")
	}
	node := context.Nodes[0]
	if node.FirstDegree.Degree != "D1" || node.FirstDegree.Octave != 2 || len(node.ExtraDegrees) != 0 {
		t.Fatalf("unexpected tonic context node: %+v", node)
	}
	if node.Sustain.Type != "endless" || node.Duration.Type != "same_as_scale_rotation" {
		t.Fatalf("unexpected context sustain or duration: %+v", node)
	}
	if node.SetupMelody == nil || node.SetupMelody.Repeat != "Once" ||
		node.SetupMelody.Melody.FirstDegree.Degree != "D1" || node.SetupMelody.Melody.FirstDegree.Octave != 3 {
		t.Fatalf("unexpected setup melody: %+v", node.SetupMelody)
	}
	wantDegrees := []coursedomain.Degree{"D3", "D5", "D1"}
	if len(node.SetupMelody.Melody.ExtraDegrees) != len(wantDegrees) {
		t.Fatalf("setup melody steps = %d", len(node.SetupMelody.Melody.ExtraDegrees))
	}
	for index, step := range node.SetupMelody.Melody.ExtraDegrees {
		if step.Degree != wantDegrees[index] || step.Direction != "Up" {
			t.Fatalf("unexpected setup melody step %d: %+v", index, step)
		}
	}
}
