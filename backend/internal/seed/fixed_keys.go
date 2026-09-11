package seed

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gorm.io/gorm"

	"shuuen-backend/internal/catalog"
	"shuuen-backend/internal/config"
	coursedomain "shuuen-backend/internal/course"
	"shuuen-backend/internal/model"
)

const randomTonicSortOrder = 26

type FixedKeysResult struct {
	CourseIDs []uint
	Courses   int
	Groups    int
	Levels    int
}

type fixedKeySpec struct {
	Path        string
	Name        string
	TonicName   string
	Root        coursedomain.Pitch
	RootIndex   int
	ScaleType   string
	SortOrder   int
	Description string
}

// fixedKeySpecs deliberately alternates Major and its relative Natural Minor.
// After the zero-accidental pair, each sharp pair is followed by the flat pair
// with the same number of accidentals. F-sharp and G-flat Major remain separate
// courses even though the backend represents their roots by one pitch class.
var fixedKeySpecs = []fixedKeySpec{
	{Path: cTonicCoursePath, Name: "C tonic", TonicName: "C", Root: coursedomain.PitchC, RootIndex: 0, ScaleType: "Major", SortOrder: 0, Description: "Progressive melody training over a C tonic."},
	{Path: "a-natural-minor", Name: "A natural minor", TonicName: "A", Root: coursedomain.PitchA, RootIndex: 9, ScaleType: "NaturalMinor", SortOrder: 1},
	{Path: "g-major", Name: "G major", TonicName: "G", Root: coursedomain.PitchG, RootIndex: 7, ScaleType: "Major", SortOrder: 2},
	{Path: "e-natural-minor", Name: "E natural minor", TonicName: "E", Root: coursedomain.PitchE, RootIndex: 4, ScaleType: "NaturalMinor", SortOrder: 3},
	{Path: "f-major", Name: "F major", TonicName: "F", Root: coursedomain.PitchF, RootIndex: 5, ScaleType: "Major", SortOrder: 4},
	{Path: "d-natural-minor", Name: "D natural minor", TonicName: "D", Root: coursedomain.PitchD, RootIndex: 2, ScaleType: "NaturalMinor", SortOrder: 5},
	{Path: "d-major", Name: "D major", TonicName: "D", Root: coursedomain.PitchD, RootIndex: 2, ScaleType: "Major", SortOrder: 6},
	{Path: "b-natural-minor", Name: "B natural minor", TonicName: "B", Root: coursedomain.PitchB, RootIndex: 11, ScaleType: "NaturalMinor", SortOrder: 7},
	{Path: "b-flat-major", Name: "B♭ major", TonicName: "B♭", Root: coursedomain.PitchASharp, RootIndex: 10, ScaleType: "Major", SortOrder: 8},
	{Path: "g-natural-minor", Name: "G natural minor", TonicName: "G", Root: coursedomain.PitchG, RootIndex: 7, ScaleType: "NaturalMinor", SortOrder: 9},
	{Path: "a-major", Name: "A major", TonicName: "A", Root: coursedomain.PitchA, RootIndex: 9, ScaleType: "Major", SortOrder: 10},
	{Path: "f-sharp-natural-minor", Name: "F♯ natural minor", TonicName: "F♯", Root: coursedomain.PitchFSharp, RootIndex: 6, ScaleType: "NaturalMinor", SortOrder: 11},
	{Path: "e-flat-major", Name: "E♭ major", TonicName: "E♭", Root: coursedomain.PitchDSharp, RootIndex: 3, ScaleType: "Major", SortOrder: 12},
	{Path: "c-natural-minor", Name: "C natural minor", TonicName: "C", Root: coursedomain.PitchC, RootIndex: 0, ScaleType: "NaturalMinor", SortOrder: 13},
	{Path: "e-major", Name: "E major", TonicName: "E", Root: coursedomain.PitchE, RootIndex: 4, ScaleType: "Major", SortOrder: 14},
	{Path: "c-sharp-natural-minor", Name: "C♯ natural minor", TonicName: "C♯", Root: coursedomain.PitchCSharp, RootIndex: 1, ScaleType: "NaturalMinor", SortOrder: 15},
	{Path: "a-flat-major", Name: "A♭ major", TonicName: "A♭", Root: coursedomain.PitchGSharp, RootIndex: 8, ScaleType: "Major", SortOrder: 16},
	{Path: "f-natural-minor", Name: "F natural minor", TonicName: "F", Root: coursedomain.PitchF, RootIndex: 5, ScaleType: "NaturalMinor", SortOrder: 17},
	{Path: "b-major", Name: "B major", TonicName: "B", Root: coursedomain.PitchB, RootIndex: 11, ScaleType: "Major", SortOrder: 18},
	{Path: "g-sharp-natural-minor", Name: "G♯ natural minor", TonicName: "G♯", Root: coursedomain.PitchGSharp, RootIndex: 8, ScaleType: "NaturalMinor", SortOrder: 19},
	{Path: "d-flat-major", Name: "D♭ major", TonicName: "D♭", Root: coursedomain.PitchCSharp, RootIndex: 1, ScaleType: "Major", SortOrder: 20},
	{Path: "b-flat-natural-minor", Name: "B♭ natural minor", TonicName: "B♭", Root: coursedomain.PitchASharp, RootIndex: 10, ScaleType: "NaturalMinor", SortOrder: 21},
	{Path: "f-sharp-major", Name: "F♯ major", TonicName: "F♯", Root: coursedomain.PitchFSharp, RootIndex: 6, ScaleType: "Major", SortOrder: 22},
	{Path: "d-sharp-natural-minor", Name: "D♯ natural minor", TonicName: "D♯", Root: coursedomain.PitchDSharp, RootIndex: 3, ScaleType: "NaturalMinor", SortOrder: 23},
	{Path: "g-flat-major", Name: "G♭ major", TonicName: "G♭", Root: coursedomain.PitchFSharp, RootIndex: 6, ScaleType: "Major", SortOrder: 24},
	{Path: "e-flat-natural-minor", Name: "E♭ natural minor", TonicName: "E♭", Root: coursedomain.PitchDSharp, RootIndex: 3, ScaleType: "NaturalMinor", SortOrder: 25},
}

