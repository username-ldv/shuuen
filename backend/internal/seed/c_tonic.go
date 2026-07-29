package seed

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"gorm.io/gorm"

	"shuuen-backend/internal/catalog"
	"shuuen-backend/internal/config"
	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
)

const (
	cTonicCoursePath = "c-tonic"
	cTonicModePath   = "c-tonic/melodies"
)

type CTonicResult struct {
	CourseID uint
	Groups   int
	Levels   int
}

type cTonicGroupSpec struct {
	Slug        string
	Name        string
	Description string
	AddedPitch  *coursedomain.Pitch
}

var cTonicGroupSpecs = []cTonicGroupSpec{
	{
		Slug: "c-major", Name: "C major",
		Description: "The seven diatonic notes of C major.",
	},
	{
		Slug: "plus-f-sharp", Name: "F♯",
		Description: "C major with F♯ added.", AddedPitch: pitchPointer(coursedomain.PitchFSharp),
	},
	{
		Slug: "plus-c-sharp", Name: "C♯ (D♭)",
		Description: "C major with F♯ and C♯ added.", AddedPitch: pitchPointer(coursedomain.PitchCSharp),
	},
	{
		Slug: "plus-g-sharp", Name: "G♯ (A♭)",
		Description: "C major with F♯, C♯, and G♯ added.", AddedPitch: pitchPointer(coursedomain.PitchGSharp),
	},
	{
		Slug: "plus-d-sharp", Name: "D♯ (E♭)",
		Description: "C major with F♯, C♯, G♯, and D♯ added.", AddedPitch: pitchPointer(coursedomain.PitchDSharp),
	},
	{
		Slug: "plus-a-sharp", Name: "A♯ (B♭)",
		Description: "The complete chromatic scale, reached by adding A♯ to the preceding pitches.", AddedPitch: pitchPointer(coursedomain.PitchASharp),
	},
}

type seededAbsoluteScaleConfig struct {
	Type string `json:"type"`
	coursedomain.AbsoluteScaleConfig
}

type seededRandomMelodyConfig struct {
	Type                   string                    `json:"type"`
	ScaleConfig            seededAbsoluteScaleConfig `json:"scale_config"`
	QuestionsNumber        *int                      `json:"questions_number"`
	NotesPerSequence       *int                      `json:"notes_per_sequence"`
	Tempo                  int                       `json:"tempo"`
	Range                  coursedomain.NoteRange    `json:"range"`
	RotateEveryQuestions   *int                      `json:"rotate_every_questions"`
	MelodyStyle            coursedomain.MelodyStyle  `json:"melody_style"`
	TuneInconsistencyCents int                       `json:"tune_inconsistency_cents"`
}

type seededMelodyDefinition struct {
	Config  seededRandomMelodyConfig    `json:"config"`
	Context *coursedomain.DegreeContext `json:"context"`
}

// SeedCTonic creates or resets the deterministic C-tonic course records. It is
// safe to run repeatedly: stable public ids are updated in place, while other
// modes or progression groups that an administrator added later are preserved.
func SeedCTonic(ctx context.Context, db *gorm.DB, catalogConfig config.CatalogConfig) (CTonicResult, error) {
	if err := createCTonicFolders(catalogConfig); err != nil {
		return CTonicResult{}, err
	}
	scanner, err := catalog.NewScanner(db, catalogConfig)
	if err != nil {
		return CTonicResult{}, err
	}
	if _, err := scanner.Scan(ctx); err != nil {
		return CTonicResult{}, fmt.Errorf("scan seeded course folders: %w", err)
	}

	var result CTonicResult
	err = db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		courseGroup, err := findSeedLibraryGroup(tx, cTonicCoursePath)
		if err != nil {
			return err
		}
		modeGroup, err := findSeedLibraryGroup(tx, cTonicModePath)
		if err != nil {
			return err
		}

		if err := upsertCTonicCourse(tx, courseGroup); err != nil {
			return err
		}
		mode, err := upsertCTonicMode(tx, courseGroup.ID, modeGroup.ID)
		if err != nil {
			return err
		}

		activePitches := cMajorPitchSet()
		for groupIndex, spec := range cTonicGroupSpecs {
			if spec.AddedPitch != nil {
				activePitches[*spec.AddedPitch] = true
			}
			libraryGroup, err := findSeedLibraryGroup(tx, filepath.ToSlash(filepath.Join(cTonicModePath, spec.Slug)))
			if err != nil {
				return err
			}
			progression, err := upsertCTonicProgressionGroup(tx, mode.ID, libraryGroup.ID, spec, groupIndex)
			if err != nil {
				return err
			}
			for levelIndex, tempo := 0, 60; tempo <= 160; levelIndex, tempo = levelIndex+1, tempo+10 {
				definition, err := cTonicDefinition(activePitches, groupIndex != 0, tempo)
				if err != nil {
					return err
				}
				level := model.CourseLevel{
					ID:                 fmt.Sprintf("seed-c-tonic-%s-%03d-bpm", spec.Slug, tempo),
					ProgressionGroupID: progression.ID,
					Name:               fmt.Sprintf("%s — %d BPM", spec.Name, tempo),
					Source:             "built_in",
					Definition:         model.JSONDocument(definition),
					SortOrder:          levelIndex,
					IsPublic:           true,
					SectionLibraryGroupID: func() *uint {
						id := libraryGroup.ID
						return &id
					}(),
				}
				if err := upsertCTonicLevel(tx, level); err != nil {
					return err
				}
				result.Levels++
			}
			result.Groups++
		}
		result.CourseID = courseGroup.ID
		return nil
	})
	if err != nil {
		return CTonicResult{}, err
	}
	return result, nil
}

