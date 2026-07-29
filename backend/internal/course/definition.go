package course

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"
)

const (
	ModeSingles  = "singles"
	ModeMelodies = "melodies"
	ModeChords   = "chords"
)

var validModes = map[string]bool{
	ModeSingles: true, ModeMelodies: true, ModeChords: true,
}

func NormalizeMode(value string) string { return strings.ToLower(strings.TrimSpace(value)) }

func IsValidMode(value string) bool { return validModes[NormalizeMode(value)] }

func DefaultModeName(mode string) string {
	switch NormalizeMode(mode) {
	case ModeSingles:
		return "Singles"
	case ModeMelodies:
		return "Melodies"
	case ModeChords:
		return "Chords"
	default:
		return mode
	}
}

type Pitch string

const (
	PitchC      Pitch = "C"
	PitchCSharp Pitch = "CSharp"
	PitchD      Pitch = "D"
	PitchDSharp Pitch = "DSharp"
	PitchE      Pitch = "E"
	PitchF      Pitch = "F"
	PitchFSharp Pitch = "FSharp"
	PitchG      Pitch = "G"
	PitchGSharp Pitch = "GSharp"
	PitchA      Pitch = "A"
	PitchASharp Pitch = "ASharp"
	PitchB      Pitch = "B"
)

var validPitches = map[Pitch]bool{
	PitchC: true, PitchCSharp: true, PitchD: true, PitchDSharp: true,
	PitchE: true, PitchF: true, PitchFSharp: true, PitchG: true,
	PitchGSharp: true, PitchA: true, PitchASharp: true, PitchB: true,
}

type Degree string

var validDegrees = map[Degree]bool{
	"D1": true, "D5": true, "D2": true, "D6": true, "D3": true, "D7": true,
	"DS4": true, "DF2": true, "DF6": true, "DF3": true, "DF7": true, "D4": true,
}

type Note struct {
	MIDIIndex int `json:"midi_index"`
}

func (note Note) validate(field string) error {
	if note.MIDIIndex < 21 || note.MIDIIndex > 108 {
		return fmt.Errorf("%s.midi_index must be in the 88-key piano range 21..108", field)
	}
	return nil
}

type NoteRange struct {
	From Note `json:"from"`
	To   Note `json:"to"`
}

func (value NoteRange) validate(field string) error {
	if err := value.From.validate(field + ".from"); err != nil {
		return err
	}
	if err := value.To.validate(field + ".to"); err != nil {
		return err
	}
	if value.From.MIDIIndex > value.To.MIDIIndex {
		return fmt.Errorf("%s.from must not be higher than %s.to", field, field)
	}
	return nil
}

type PitchState struct {
	Pitch  Pitch `json:"pitch"`
	Active bool  `json:"active"`
}

type DegreeState struct {
	Degree Degree `json:"degree"`
	Active bool   `json:"active"`
}

type AbsoluteScaleConfig struct {
	Root        Pitch        `json:"root"`
	ScaleType   string       `json:"scale_type"`
	PitchStates []PitchState `json:"pitch_states"`
}

type RelativeScaleConfig struct {
	ScaleType    string        `json:"scale_type"`
	DegreeStates []DegreeState `json:"degree_states"`
}

var validScaleTypes = map[string]bool{
	"Major": true, "NaturalMinor": true, "Chromatic": true, "Custom": true,
}

func (scale AbsoluteScaleConfig) validate(field string) error {
	if !validPitches[scale.Root] {
		return fmt.Errorf("%s.root is invalid", field)
	}
	if !validScaleTypes[scale.ScaleType] {
		return fmt.Errorf("%s.scale_type is invalid", field)
	}
	if len(scale.PitchStates) == 0 {
		return fmt.Errorf("%s.pitch_states must not be empty", field)
	}
	active := 0
	seen := map[Pitch]bool{}
	for index, state := range scale.PitchStates {
		if !validPitches[state.Pitch] {
			return fmt.Errorf("%s.pitch_states[%d].pitch is invalid", field, index)
		}
		if seen[state.Pitch] {
			return fmt.Errorf("%s.pitch_states contains duplicate pitch %s", field, state.Pitch)
		}
		seen[state.Pitch] = true
		if state.Active {
			active++
		}
	}
	if active == 0 {
		return fmt.Errorf("%s must contain at least one active pitch", field)
	}
	return nil
}