type fixedKeyAddition struct {
	Slug        string
	DegreeLabel string
	Degree      int
	Offset      int
}

type fixedKeyGroupSpec struct {
	Slug        string
	Name        string
	Description string
	AddedPitch  *coursedomain.Pitch
}

var majorKeyAdditions = []fixedKeyAddition{
	{Slug: "plus-sharp-4", DegreeLabel: "♯4", Degree: 4, Offset: 6},
	{Slug: "plus-flat-2", DegreeLabel: "♭2", Degree: 2, Offset: 1},
	{Slug: "plus-flat-6", DegreeLabel: "♭6", Degree: 6, Offset: 8},
	{Slug: "plus-flat-3", DegreeLabel: "♭3", Degree: 3, Offset: 3},
	{Slug: "plus-flat-7", DegreeLabel: "♭7", Degree: 7, Offset: 10},
}

var naturalMinorKeyAdditions = []fixedKeyAddition{
	{Slug: "plus-sharp-4", DegreeLabel: "♯4", Degree: 4, Offset: 6},
	{Slug: "plus-flat-2", DegreeLabel: "♭2", Degree: 2, Offset: 1},
	{Slug: "plus-natural-6", DegreeLabel: "♮6", Degree: 6, Offset: 9},
	{Slug: "plus-natural-3", DegreeLabel: "♮3", Degree: 3, Offset: 4},
	{Slug: "plus-natural-7", DegreeLabel: "♮7", Degree: 7, Offset: 11},
}

