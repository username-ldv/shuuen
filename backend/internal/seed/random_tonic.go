package seed

import (
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
	randomTonicCoursePath = "random-tonic"
	randomTonicModePath   = "random-tonic/melodies"
)

type RandomTonicResult struct {
	CourseID uint
	Groups   int
	Levels   int
}

type randomTonicGroupSpec struct {
	Slug        string
	Name        string
	Description string
	AddedDegree *coursedomain.Degree
}

var randomTonicGroupSpecs = []randomTonicGroupSpec{
	{
		Slug: "diatonic", Name: "Diatonic",
		Description: "The seven diatonic degrees of a randomly selected major key.",
	},
	{
		Slug: "plus-sharp-4", Name: "♯4",
		Description: "Random Major with ♯4 added.", AddedDegree: degreePointer("DS4"),
	},
	{
		Slug: "plus-flat-2", Name: "♭2",
		Description: "Random Major with ♯4 and ♭2 added.", AddedDegree: degreePointer("DF2"),
	},
	{
		Slug: "plus-flat-6", Name: "♭6",
		Description: "Random Major with ♯4, ♭2, and ♭6 added.", AddedDegree: degreePointer("DF6"),
	},
	{
		Slug: "plus-flat-3", Name: "♭3",
		Description: "Random Major with ♯4, ♭2, ♭6, and ♭3 added.", AddedDegree: degreePointer("DF3"),
	},
	{
		Slug: "plus-flat-7", Name: "♭7",
		Description: "All twelve chromatic degrees, reached by adding ♭7 to the preceding degrees.", AddedDegree: degreePointer("DF7"),
	},
}

type seededRelativeScaleConfig struct {
	Type string `json:"type"`
	coursedomain.RelativeScaleConfig
}

type seededRelativeRandomMelodyConfig struct {
	Type                   string                    `json:"type"`
	ScaleConfig            seededRelativeScaleConfig `json:"scale_config"`
	QuestionsNumber        *int                      `json:"questions_number"`
	NotesPerSequence       *int                      `json:"notes_per_sequence"`
	Tempo                  int                       `json:"tempo"`
	Range                  coursedomain.NoteRange    `json:"range"`
	RotateEveryQuestions   *int                      `json:"rotate_every_questions"`
	MelodyStyle            coursedomain.MelodyStyle  `json:"melody_style"`
	TuneInconsistencyCents int                       `json:"tune_inconsistency_cents"`
}

type seededRelativeMelodyDefinition struct {
	Config  seededRelativeRandomMelodyConfig `json:"config"`
	Context *coursedomain.DegreeContext      `json:"context"`
}