func (scale RelativeScaleConfig) validate(field string) error {
	if !validScaleTypes[scale.ScaleType] {
		return fmt.Errorf("%s.scale_type is invalid", field)
	}
	if len(scale.DegreeStates) == 0 {
		return fmt.Errorf("%s.degree_states must not be empty", field)
	}
	active := 0
	seen := map[Degree]bool{}
	for index, state := range scale.DegreeStates {
		if !validDegrees[state.Degree] {
			return fmt.Errorf("%s.degree_states[%d].degree is invalid", field, index)
		}
		if seen[state.Degree] {
			return fmt.Errorf("%s.degree_states contains duplicate degree %s", field, state.Degree)
		}
		seen[state.Degree] = true
		if state.Active {
			active++
		}
	}
	if active == 0 {
		return fmt.Errorf("%s must contain at least one active degree", field)
	}
	return nil
}

type DegreeWithOctave struct {
	Degree Degree `json:"degree"`
	Octave int    `json:"octave"`
}

func (value DegreeWithOctave) validate(field string) error {
	if !validDegrees[value.Degree] {
		return fmt.Errorf("%s.degree is invalid", field)
	}
	if value.Octave < 0 || value.Octave > 8 {
		return fmt.Errorf("%s.octave must be between 0 and 8", field)
	}
	return nil
}

type DirectedDegree struct {
	Degree    Degree `json:"degree"`
	Direction string `json:"direction"`
}

type RelativeMelody struct {
	FirstDegree  DegreeWithOctave `json:"first_degree"`
	ExtraDegrees []DirectedDegree `json:"extra_degrees"`
}

type SetupMelody struct {
	Melody RelativeMelody `json:"melody"`
	Repeat string         `json:"repeat"`
}

type Sustain struct {
	Type       string `json:"type"`
	DurationMS *int64 `json:"duration_ms,omitempty"`
}

type ContextDuration struct {
	Type                string `json:"type"`
	DurationInQuestions *int   `json:"duration_in_questions,omitempty"`
}

type DegreeContextNode struct {
	FirstDegree       DegreeWithOctave `json:"first_degree"`
	ExtraDegrees      []Degree         `json:"extra_degrees"`
	Sustain           Sustain          `json:"sustain"`
	Duration          ContextDuration  `json:"duration"`
	SetupMelody       *SetupMelody     `json:"setup_melody"`
	RelativeDirection string           `json:"relative_direction"`
}

type DegreeContext struct {
	ID     string              `json:"id"`
	Source string              `json:"source"`
	Nodes  []DegreeContextNode `json:"nodes"`
	Name   *string             `json:"name"`
}

func (context DegreeContext) validate() error {
	if strings.TrimSpace(context.ID) == "" || len(context.ID) > 64 {
		return errors.New("context.id is required and must be at most 64 characters")
	}
	if !validContextSources[context.Source] {
		return errors.New("context.source is invalid")
	}
	if len(context.Nodes) == 0 {
		return errors.New("context.nodes must not be empty")
	}
	for index, node := range context.Nodes {
		field := fmt.Sprintf("context.nodes[%d]", index)
		if err := node.FirstDegree.validate(field + ".first_degree"); err != nil {
			return err
		}
		for degreeIndex, degree := range node.ExtraDegrees {
			if !validDegrees[degree] {
				return fmt.Errorf("%s.extra_degrees[%d] is invalid", field, degreeIndex)
			}
		}
		if node.RelativeDirection != "Up" && node.RelativeDirection != "Down" {
			return fmt.Errorf("%s.relative_direction must be Up or Down", field)
		}
		switch node.Sustain.Type {
		case "endless":
			if node.Sustain.DurationMS != nil {
				return fmt.Errorf("%s.sustain.duration_ms is only valid for finite sustain", field)
			}
		case "finite":
			if node.Sustain.DurationMS == nil || *node.Sustain.DurationMS <= 0 {
				return fmt.Errorf("%s.sustain.duration_ms must be positive", field)
			}
		default:
			return fmt.Errorf("%s.sustain.type is invalid", field)
		}
		switch node.Duration.Type {
		case "finite":
			if node.Duration.DurationInQuestions == nil || *node.Duration.DurationInQuestions <= 0 {
				return fmt.Errorf("%s.duration.duration_in_questions must be positive", field)
			}
		case "immediate", "endless", "same_as_scale_rotation":
			if node.Duration.DurationInQuestions != nil {
				return fmt.Errorf("%s.duration.duration_in_questions is only valid for finite duration", field)
			}
		default:
			return fmt.Errorf("%s.duration.type is invalid", field)
		}
		if node.SetupMelody != nil {
			if node.SetupMelody.Repeat != "Once" && node.SetupMelody.Repeat != "EveryTime" {
				return fmt.Errorf("%s.setup_melody.repeat is invalid", field)
			}
			if err := node.SetupMelody.Melody.FirstDegree.validate(field + ".setup_melody.melody.first_degree"); err != nil {
				return err
			}
			for stepIndex, step := range node.SetupMelody.Melody.ExtraDegrees {
				if !validDegrees[step.Degree] || (step.Direction != "Up" && step.Direction != "Down") {
					return fmt.Errorf("%s.setup_melody.melody.extra_degrees[%d] is invalid", field, stepIndex)
				}
			}
		}
	}
	return nil
}