func cTonicDefinition(active map[coursedomain.Pitch]bool, chromatic bool, tempo int) (json.RawMessage, error) {
	pitchOrder := []coursedomain.Pitch{
		coursedomain.PitchC, coursedomain.PitchCSharp, coursedomain.PitchD, coursedomain.PitchDSharp,
		coursedomain.PitchE, coursedomain.PitchF, coursedomain.PitchFSharp, coursedomain.PitchG,
		coursedomain.PitchGSharp, coursedomain.PitchA, coursedomain.PitchASharp, coursedomain.PitchB,
	}
	states := make([]coursedomain.PitchState, 0, len(pitchOrder))
	for _, pitch := range pitchOrder {
		states = append(states, coursedomain.PitchState{Pitch: pitch, Active: active[pitch]})
	}
	questions, notesPerSequence := 20, 8
	contextName := "C tonic drone"
	definition := seededMelodyDefinition{
		Config: seededRandomMelodyConfig{
			Type: "random",
			ScaleConfig: seededAbsoluteScaleConfig{
				Type: "absolute",
				AbsoluteScaleConfig: coursedomain.AbsoluteScaleConfig{
					Root: coursedomain.PitchC, ScaleType: "Major", PitchStates: states,
				},
			},
			QuestionsNumber: &questions, NotesPerSequence: &notesPerSequence,
			Tempo: tempo,
			Range: coursedomain.NoteRange{
				From: coursedomain.Note{MIDIIndex: 36},
				To:   coursedomain.Note{MIDIIndex: 96},
			},
			MelodyStyle: steadyQuartersStyle(),
		},
		Context: &coursedomain.DegreeContext{
			ID: "seed-c-tonic-drone", Source: "built_in", Name: &contextName,
			Nodes: []coursedomain.DegreeContextNode{
				{
					FirstDegree:  coursedomain.DegreeWithOctave{Degree: "D1", Octave: 2},
					ExtraDegrees: []coursedomain.Degree{},
					Sustain:      coursedomain.Sustain{Type: "endless"},
					Duration:     coursedomain.ContextDuration{Type: "same_as_scale_rotation"},
					SetupMelody: &coursedomain.SetupMelody{
						Melody: coursedomain.RelativeMelody{
							FirstDegree: coursedomain.DegreeWithOctave{Degree: "D1", Octave: 3},
							ExtraDegrees: []coursedomain.DirectedDegree{
								{Degree: "D3", Direction: "Up"},
								{Degree: "D5", Direction: "Up"},
								{Degree: "D1", Direction: "Up"},
							},
						},
						Repeat: "Once",
					},
					RelativeDirection: "Up",
				},
			},
		},
	}
	if chromatic {
		definition.Config.ScaleConfig.ScaleType = "Chromatic"
	}
	raw, err := json.Marshal(definition)
	if err != nil {
		return nil, err
	}
	if _, err := coursedomain.ValidateDefinition(coursedomain.ModeMelodies, raw, true); err != nil {
		return nil, fmt.Errorf("validate C-tonic level at %d BPM: %w", tempo, err)
	}
	return raw, nil
}

