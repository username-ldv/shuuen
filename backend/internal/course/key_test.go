package course

import (
	"reflect"
	"testing"
)

func TestMelodyKeyDerivesScaleTypeAndCanonicalOrder(t *testing.T) {
	key := MelodyKey{Tonic: PitchA, Spelling: " Sharps ", Degrees: []Degree{"D5", "D1", "DF7", "D4", "DF3", "D2", "DF6"}, ScaleType: "Major", ScaleName: " Aeolian "}
	normalized, err := ParseMelodyKey(key, "key")
	if err != nil {
		t.Fatalf("ParseMelodyKey returned error: %v", err)
	}
	if normalized.ScaleType != ScaleTypeNaturalMinor {
		t.Fatalf("scale type = %q, want NaturalMinor", normalized.ScaleType)
	}
	if normalized.Spelling != SpellingSharps || normalized.ScaleName != "Aeolian" {
		t.Fatalf("normalized = %#v", normalized)
	}
	want := []Degree{"D1", "D2", "DF3", "D4", "D5", "DF6", "DF7"}
	if !reflect.DeepEqual(normalized.Degrees, want) {
		t.Fatalf("degrees = %v, want %v", normalized.Degrees, want)
	}

	lydian := MelodyKey{Tonic: PitchF, Spelling: SpellingFlats, Degrees: []Degree{"D1", "D2", "D3", "DS4", "D5", "D6", "D7"}}
	if got := lydian.DerivedScaleType(); got != ScaleTypeCustom {
		t.Fatalf("lydian scale type = %q, want Custom", got)
	}
	major := MelodyKey{Tonic: PitchC, Spelling: SpellingSharps, Degrees: majorDegrees}
	if got := major.DerivedScaleType(); got != ScaleTypeMajor {
		t.Fatalf("major scale type = %q", got)
	}
}

func TestMelodyKeyValidation(t *testing.T) {
	cases := map[string]MelodyKey{
		"bad tonic":     {Tonic: "H", Spelling: SpellingSharps, Degrees: majorDegrees},
		"bad spelling":  {Tonic: PitchC, Spelling: "natural", Degrees: majorDegrees},
		"no degrees":    {Tonic: PitchC, Spelling: SpellingSharps},
		"bad degree":    {Tonic: PitchC, Spelling: SpellingSharps, Degrees: []Degree{"D1", "D9"}},
		"duplicate":     {Tonic: PitchC, Spelling: SpellingSharps, Degrees: []Degree{"D1", "D1"}},
		"bad scaletype": {Tonic: PitchC, Spelling: SpellingSharps, Degrees: majorDegrees, ScaleType: "Dorian"},
	}
	for name, key := range cases {
		if err := key.Validate("key"); err == nil {
			t.Fatalf("%s: expected validation error", name)
		}
	}
}