var validContextSources = map[string]bool{
	"built_in": true, "user": true, "local": true, "imported": true,
}

type ChordFigure struct {
	Type        string `json:"type"`
	LadderSteps []int  `json:"ladder_steps,omitempty"`
}

type WeightedChordFigure struct {
	Figure ChordFigure `json:"figure"`
	Weight float64     `json:"weight"`
}

type ChordStyle struct {
	ID          string                `json:"id"`
	Name        string                `json:"name"`
	Description string                `json:"description"`
	Tier        string                `json:"tier"`
	Figures     []WeightedChordFigure `json:"figures"`
}

func (style ChordStyle) validate(field string) error {
	if strings.TrimSpace(style.ID) == "" || strings.TrimSpace(style.Name) == "" || !validStyleTier(style.Tier) {
		return fmt.Errorf("%s id, name, or tier is invalid", field)
	}
	if len(style.Figures) == 0 {
		return fmt.Errorf("%s.figures must not be empty", field)
	}
	for index, weighted := range style.Figures {
		if weighted.Weight <= 0 {
			return fmt.Errorf("%s.figures[%d].weight must be positive", field, index)
		}
		switch weighted.Figure.Type {
		case "free_pick":
			if len(weighted.Figure.LadderSteps) != 0 {
				return fmt.Errorf("%s.figures[%d].figure.ladder_steps is invalid for free_pick", field, index)
			}
		case "stacked":
			steps := weighted.Figure.LadderSteps
			if len(steps) < 2 || steps[0] != 0 {
				return fmt.Errorf("%s.figures[%d].figure.ladder_steps must start at 0 and contain at least two steps", field, index)
			}
			for step := 1; step < len(steps); step++ {
				if steps[step] <= steps[step-1] {
					return fmt.Errorf("%s.figures[%d].figure.ladder_steps must strictly increase", field, index)
				}
			}
		default:
			return fmt.Errorf("%s.figures[%d].figure.type is invalid", field, index)
		}
	}
	return nil
}

type RhythmFigure struct {
	Values  []string `json:"values"`
	Contour []*int   `json:"contour"`
	Ladder  string   `json:"ladder"`
}

type WeightedFigure struct {
	Figure RhythmFigure `json:"figure"`
	Weight float64      `json:"weight"`
}

type NoteWeights struct {
	IntervalWeights []float64          `json:"interval_weights"`
	DegreeWeights   map[Degree]float64 `json:"degree_weights"`
	ChordToneBoost  float64            `json:"chord_tone_boost"`
}

type MelodyStyle struct {
	ID          string           `json:"id"`
	Name        string           `json:"name"`
	Description string           `json:"description"`
	Tier        string           `json:"tier"`
	Figures     []WeightedFigure `json:"figures"`
	NoteWeights NoteWeights      `json:"note_weights"`
}

