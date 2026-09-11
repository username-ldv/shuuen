package seed

import (
	"encoding/json"
	"testing"

	"gorm.io/gorm"

	"shuuen-backend/internal/config"
	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/database"
	"shuuen-backend/internal/model"
)

func TestSeedAllFixedKeysCreatesOrderedIdempotentCourses(t *testing.T) {
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

	result, err := SeedAllFixedKeys(t.Context(), db, catalogConfig)
	if err != nil {
		t.Fatal(err)
	}
	if result.Courses != 26 || len(result.CourseIDs) != 26 || result.Groups != 156 || result.Levels != 1716 {
		t.Fatalf("unexpected fixed-key seed result: %+v", result)
	}

	wantNames := []string{
		"C tonic", "A natural minor",
		"G major", "E natural minor", "F major", "D natural minor",
		"D major", "B natural minor", "B♭ major", "G natural minor",
		"A major", "F♯ natural minor", "E♭ major", "C natural minor",
		"E major", "C♯ natural minor", "A♭ major", "F natural minor",
		"B major", "G♯ natural minor", "D♭ major", "B♭ natural minor",
		"F♯ major", "D♯ natural minor", "G♭ major", "E♭ natural minor",
	}
	var courses []model.Course
	if err := db.Where("id IN ?", result.CourseIDs).Order("sort_order ASC").Find(&courses).Error; err != nil {
		t.Fatal(err)
	}
	if len(courses) != len(fixedKeySpecs) {
		t.Fatalf("courses = %d, want %d", len(courses), len(fixedKeySpecs))
	}

	for keyIndex, key := range fixedKeySpecs {
		course := courses[keyIndex]
		if course.ID != result.CourseIDs[keyIndex] || course.Name != wantNames[keyIndex] ||
			course.Name != key.Name || course.SortOrder != keyIndex || !course.IsPublic ||
			course.StructureSource != model.CourseStructureManaged {
			t.Fatalf("course %d does not match key spec: course=%+v spec=%+v", keyIndex, course, key)
		}
		assertFixedKeyCourse(t, db, course, key)
	}
	if courses[22].ID == courses[24].ID || fixedKeySpecs[22].Root != fixedKeySpecs[24].Root {
		t.Fatal("F♯ major and G♭ major must be separate courses sharing one root pitch class")
	}

	second, err := SeedAllFixedKeys(t.Context(), db, catalogConfig)
	if err != nil {
		t.Fatalf("second fixed-key seed failed: %v", err)
	}
	if second.Courses != result.Courses || second.Groups != result.Groups || second.Levels != result.Levels {
		t.Fatalf("second fixed-key seed result changed: first=%+v second=%+v", result, second)
	}
	var groupCount, levelCount int64
	if err := db.Model(&model.CourseProgressionGroup{}).
		Joins("JOIN course_modes AS cm ON cm.id = course_progression_groups.course_mode_id").
		Where("cm.course_id IN ?", result.CourseIDs).Count(&groupCount).Error; err != nil {
		t.Fatal(err)
	}
	if err := db.Model(&model.CourseLevel{}).
		Joins("JOIN course_progression_groups AS pg ON pg.id = course_levels.progression_group_id").
		Joins("JOIN course_modes AS cm ON cm.id = pg.course_mode_id").
		Where("cm.course_id IN ?", result.CourseIDs).Count(&levelCount).Error; err != nil {
		t.Fatal(err)
	}
	if groupCount != 156 || levelCount != 1716 {
		t.Fatalf("second seed duplicated rows: groups=%d levels=%d", groupCount, levelCount)
	}
}