// SeedAllFixedKeys creates the complete fixed-tonic collection. The first
// course retains the established C-tonic paths and public ids.
func SeedAllFixedKeys(ctx context.Context, db *gorm.DB, catalogConfig config.CatalogConfig) (FixedKeysResult, error) {
	if err := createCTonicFolders(catalogConfig); err != nil {
		return FixedKeysResult{}, err
	}
	otherKeys := fixedKeySpecs[1:]
	if err := createFixedKeyFolders(catalogConfig, otherKeys); err != nil {
		return FixedKeysResult{}, err
	}
	scanner, err := catalog.NewScanner(db, catalogConfig)
	if err != nil {
		return FixedKeysResult{}, err
	}
	if _, err := scanner.Scan(ctx); err != nil {
		return FixedKeysResult{}, fmt.Errorf("scan seeded fixed-key folders: %w", err)
	}

	var result FixedKeysResult
	err = db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		cResult, err := seedCTonicCourse(tx)
		if err != nil {
			return err
		}
		result.CourseIDs = append(result.CourseIDs, cResult.CourseID)
		result.Courses++
		result.Groups += cResult.Groups
		result.Levels += cResult.Levels
		for _, key := range otherKeys {
			courseID, groups, levels, err := seedFixedKeyCourse(tx, key)
			if err != nil {
				return err
			}
			result.CourseIDs = append(result.CourseIDs, courseID)
			result.Courses++
			result.Groups += groups
			result.Levels += levels
		}
		return nil
	})
	if err != nil {
		return FixedKeysResult{}, err
	}
	return result, nil
}

func seedFixedKeyCourse(db *gorm.DB, key fixedKeySpec) (uint, int, int, error) {
	courseGroup, err := findSeedLibraryGroup(db, key.Path)
	if err != nil {
		return 0, 0, 0, err
	}
	modePath := filepath.ToSlash(filepath.Join(key.Path, "melodies"))
	modeGroup, err := findSeedLibraryGroup(db, modePath)
	if err != nil {
		return 0, 0, 0, err
	}
	if err := upsertFixedKeyCourse(db, courseGroup, key); err != nil {
		return 0, 0, 0, err
	}
	mode, err := upsertFixedKeyMode(db, courseGroup.ID, modeGroup.ID)
	if err != nil {
		return 0, 0, 0, err
	}

	activePitches := fixedKeyPitchSet(key)
	groupSpecs := fixedKeyGroupSpecs(key)
	levelCount := 0
	for groupIndex, groupSpec := range groupSpecs {
		if groupSpec.AddedPitch != nil {
			activePitches[*groupSpec.AddedPitch] = true
		}
		libraryPath := filepath.ToSlash(filepath.Join(modePath, groupSpec.Slug))
		libraryGroup, err := findSeedLibraryGroup(db, libraryPath)
		if err != nil {
			return 0, 0, 0, err
		}
		progression, err := upsertFixedKeyProgressionGroup(db, mode.ID, libraryGroup.ID, key, groupSpec, groupIndex)
		if err != nil {
			return 0, 0, 0, err
		}
		for levelIndex, tempo := 0, 60; tempo <= 160; levelIndex, tempo = levelIndex+1, tempo+10 {
			definition, err := fixedKeyDefinition(key, activePitches, groupIndex != 0, tempo)
			if err != nil {
				return 0, 0, 0, err
			}
			sectionID := libraryGroup.ID
			level := model.CourseLevel{
				ID:                    fmt.Sprintf("seed-%s-%s-%03d-bpm", key.Path, groupSpec.Slug, tempo),
				ProgressionGroupID:    progression.ID,
				Name:                  fmt.Sprintf("%s — %d BPM", groupSpec.Name, tempo),
				Source:                "built_in",
				Definition:            model.JSONDocument(definition),
				SortOrder:             levelIndex,
				IsPublic:              true,
				SectionLibraryGroupID: &sectionID,
			}
			if err := upsertSeededLevel(db, level); err != nil {
				return 0, 0, 0, err
			}
			levelCount++
		}
	}
	return courseGroup.ID, len(groupSpecs), levelCount, nil
}