var validNoteValues = map[string]bool{
	"Whole": true, "Half": true, "DottedQuarter": true, "Quarter": true,
	"Eighth": true, "Sixteenth": true,
}

func (style MelodyStyle) validate(field string) error {
	if strings.TrimSpace(style.ID) == "" || strings.TrimSpace(style.Name) == "" || !validStyleTier(style.Tier) {
		return fmt.Errorf("%s id, name, or tier is invalid", field)
	}
	if len(style.Figures) == 0 {
		return fmt.Errorf("%s.figures must not be empty", field)
	}
	for index, weighted := range style.Figures {
		if weighted.Weight <= 0 || len(weighted.Figure.Values) == 0 {
			return fmt.Errorf("%s.figures[%d] has an invalid weight or empty values", field, index)
		}
		for valueIndex, value := range weighted.Figure.Values {
			if !validNoteValues[value] {
				return fmt.Errorf("%s.figures[%d].figure.values[%d] is invalid", field, index, valueIndex)
			}
		}
		if len(weighted.Figure.Contour) != 0 && len(weighted.Figure.Contour) != len(weighted.Figure.Values)-1 {
			return fmt.Errorf("%s.figures[%d].figure.contour must be empty or contain one item per note gap", field, index)
		}
		if weighted.Figure.Ladder != "Scale" && weighted.Figure.Ladder != "Chord" {
			return fmt.Errorf("%s.figures[%d].figure.ladder is invalid", field, index)
		}
	}
	for index, weight := range style.NoteWeights.IntervalWeights {
		if weight <= 0 {
			return fmt.Errorf("%s.note_weights.interval_weights[%d] must be positive", field, index)
		}
	}
	for degree, weight := range style.NoteWeights.DegreeWeights {
		if !validDegrees[degree] || weight <= 0 {
			return fmt.Errorf("%s.note_weights.degree_weights contains an invalid degree or weight", field)
		}
	}
	if style.NoteWeights.ChordToneBoost <= 0 {
		return fmt.Errorf("%s.note_weights.chord_tone_boost must be positive", field)
	}
	return nil
}

func validStyleTier(value string) bool {
	return value == "Beginner" || value == "Intermediate" || value == "Advanced"
}

type MIDIFileReference struct {
	Type      string `json:"type"`
	MelodyID  *uint  `json:"melody_id,omitempty"`
	VariantID *uint  `json:"variant_id,omitempty"`
	Path      string `json:"path,omitempty"`
	FileName  string `json:"file_name"`
}

type MIDIReferences struct {
	File *MIDIFileReference
}

type SinglesLevelDefinition struct {
	LevelConfig     json.RawMessage `json:"level_config"`
	Context         *DegreeContext  `json:"context"`
	QuestionsNumber *int            `json:"questions_number"`
	Range           *NoteRange      `json:"range"`
}

type MelodiesLevelDefinition struct {
	Config  json.RawMessage `json:"config"`
	Context *DegreeContext  `json:"context"`
}

type ChordSizeRange struct {
	Min int `json:"min"`
	Max int `json:"max"`
}

type ChordsLevelDefinition struct {
	LevelConfig     json.RawMessage `json:"level_config"`
	Context         *DegreeContext  `json:"context"`
	QuestionsNumber *int            `json:"questions_number"`
	Range           *NoteRange      `json:"range"`
	ChordSize       *ChordSizeRange `json:"chord_size"`
	SustainNotes    *bool           `json:"sustain_notes"`
	AnswerOrder     string          `json:"answer_order"`
}

type configType struct {
	Type string `json:"type"`
}

type singlesAbsoluteConfig struct {
	Type                   string                `json:"type"`
	Scales                 []AbsoluteScaleConfig `json:"scales"`
	RotateEveryQuestions   *int                  `json:"rotate_every_questions"`
	TuneInconsistencyCents int                   `json:"tune_inconsistency_cents"`
}

type singlesRelativeConfig struct {
	Type                   string              `json:"type"`
	ScaleConfig            RelativeScaleConfig `json:"scale_config"`
	RotateEveryQuestions   *int                `json:"rotate_every_questions"`
	TuneInconsistencyCents int                 `json:"tune_inconsistency_cents"`
}