func assertFixedKeyCourse(t *testing.T, db *gorm.DB, course model.Course, key fixedKeySpec) {
	t.Helper()
	var mode model.CourseMode
	if err := db.Where("course_id = ? AND mode = ?", course.ID, coursedomain.ModeMelodies).First(&mode).Error; err != nil {
		t.Fatal(err)
	}
	var groups []model.CourseProgressionGroup
	if err := db.Where("course_mode_id = ?", mode.ID).Order("sort_order ASC").Find(&groups).Error; err != nil {
		t.Fatal(err)
	}
	if len(groups) != 6 {
		t.Fatalf("%s groups = %d, want 6", course.Name, len(groups))
	}

	activePitches := fixedKeyPitchSet(key)
	additions := majorKeyAdditions
	if key.ScaleType == "NaturalMinor" {
		additions = naturalMinorKeyAdditions
	}
	for groupIndex, group := range groups {
		if group.SortOrder != groupIndex {
			t.Fatalf("%s group %d sort order = %d", course.Name, groupIndex, group.SortOrder)
		}
		if groupIndex > 0 {
			activePitches[pitchAt(key.RootIndex+additions[groupIndex-1].Offset)] = true
		}
		var levels []model.CourseLevel
		if err := db.Where("progression_group_id = ?", group.ID).Order("sort_order ASC").Find(&levels).Error; err != nil {
			t.Fatal(err)
		}
		if len(levels) != 11 {
			t.Fatalf("%s group %q levels = %d, want 11", course.Name, group.Name, len(levels))
		}
		for levelIndex, level := range levels {
			var definition seededMelodyDefinition
			if err := json.Unmarshal(level.Definition, &definition); err != nil {
				t.Fatal(err)
			}
			if _, err := coursedomain.ValidateDefinition(coursedomain.ModeMelodies, json.RawMessage(level.Definition), true); err != nil {
				t.Fatalf("%s level %q is invalid: %v", course.Name, level.Name, err)
			}
			config := definition.Config
			if config.ScaleConfig.Type != "absolute" || config.ScaleConfig.Root != key.Root {
				t.Fatalf("%s level %q has wrong absolute root: %+v", course.Name, level.Name, config.ScaleConfig)
			}
			wantScaleType := key.ScaleType
			if groupIndex > 0 {
				wantScaleType = "Chromatic"
			}
			if config.ScaleConfig.ScaleType != wantScaleType {
				t.Fatalf("%s group %q scale type = %q, want %q", course.Name, group.Name, config.ScaleConfig.ScaleType, wantScaleType)
			}
			if config.Tempo != 60+levelIndex*10 || config.QuestionsNumber == nil || *config.QuestionsNumber != 20 ||
				config.NotesPerSequence == nil || *config.NotesPerSequence != 8 || config.RotateEveryQuestions != nil {
				t.Fatalf("%s level %q does not match fixed C-tonic parameters", course.Name, level.Name)
			}
			if config.Range.From.MIDIIndex != 36 || config.Range.To.MIDIIndex != 96 || config.MelodyStyle.ID != "steady-quarters" {
				t.Fatalf("%s level %q has wrong range or melody style", course.Name, level.Name)
			}
			if len(config.ScaleConfig.PitchStates) != 12 {
				t.Fatalf("%s level %q pitch states = %d, want 12", course.Name, level.Name, len(config.ScaleConfig.PitchStates))
			}
			activeCount := 0
			for _, state := range config.ScaleConfig.PitchStates {
				if state.Active {
					activeCount++
				}
				if state.Active != activePitches[state.Pitch] {
					t.Fatalf("%s group %q pitch %s active = %t, want %t", course.Name, group.Name, state.Pitch, state.Active, activePitches[state.Pitch])
				}
			}
			if activeCount != 7+groupIndex {
				t.Fatalf("%s group %q active pitches = %d, want %d", course.Name, group.Name, activeCount, 7+groupIndex)
			}
			assertFixedKeyContext(t, definition.Context, key.ScaleType == "NaturalMinor")
		}
	}
}

func assertFixedKeyContext(t *testing.T, context *coursedomain.DegreeContext, naturalMinor bool) {
	t.Helper()
	if context == nil || len(context.Nodes) != 1 {
		t.Fatal("fixed-key context must contain exactly one node")
	}
	node := context.Nodes[0]
	if node.FirstDegree.Degree != "D1" || node.FirstDegree.Octave != 2 || len(node.ExtraDegrees) != 0 ||
		node.Sustain.Type != "endless" || node.Duration.Type != "same_as_scale_rotation" {
		t.Fatalf("unexpected fixed-key context node: %+v", node)
	}
	if node.SetupMelody == nil || node.SetupMelody.Repeat != "Once" ||
		node.SetupMelody.Melody.FirstDegree.Degree != "D1" || node.SetupMelody.Melody.FirstDegree.Octave != 3 {
		t.Fatalf("unexpected fixed-key setup melody: %+v", node.SetupMelody)
	}
	wantThird := coursedomain.Degree("D3")
	if naturalMinor {
		wantThird = "DF3"
	}
	wantDegrees := []coursedomain.Degree{wantThird, "D5", "D1"}
	if len(node.SetupMelody.Melody.ExtraDegrees) != len(wantDegrees) {
		t.Fatalf("setup melody steps = %d", len(node.SetupMelody.Melody.ExtraDegrees))
	}
	for index, step := range node.SetupMelody.Melody.ExtraDegrees {
		if step.Degree != wantDegrees[index] || step.Direction != "Up" {
			t.Fatalf("unexpected setup melody step %d: %+v", index, step)
		}
	}
}