func fixedKeyDefinition(key fixedKeySpec, active map[coursedomain.Pitch]bool, chromatic bool, tempo int) (json.RawMessage, error) {
	states := make([]coursedomain.PitchState, 0, len(fixedPitchOrder))
	for _, pitch := range fixedPitchOrder {
		states = append(states, coursedomain.PitchState{Pitch: pitch, Active: active[pitch]})
	}
	questions, notesPerSequence := 20, 8
	definition := seededMelodyDefinition{
		Config: seededRandomMelodyConfig{
			Type: "random",
			ScaleConfig: seededAbsoluteScaleConfig{
				Type: "absolute",
				AbsoluteScaleConfig: coursedomain.AbsoluteScaleConfig{
					Root: key.Root, ScaleType: key.ScaleType, PitchStates: states,
				},
			},
			QuestionsNumber:  &questions,
			NotesPerSequence: &notesPerSequence,
			Tempo:            tempo,
			Range: coursedomain.NoteRange{
				From: coursedomain.Note{MIDIIndex: 36},
				To:   coursedomain.Note{MIDIIndex: 96},
			},
			MelodyStyle: steadyQuartersStyle(),
		},
		Context: fixedKeyContext(key),
	}
	if chromatic {
		definition.Config.ScaleConfig.ScaleType = "Chromatic"
	}
	raw, err := json.Marshal(definition)
	if err != nil {
		return nil, err
	}
	if _, err := coursedomain.ValidateDefinition(coursedomain.ModeMelodies, raw, true); err != nil {
		return nil, fmt.Errorf("validate %s level at %d BPM: %w", key.Name, tempo, err)
	}
	return raw, nil
}

func fixedKeyContext(key fixedKeySpec) *coursedomain.DegreeContext {
	context := tonicDroneContext("seed-"+key.Path+"-drone", key.TonicName+" tonic drone")
	if key.ScaleType == "NaturalMinor" {
		context.Nodes[0].SetupMelody.Melody.ExtraDegrees[0].Degree = "DF3"
	}
	return context
}

var fixedPitchOrder = []coursedomain.Pitch{
	coursedomain.PitchC, coursedomain.PitchCSharp, coursedomain.PitchD, coursedomain.PitchDSharp,
	coursedomain.PitchE, coursedomain.PitchF, coursedomain.PitchFSharp, coursedomain.PitchG,
	coursedomain.PitchGSharp, coursedomain.PitchA, coursedomain.PitchASharp, coursedomain.PitchB,
}

func fixedKeyPitchSet(key fixedKeySpec) map[coursedomain.Pitch]bool {
	intervals := []int{0, 2, 4, 5, 7, 9, 11}
	if key.ScaleType == "NaturalMinor" {
		intervals = []int{0, 2, 3, 5, 7, 8, 10}
	}
	result := make(map[coursedomain.Pitch]bool, len(intervals))
	for _, interval := range intervals {
		result[pitchAt(key.RootIndex+interval)] = true
	}
	return result
}

func fixedKeyGroupSpecs(key fixedKeySpec) []fixedKeyGroupSpec {
	result := []fixedKeyGroupSpec{{
		Slug: "diatonic", Name: key.Name,
		Description: fmt.Sprintf("The seven diatonic notes of %s.", key.Name),
	}}
	additions := majorKeyAdditions
	if key.ScaleType == "NaturalMinor" {
		additions = naturalMinorKeyAdditions
	}
	addedNames := make([]string, 0, len(additions))
	for index, addition := range additions {
		pitch := pitchAt(key.RootIndex + addition.Offset)
		pitchName := spellScaleDegree(key, addition.Degree, addition.Offset)
		addedNames = append(addedNames, pitchName)
		description := fmt.Sprintf("%s with %s (%s) added.", key.Name, strings.Join(addedNames, ", "), addition.DegreeLabel)
		if index == len(additions)-1 {
			description = fmt.Sprintf("The complete chromatic scale, reached by adding %s (%s) to the preceding pitches.", pitchName, addition.DegreeLabel)
		}
		result = append(result, fixedKeyGroupSpec{
			Slug: addition.Slug, Name: pitchName, Description: description, AddedPitch: pitchPointer(pitch),
		})
	}
	return result
}