type chordsAbsoluteConfig struct {
	Type                 string                `json:"type"`
	Scales               []AbsoluteScaleConfig `json:"scales"`
	RotateEveryQuestions *int                  `json:"rotate_every_questions"`
	ChordStyle           *ChordStyle           `json:"chord_style"`
}

type chordsRelativeConfig struct {
	Type                 string              `json:"type"`
	ScaleConfig          RelativeScaleConfig `json:"scale_config"`
	RotateEveryQuestions *int                `json:"rotate_every_questions"`
	ChordStyle           *ChordStyle         `json:"chord_style"`
}

type melodyRandomConfig struct {
	Type                   string          `json:"type"`
	ScaleConfig            json.RawMessage `json:"scale_config"`
	QuestionsNumber        *int            `json:"questions_number"`
	NotesPerSequence       *int            `json:"notes_per_sequence"`
	Tempo                  int             `json:"tempo"`
	Range                  NoteRange       `json:"range"`
	RotateEveryQuestions   *int            `json:"rotate_every_questions"`
	MelodyStyle            *MelodyStyle    `json:"melody_style"`
	TuneInconsistencyCents int             `json:"tune_inconsistency_cents"`
}

type melodyMIDIConfig struct {
	Type                  string            `json:"type"`
	File                  MIDIFileReference `json:"file"`
	UseOriginalVelocities bool              `json:"use_original_velocities"`
}

type scaleType struct {
	Type string `json:"type"`
}

type absoluteScaleEnvelope struct {
	Type string `json:"type"`
	AbsoluteScaleConfig
}

type relativeScaleEnvelope struct {
	Type string `json:"type"`
	RelativeScaleConfig
}

func ValidateDefinition(mode string, raw json.RawMessage, public bool) (MIDIReferences, error) {
	if len(raw) == 0 || !json.Valid(raw) || string(raw) == "null" {
		return MIDIReferences{}, errors.New("definition must be a JSON object")
	}
	switch NormalizeMode(mode) {
	case ModeSingles:
		var definition SinglesLevelDefinition
		if err := json.Unmarshal(raw, &definition); err != nil {
			return MIDIReferences{}, errors.New("invalid singles definition")
		}
		if err := validateQuestions(definition.QuestionsNumber, "definition.questions_number"); err != nil {
			return MIDIReferences{}, err
		}
		if definition.Range == nil {
			return MIDIReferences{}, errors.New("definition.range is required")
		}
		if err := definition.Range.validate("definition.range"); err != nil {
			return MIDIReferences{}, err
		}
		if definition.Context != nil {
			if err := definition.Context.validate(); err != nil {
				return MIDIReferences{}, err
			}
		}
		return MIDIReferences{}, validateSinglesConfig(definition.LevelConfig)
	case ModeMelodies:
		var definition MelodiesLevelDefinition
		if err := json.Unmarshal(raw, &definition); err != nil {
			return MIDIReferences{}, errors.New("invalid melodies definition")
		}
		if definition.Context != nil {
			if err := definition.Context.validate(); err != nil {
				return MIDIReferences{}, err
			}
		}
		return validateMelodyConfig(definition.Config, public)
	case ModeChords:
		var definition ChordsLevelDefinition
		if err := json.Unmarshal(raw, &definition); err != nil {
			return MIDIReferences{}, errors.New("invalid chords definition")
		}
		if err := validateQuestions(definition.QuestionsNumber, "definition.questions_number"); err != nil {
			return MIDIReferences{}, err
		}
		if definition.Range == nil {
			return MIDIReferences{}, errors.New("definition.range is required")
		}
		if err := definition.Range.validate("definition.range"); err != nil {
			return MIDIReferences{}, err
		}
		if definition.ChordSize == nil || definition.ChordSize.Min < 2 || definition.ChordSize.Max > 10 || definition.ChordSize.Min > definition.ChordSize.Max {
			return MIDIReferences{}, errors.New("definition.chord_size must satisfy 2 <= min <= max <= 10")
		}
		if definition.SustainNotes == nil {
			return MIDIReferences{}, errors.New("definition.sustain_notes is required")
		}
		if definition.AnswerOrder != "Any" && definition.AnswerOrder != "FromBottom" && definition.AnswerOrder != "FromTop" {
			return MIDIReferences{}, errors.New("definition.answer_order is invalid")
		}
		if definition.Context != nil {
			if err := definition.Context.validate(); err != nil {
				return MIDIReferences{}, err
			}
		}
		return MIDIReferences{}, validateChordsConfig(definition.LevelConfig)
	default:
		return MIDIReferences{}, errors.New("unsupported course mode")
	}
}

