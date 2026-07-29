package course

import (
	"encoding/json"
	"testing"
)

func TestPublicMIDIDefinitionRequiresBackendReference(t *testing.T) {
	definition := MelodiesLevelDefinition{
		Config: mustJSON(melodyMIDIConfig{
			Type: "midi",
			File: MIDIFileReference{Type: "local", Path: "D:/private/song.mid", FileName: "song.mid"},
		}),
	}
	raw, err := json.Marshal(definition)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := ValidateDefinition(ModeMelodies, raw, true); err == nil {
		t.Fatal("public MIDI definition accepted a local file path")
	}
	if _, err := ValidateDefinition(ModeMelodies, raw, false); err != nil {
		t.Fatalf("private MIDI definition rejected a local file path: %v", err)
	}
}

func TestDefaultMIDIDefinitionRoundTripsThroughValidation(t *testing.T) {
	raw := DefaultMIDIDefinition(12, 34, "lesson.mid")
	references, err := ValidateDefinition(ModeMelodies, raw, true)
	if err != nil {
		t.Fatal(err)
	}
	if references.File == nil || references.File.MelodyID == nil || *references.File.MelodyID != 12 ||
		references.File.VariantID == nil || *references.File.VariantID != 34 {
		t.Fatalf("unexpected MIDI references: %#v", references)
	}
}

func TestChordDefinitionValidatesCopiedMusicalBounds(t *testing.T) {
	sustain := true
	questions := 20
	definition := ChordsLevelDefinition{
		LevelConfig: mustJSON(chordsRelativeConfig{
			Type: "relative",
			ScaleConfig: RelativeScaleConfig{
				ScaleType:    "Major",
				DegreeStates: []DegreeState{{Degree: "D1", Active: true}, {Degree: "D3", Active: true}, {Degree: "D5", Active: true}},
			},
		}),
		QuestionsNumber: &questions,
		Range:           &NoteRange{From: Note{MIDIIndex: 36}, To: Note{MIDIIndex: 84}},
		ChordSize:       &ChordSizeRange{Min: 2, Max: 4},
		SustainNotes:    &sustain,
		AnswerOrder:     "Any",
	}
	raw, err := json.Marshal(definition)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := ValidateDefinition(ModeChords, raw, true); err != nil {
		t.Fatalf("valid chord definition rejected: %v", err)
	}
	definition.Range.To.MIDIIndex = 109
	raw, _ = json.Marshal(definition)
	if _, err := ValidateDefinition(ModeChords, raw, true); err == nil {
		t.Fatal("out-of-range piano note was accepted")
	}
}

func TestRandomMelodyDefinitionCarriesScaleContextAndStyle(t *testing.T) {
	questions, notes, rotation := 20, 4, 10
	definition := MelodiesLevelDefinition{
		Config: mustJSON(melodyRandomConfig{
			Type: "random",
			ScaleConfig: mustJSON(relativeScaleEnvelope{
				Type: "relative",
				RelativeScaleConfig: RelativeScaleConfig{
					ScaleType: "Major",
					DegreeStates: []DegreeState{{Degree: "D1", Active: true}, {Degree: "D3", Active: true}, {Degree: "D5", Active: true}},
				},
			}),
			QuestionsNumber: &questions, NotesPerSequence: &notes, Tempo: 96,
			Range: NoteRange{From: Note{MIDIIndex: 48}, To: Note{MIDIIndex: 72}},
			RotateEveryQuestions: &rotation,
			MelodyStyle: &MelodyStyle{
				ID: "quarters", Name: "Quarters", Tier: "Beginner",
				Figures: []WeightedFigure{{
					Figure: RhythmFigure{Values: []string{"Quarter"}, Ladder: "Scale"}, Weight: 1,
				}},
				NoteWeights: NoteWeights{ChordToneBoost: 1},
			},
		}),
		Context: &DegreeContext{
			ID: "simple-drone", Source: "imported",
			Nodes: []DegreeContextNode{{
				FirstDegree: DegreeWithOctave{Degree: "D1", Octave: 2},
				ExtraDegrees: []Degree{}, Sustain: Sustain{Type: "endless"},
				Duration: ContextDuration{Type: "same_as_scale_rotation"},
				RelativeDirection: "Up",
			}},
		},
	}
	raw, err := json.Marshal(definition)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := ValidateDefinition(ModeMelodies, raw, true); err != nil {
		t.Fatalf("valid random melody definition rejected: %v", err)
	}
}