func pitchAt(index int) coursedomain.Pitch {
	return fixedPitchOrder[((index%len(fixedPitchOrder))+len(fixedPitchOrder))%len(fixedPitchOrder)]
}

func spellScaleDegree(key fixedKeySpec, degree int, offset int) string {
	letters := []string{"C", "D", "E", "F", "G", "A", "B"}
	naturalPitches := []int{0, 2, 4, 5, 7, 9, 11}
	targetLetterIndex := 0
	for index, letter := range letters {
		if strings.HasPrefix(key.TonicName, letter) {
			targetLetterIndex = (index + degree - 1) % len(letters)
			break
		}
	}
	targetPitch := (key.RootIndex + offset) % len(fixedPitchOrder)
	difference := (targetPitch - naturalPitches[targetLetterIndex] + len(fixedPitchOrder)) % len(fixedPitchOrder)
	accidental := map[int]string{0: "", 1: "♯", 2: "𝄪", 10: "𝄫", 11: "♭"}[difference]
	if accidental == "" && difference != 0 {
		return string(pitchAt(targetPitch))
	}
	return letters[targetLetterIndex] + accidental
}

func createFixedKeyFolders(catalogConfig config.CatalogConfig, keys []fixedKeySpec) error {
	if catalogConfig.FolderMetadataFile == "" || filepath.Base(catalogConfig.FolderMetadataFile) != catalogConfig.FolderMetadataFile {
		return errors.New("folder metadata file must be a file name")
	}
	root, err := filepath.Abs(catalogConfig.Root)
	if err != nil {
		return err
	}
	public := true
	for _, key := range keys {
		modePath := filepath.ToSlash(filepath.Join(key.Path, "melodies"))
		folders := []struct {
			Path        string
			Name        string
			Description string
			SortOrder   int
		}{
			{Path: key.Path, Name: key.Name, Description: fixedKeyDescription(key), SortOrder: key.SortOrder},
			{Path: modePath, Name: "Melodies", Description: "Generated melody progressions.", SortOrder: 0},
		}
		for index, group := range fixedKeyGroupSpecs(key) {
			folders = append(folders, struct {
				Path        string
				Name        string
				Description string
				SortOrder   int
			}{
				Path: filepath.ToSlash(filepath.Join(modePath, group.Slug)), Name: group.Name,
				Description: group.Description, SortOrder: index,
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
	}
	return nil
}

func fixedKeyDescription(key fixedKeySpec) string {
	if key.Description != "" {
		return key.Description
	}
	return fmt.Sprintf("Progressive melody training in %s.", key.Name)
}

func upsertFixedKeyCourse(db *gorm.DB, libraryGroup model.LibraryGroup, key fixedKeySpec) error {
	values := map[string]any{
		"deleted_at": nil, "name": key.Name, "description": fixedKeyDescription(key), "author": "LdV",
		"is_public": true, "sort_order": key.SortOrder, "structure_source": model.CourseStructureManaged,
	}
	var existing model.Course
	err := db.Unscoped().Where("id = ?", libraryGroup.ID).First(&existing).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		course := model.Course{
			ID: libraryGroup.ID, Name: key.Name, Description: fixedKeyDescription(key), Author: "LdV",
			IsPublic: true, SortOrder: key.SortOrder, StructureSource: model.CourseStructureManaged,
		}
		return db.Omit("Modes").Create(&course).Error
	}
	if err != nil {
		return err
	}
	return db.Unscoped().Model(&model.Course{}).Where("id = ?", libraryGroup.ID).Updates(values).Error
}

func upsertFixedKeyMode(db *gorm.DB, courseID uint, libraryGroupID uint) (model.CourseMode, error) {
	return upsertCTonicMode(db, courseID, libraryGroupID)
}

func upsertFixedKeyProgressionGroup(db *gorm.DB, modeID uint, libraryGroupID uint, key fixedKeySpec, spec fixedKeyGroupSpec, sortOrder int) (model.CourseProgressionGroup, error) {
	publicID := "seed-" + key.Path + "-" + spec.Slug
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