func validateSinglesConfig(raw json.RawMessage) error {
	var envelope configType
	if err := json.Unmarshal(raw, &envelope); err != nil {
		return errors.New("definition.level_config is invalid")
	}
	switch envelope.Type {
	case "absolute":
		var config singlesAbsoluteConfig
		if err := json.Unmarshal(raw, &config); err != nil || len(config.Scales) == 0 {
			return errors.New("absolute singles config requires at least one scale")
		}
		for index, scale := range config.Scales {
			if err := scale.validate(fmt.Sprintf("definition.level_config.scales[%d]", index)); err != nil {
				return err
			}
		}
		return validateRotationAndTuning(config.RotateEveryQuestions, config.TuneInconsistencyCents)
	case "relative":
		var config singlesRelativeConfig
		if err := json.Unmarshal(raw, &config); err != nil {
			return errors.New("invalid relative singles config")
		}
		if err := config.ScaleConfig.validate("definition.level_config.scale_config"); err != nil {
			return err
		}
		return validateRotationAndTuning(config.RotateEveryQuestions, config.TuneInconsistencyCents)
	default:
		return errors.New("definition.level_config.type must be absolute or relative")
	}
}

func validateChordsConfig(raw json.RawMessage) error {
	var envelope configType
	if err := json.Unmarshal(raw, &envelope); err != nil {
		return errors.New("definition.level_config is invalid")
	}
	switch envelope.Type {
	case "absolute":
		var config chordsAbsoluteConfig
		if err := json.Unmarshal(raw, &config); err != nil || len(config.Scales) == 0 {
			return errors.New("absolute chords config requires at least one scale")
		}
		for index, scale := range config.Scales {
			if err := scale.validate(fmt.Sprintf("definition.level_config.scales[%d]", index)); err != nil {
				return err
			}
		}
		if err := validatePositiveOptional(config.RotateEveryQuestions, "rotate_every_questions"); err != nil {
			return err
		}
		if config.ChordStyle != nil {
			return config.ChordStyle.validate("definition.level_config.chord_style")
		}
		return nil
	case "relative":
		var config chordsRelativeConfig
		if err := json.Unmarshal(raw, &config); err != nil {
			return errors.New("invalid relative chords config")
		}
		if err := config.ScaleConfig.validate("definition.level_config.scale_config"); err != nil {
			return err
		}
		if err := validatePositiveOptional(config.RotateEveryQuestions, "rotate_every_questions"); err != nil {
			return err
		}
		if config.ChordStyle != nil {
			return config.ChordStyle.validate("definition.level_config.chord_style")
		}
		return nil
	default:
		return errors.New("definition.level_config.type must be absolute or relative")
	}
}