func steadyQuartersStyle() coursedomain.MelodyStyle {
	return coursedomain.MelodyStyle{
		ID:          "steady-quarters",
		Name:        "Steady quarters",
		Description: "Every note a plain quarter, picked fully at random — the original drill.",
		Tier:        "Beginner",
		Figures: []coursedomain.WeightedFigure{
			{
				Figure: coursedomain.RhythmFigure{
					Values: []string{"Quarter"}, Contour: []*int{}, Ladder: "Scale",
				},
				Weight: 1,
			},
		},
		NoteWeights: coursedomain.NoteWeights{
			IntervalWeights: []float64{}, DegreeWeights: map[coursedomain.Degree]float64{}, ChordToneBoost: 1,
		},
	}
}

func cMajorPitchSet() map[coursedomain.Pitch]bool {
	return map[coursedomain.Pitch]bool{
		coursedomain.PitchC: true, coursedomain.PitchD: true, coursedomain.PitchE: true,
		coursedomain.PitchF: true, coursedomain.PitchG: true, coursedomain.PitchA: true,
		coursedomain.PitchB: true,
	}
}

func createCTonicFolders(catalogConfig config.CatalogConfig) error {
	if catalogConfig.FolderMetadataFile == "" || filepath.Base(catalogConfig.FolderMetadataFile) != catalogConfig.FolderMetadataFile {
		return errors.New("folder metadata file must be a file name")
	}
	root, err := filepath.Abs(catalogConfig.Root)
	if err != nil {
		return err
	}
	public := true
	folders := []struct {
		Path        string
		Name        string
		Description string
		SortOrder   int
	}{
		{Path: cTonicCoursePath, Name: "C tonic", Description: "Progressive melody training over a C tonic.", SortOrder: 0},
		{Path: cTonicModePath, Name: "Melodies", Description: "Generated melody progressions.", SortOrder: 0},
	}
	for index, spec := range cTonicGroupSpecs {
		folders = append(folders, struct {
			Path        string
			Name        string
			Description string
			SortOrder   int
		}{
			Path: filepath.ToSlash(filepath.Join(cTonicModePath, spec.Slug)), Name: spec.Name,
			Description: spec.Description, SortOrder: index,
		})
	}
	for _, folder := range folders {
		absolute := filepath.Join(root, filepath.FromSlash(folder.Path))
		relative, err := filepath.Rel(root, absolute)
		if err != nil || relative == ".." || filepath.IsAbs(relative) {
			return fmt.Errorf("seed folder escapes catalog root: %s", folder.Path)
		}
		if err := os.MkdirAll(absolute, 0o755); err != nil {
			return err
		}
		metadata := catalog.FolderMetadata{
			Name: folder.Name, Description: folder.Description, SortOrder: folder.SortOrder, IsPublic: &public,
		}
		if err := writeSeedFolderMetadata(filepath.Join(absolute, catalogConfig.FolderMetadataFile), metadata); err != nil {
			return err
		}
	}
	return nil
}

func writeSeedFolderMetadata(metadataPath string, metadata catalog.FolderMetadata) error {
	payload := map[string]any{}
	if existing, err := os.ReadFile(metadataPath); err == nil {
		if err := json.Unmarshal(existing, &payload); err != nil {
			return fmt.Errorf("read %s: %w", metadataPath, err)
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return err
	}
	payload["name"] = metadata.Name
	payload["description"] = metadata.Description
	payload["sort_order"] = metadata.SortOrder
	payload["is_public"] = *metadata.IsPublic
	delete(payload, "is_active")
	encoded, err := json.MarshalIndent(payload, "", "  ")
	if err != nil {
		return err
	}
	encoded = append(encoded, '\n')
	if existing, err := os.ReadFile(metadataPath); err == nil && bytes.Equal(existing, encoded) {
		return nil
	}
	return os.WriteFile(metadataPath, encoded, 0o644)
}

func findSeedLibraryGroup(db *gorm.DB, groupPath string) (model.LibraryGroup, error) {
	var group model.LibraryGroup
	if err := db.Where("path = ? AND deleted_at IS NULL", groupPath).First(&group).Error; err != nil {
		return model.LibraryGroup{}, fmt.Errorf("find seeded library group %q: %w", groupPath, err)
	}
	return group, nil
}

func upsertCTonicCourse(db *gorm.DB, libraryGroup model.LibraryGroup) error {
	values := map[string]any{
		"deleted_at": nil, "name": "C tonic",
		"description": "Progressive melody training over a C tonic.", "author": "LdV",
		"is_public": true, "sort_order": 0, "structure_source": model.CourseStructureManaged,
	}
	var existing model.Course
	err := db.Unscoped().Where("id = ?", libraryGroup.ID).First(&existing).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		course := model.Course{
			ID: libraryGroup.ID, Name: "C tonic", Description: "Progressive melody training over a C tonic.",
			Author: "LdV", IsPublic: true, StructureSource: model.CourseStructureManaged,
		}
		return db.Omit("Modes").Create(&course).Error
	}
	if err != nil {
		return err
	}
	return db.Unscoped().Model(&model.Course{}).Where("id = ?", libraryGroup.ID).Updates(values).Error
}