// SeedRandomTonic creates or resets the deterministic random-tonic course. Its
// relative Major scale starts on a random tonic and rotates every five
// questions; progression groups cumulatively introduce chromatic degrees.
func SeedRandomTonic(ctx context.Context, db *gorm.DB, catalogConfig config.CatalogConfig) (RandomTonicResult, error) {
	if err := createRandomTonicFolders(catalogConfig); err != nil {
		return RandomTonicResult{}, err
	}
	scanner, err := catalog.NewScanner(db, catalogConfig)
	if err != nil {
		return RandomTonicResult{}, err
	}
	if _, err := scanner.Scan(ctx); err != nil {
		return RandomTonicResult{}, fmt.Errorf("scan seeded random-tonic folders: %w", err)
	}

	var result RandomTonicResult
	err = db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		courseGroup, err := findSeedLibraryGroup(tx, randomTonicCoursePath)
		if err != nil {
			return err
		}
		modeGroup, err := findSeedLibraryGroup(tx, randomTonicModePath)
		if err != nil {
			return err
		}
		if err := upsertRandomTonicCourse(tx, courseGroup); err != nil {
			return err
		}
		mode, err := upsertRandomTonicMode(tx, courseGroup.ID, modeGroup.ID)
		if err != nil {
			return err
		}

		activeDegrees := majorDegreeSet()
		for groupIndex, spec := range randomTonicGroupSpecs {
			if spec.AddedDegree != nil {
				activeDegrees[*spec.AddedDegree] = true
			}
			libraryGroup, err := findSeedLibraryGroup(tx, filepath.ToSlash(filepath.Join(randomTonicModePath, spec.Slug)))
			if err != nil {
				return err
			}
			progression, err := upsertRandomTonicProgressionGroup(tx, mode.ID, libraryGroup.ID, spec, groupIndex)
			if err != nil {
				return err
			}
			for levelIndex, tempo := 0, 60; tempo <= 160; levelIndex, tempo = levelIndex+1, tempo+10 {
				definition, err := randomTonicDefinition(activeDegrees, groupIndex != 0, tempo)
				if err != nil {
					return err
				}
				sectionID := libraryGroup.ID
				level := model.CourseLevel{
					ID:                    fmt.Sprintf("seed-random-tonic-%s-%03d-bpm", spec.Slug, tempo),
					ProgressionGroupID:    progression.ID,
					Name:                  fmt.Sprintf("%s — %d BPM", spec.Name, tempo),
					Source:                "built_in",
					Definition:            model.JSONDocument(definition),
					SortOrder:             levelIndex,
					IsPublic:              true,
					SectionLibraryGroupID: &sectionID,
				}
				if err := upsertSeededLevel(tx, level); err != nil {
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
		return RandomTonicResult{}, err
	}
	return result, nil
}

func randomTonicDefinition(active map[coursedomain.Degree]bool, chromatic bool, tempo int) (json.RawMessage, error) {
	degreeOrder := []coursedomain.Degree{
		"D1", "DF2", "D2", "DF3", "D3", "D4", "DS4", "D5", "DF6", "D6", "DF7", "D7",
	}
	states := make([]coursedomain.DegreeState, 0, len(degreeOrder))
	for _, degree := range degreeOrder {
		states = append(states, coursedomain.DegreeState{Degree: degree, Active: active[degree]})
	}
	questions, notesPerSequence, rotateEveryQuestions := 20, 5, 5
	definition := seededRelativeMelodyDefinition{
		Config: seededRelativeRandomMelodyConfig{
			Type: "random",
			ScaleConfig: seededRelativeScaleConfig{
				Type: "relative",
				RelativeScaleConfig: coursedomain.RelativeScaleConfig{
					ScaleType: "Major", DegreeStates: states,
				},
			},
			QuestionsNumber: &questions, NotesPerSequence: &notesPerSequence,
			Tempo: tempo,
			Range: coursedomain.NoteRange{
				From: coursedomain.Note{MIDIIndex: 36},
				To:   coursedomain.Note{MIDIIndex: 96},
			},
			RotateEveryQuestions: &rotateEveryQuestions,
			MelodyStyle:          steadyQuartersStyle(),
		},
		Context: tonicDroneContext("seed-random-tonic-drone", "Random tonic drone"),
	}
	if chromatic {
		definition.Config.ScaleConfig.ScaleType = "Chromatic"
	}
	raw, err := json.Marshal(definition)
	if err != nil {
		return nil, err
	}
	if _, err := coursedomain.ValidateDefinition(coursedomain.ModeMelodies, raw, true); err != nil {
		return nil, fmt.Errorf("validate random-tonic level at %d BPM: %w", tempo, err)
	}
	return raw, nil
}

func majorDegreeSet() map[coursedomain.Degree]bool {
	return map[coursedomain.Degree]bool{
		"D1": true, "D2": true, "D3": true, "D4": true,
		"D5": true, "D6": true, "D7": true,
	}
}

func createRandomTonicFolders(catalogConfig config.CatalogConfig) error {
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
		{
			Path: randomTonicCoursePath, Name: "Random tonic",
			Description: "Progressive melody training over rotating random Major tonics.", SortOrder: randomTonicSortOrder,
		},
		{
			Path: randomTonicModePath, Name: "Melodies",
			Description: "Generated random-tonic melody progressions.", SortOrder: 0,
		},
	}
	for index, spec := range randomTonicGroupSpecs {
		folders = append(folders, struct {
			Path        string
			Name        string
			Description string
			SortOrder   int
		}{
			Path: filepath.ToSlash(filepath.Join(randomTonicModePath, spec.Slug)), Name: spec.Name,
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

func upsertRandomTonicCourse(db *gorm.DB, libraryGroup model.LibraryGroup) error {
	values := map[string]any{
		"deleted_at": nil, "name": "Random tonic",
		"description": "Progressive melody training over rotating random Major tonics.", "author": "LdV",
		"is_public": true, "sort_order": randomTonicSortOrder, "structure_source": model.CourseStructureManaged,
	}
	var existing model.Course
	err := db.Unscoped().Where("id = ?", libraryGroup.ID).First(&existing).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		course := model.Course{
			ID: libraryGroup.ID, Name: "Random tonic",
			Description: "Progressive melody training over rotating random Major tonics.",
			Author:      "LdV", IsPublic: true, SortOrder: randomTonicSortOrder, StructureSource: model.CourseStructureManaged,
		}
		return db.Omit("Modes").Create(&course).Error
	}
	if err != nil {
		return err
	}
	return db.Unscoped().Model(&model.Course{}).Where("id = ?", libraryGroup.ID).Updates(values).Error
}

func upsertRandomTonicMode(db *gorm.DB, courseID uint, libraryGroupID uint) (model.CourseMode, error) {
	var mode model.CourseMode
	err := db.Unscoped().Where("course_id = ? AND mode = ?", courseID, coursedomain.ModeMelodies).First(&mode).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		mode = model.CourseMode{
			CourseID: courseID, Mode: coursedomain.ModeMelodies, LibraryGroupID: libraryGroupID,
			Name: "Melodies", Description: "Generated random-tonic melody progressions.",
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
		"description": "Generated random-tonic melody progressions.", "sort_order": 0,
	}).Error; err != nil {
		return model.CourseMode{}, err
	}
	mode.LibraryGroupID, mode.Name, mode.Description, mode.SortOrder = libraryGroupID, "Melodies", "Generated random-tonic melody progressions.", 0
	return mode, nil
}

func upsertRandomTonicProgressionGroup(db *gorm.DB, modeID uint, libraryGroupID uint, spec randomTonicGroupSpec, sortOrder int) (model.CourseProgressionGroup, error) {
	publicID := "seed-random-tonic-" + spec.Slug
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

func degreePointer(value coursedomain.Degree) *coursedomain.Degree { return &value }