func validateMelodyConfig(raw json.RawMessage, public bool) (MIDIReferences, error) {
	var envelope configType
	if err := json.Unmarshal(raw, &envelope); err != nil {
		return MIDIReferences{}, errors.New("definition.config is invalid")
	}
	switch envelope.Type {
	case "random":
		var config melodyRandomConfig
		if err := json.Unmarshal(raw, &config); err != nil {
			return MIDIReferences{}, errors.New("invalid random melody config")
		}
		if err := validateScaleConfig(config.ScaleConfig, "definition.config.scale_config"); err != nil {
			return MIDIReferences{}, err
		}
		if err := validateQuestions(config.QuestionsNumber, "definition.config.questions_number"); err != nil {
			return MIDIReferences{}, err
		}
		if err := validatePositiveOptional(config.NotesPerSequence, "notes_per_sequence"); err != nil {
			return MIDIReferences{}, err
		}
		if config.NotesPerSequence == nil && config.QuestionsNumber != nil {
			return MIDIReferences{}, errors.New("definition.config.questions_number must be null for an endless melody stream")
		}
		if config.Tempo < 20 || config.Tempo > 400 {
			return MIDIReferences{}, errors.New("definition.config.tempo must be between 20 and 400")
		}
		if err := config.Range.validate("definition.config.range"); err != nil {
			return MIDIReferences{}, err
		}
		if err := validateRotationAndTuning(config.RotateEveryQuestions, config.TuneInconsistencyCents); err != nil {
			return MIDIReferences{}, err
		}
		if config.MelodyStyle != nil {
			if err := config.MelodyStyle.validate("definition.config.melody_style"); err != nil {
				return MIDIReferences{}, err
			}
		}
		return MIDIReferences{}, nil
	case "midi":
		var config melodyMIDIConfig
		if err := json.Unmarshal(raw, &config); err != nil {
			return MIDIReferences{}, errors.New("invalid MIDI melody config")
		}
		file := config.File
		if strings.TrimSpace(file.FileName) == "" {
			return MIDIReferences{}, errors.New("definition.config.file.file_name is required")
		}
		switch file.Type {
		case "backend":
			if file.MelodyID == nil || *file.MelodyID == 0 || file.VariantID == nil || *file.VariantID == 0 || strings.TrimSpace(file.Path) != "" {
				return MIDIReferences{}, errors.New("backend MIDI files require melody_id and variant_id, and cannot contain path")
			}
		case "local":
			if public {
				return MIDIReferences{}, errors.New("public MIDI levels must use a backend file reference")
			}
			if strings.TrimSpace(file.Path) == "" || file.MelodyID != nil || file.VariantID != nil {
				return MIDIReferences{}, errors.New("local MIDI files require path and cannot contain backend ids")
			}
		default:
			return MIDIReferences{}, errors.New("definition.config.file.type must be backend or local")
		}
		return MIDIReferences{File: &file}, nil
	default:
		return MIDIReferences{}, errors.New("definition.config.type must be random or midi")
	}
}

func validateScaleConfig(raw json.RawMessage, field string) error {
	var envelope scaleType
	if err := json.Unmarshal(raw, &envelope); err != nil {
		return fmt.Errorf("%s is invalid", field)
	}
	switch envelope.Type {
	case "absolute":
		var scale absoluteScaleEnvelope
		if err := json.Unmarshal(raw, &scale); err != nil {
			return fmt.Errorf("%s is invalid", field)
		}
		return scale.AbsoluteScaleConfig.validate(field)
	case "relative":
		var scale relativeScaleEnvelope
		if err := json.Unmarshal(raw, &scale); err != nil {
			return fmt.Errorf("%s is invalid", field)
		}
		return scale.RelativeScaleConfig.validate(field)
	default:
		return fmt.Errorf("%s.type must be absolute or relative", field)
	}
}

func validateQuestions(value *int, field string) error {
	if value != nil && *value <= 0 {
		return fmt.Errorf("%s must be null or positive", field)
	}
	return nil
}

func validatePositiveOptional(value *int, field string) error {
	if value != nil && *value <= 0 {
		return fmt.Errorf("%s must be null or positive", field)
	}
	return nil
}

func validateRotationAndTuning(rotation *int, tuning int) error {
	if err := validatePositiveOptional(rotation, "rotate_every_questions"); err != nil {
		return err
	}
	if tuning < 0 || tuning > 100 {
		return errors.New("tune_inconsistency_cents must be between 0 and 100")
	}
	return nil
}

func IsValidLevelSource(value string) bool {
	switch strings.TrimSpace(value) {
	case "built_in", "user", "imported":
		return true
	default:
		return false
	}
}

func DefaultMIDIDefinition(melodyID uint, variantID uint, fileName string) json.RawMessage {
	definition := MelodiesLevelDefinition{
		Config: mustJSON(melodyMIDIConfig{
			Type: "midi",
			File: MIDIFileReference{
				Type: "backend", MelodyID: &melodyID, VariantID: &variantID, FileName: fileName,
			},
		}),
		Context: nil,
	}
	return mustJSON(definition)
}

func mustJSON(value any) json.RawMessage {
	encoded, err := json.Marshal(value)
	if err != nil {
		panic(err)
	}
	return encoded
}