func upsertCTonicMode(db *gorm.DB, courseID uint, libraryGroupID uint) (model.CourseMode, error) {
	var mode model.CourseMode
	err := db.Unscoped().Where("course_id = ? AND mode = ?", courseID, coursedomain.ModeMelodies).First(&mode).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		mode = model.CourseMode{
			CourseID: courseID, Mode: coursedomain.ModeMelodies, LibraryGroupID: libraryGroupID,
			Name: "Melodies", Description: "Generated melody progressions.",
		}
		if err := db.Omit("LibraryGroup", "Groups").Create(&mode).Error; err != nil {
			return model.CourseMode{}, err
		}
		return mode, nil
	}
	if err != nil {
		return model.CourseMode{}, err
	}
	if err := db.Unscoped().Model(&model.CourseMode{}).Where("id = ?", mode.ID).Updates(map[string]any{
		"deleted_at": nil, "library_group_id": libraryGroupID, "name": "Melodies",
		"description": "Generated melody progressions.", "sort_order": 0,
	}).Error; err != nil {
		return model.CourseMode{}, err
	}
	mode.LibraryGroupID, mode.Name, mode.Description, mode.SortOrder = libraryGroupID, "Melodies", "Generated melody progressions.", 0
	return mode, nil
}

func upsertCTonicProgressionGroup(db *gorm.DB, modeID uint, libraryGroupID uint, spec cTonicGroupSpec, sortOrder int) (model.CourseProgressionGroup, error) {
	publicID := "seed-c-tonic-" + spec.Slug
	var progression model.CourseProgressionGroup
	err := db.Unscoped().Where("public_id = ?", publicID).First(&progression).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		progression = model.CourseProgressionGroup{
			CourseModeID: modeID, PublicID: publicID, LibraryGroupID: libraryGroupID,
			Name: spec.Name, Description: spec.Description, SortOrder: sortOrder,
		}
		if err := db.Omit("LibraryGroup", "Levels").Create(&progression).Error; err != nil {
			return model.CourseProgressionGroup{}, err
		}
		return progression, nil
	}
	if err != nil {
		return model.CourseProgressionGroup{}, err
	}
	if err := db.Unscoped().Model(&model.CourseProgressionGroup{}).Where("id = ?", progression.ID).Updates(map[string]any{
		"deleted_at": nil, "course_mode_id": modeID, "library_group_id": libraryGroupID,
		"name": spec.Name, "description": spec.Description, "sort_order": sortOrder,
	}).Error; err != nil {
		return model.CourseProgressionGroup{}, err
	}
	progression.CourseModeID, progression.LibraryGroupID = modeID, libraryGroupID
	progression.Name, progression.Description, progression.SortOrder = spec.Name, spec.Description, sortOrder
	return progression, nil
}

func upsertCTonicLevel(db *gorm.DB, level model.CourseLevel) error {
	var existing model.CourseLevel
	err := db.Unscoped().Where("id = ?", level.ID).First(&existing).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return db.Omit("ProgressionGroup", "LibraryMelody", "LibraryVariant", "SectionLibraryGroup").Create(&level).Error
	}
	if err != nil {
		return err
	}
	return db.Unscoped().Model(&model.CourseLevel{}).Where("id = ?", level.ID).Updates(map[string]any{
		"deleted_at": nil, "progression_group_id": level.ProgressionGroupID,
		"name": level.Name, "source": level.Source, "definition": level.Definition,
		"sort_order": level.SortOrder, "is_public": level.IsPublic,
		"library_melody_id": nil, "library_variant_id": nil,
		"section_library_group_id": level.SectionLibraryGroupID,
	}).Error
}

func pitchPointer(value coursedomain.Pitch) *coursedomain.Pitch { return &value }
