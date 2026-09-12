package course

import (
	"fmt"
	"sort"
	"strings"
)

// Spelling records whether a labelled key is written with sharps or flats. The
// tonic itself is always one of the twelve sharp-named Pitch values, so the
// spelling is what turns DSharp into E♭ major on screen.
const (
	SpellingSharps = "sharps"
	SpellingFlats  = "flats"
)

var validSpellings = map[string]bool{SpellingSharps: true, SpellingFlats: true}

// ScaleTypeMajor and friends mirror the app's ScaleType enum names.
const (
	ScaleTypeMajor        = "Major"
	ScaleTypeNaturalMinor = "NaturalMinor"
	ScaleTypeChromatic    = "Chromatic"
	ScaleTypeCustom       = "Custom"
)

// chromaticDegreeOrder is the canonical storage order for a degree set: rising
// semitones from the tonic.
var chromaticDegreeOrder = []Degree{"D1", "DF2", "D2", "DF3", "D3", "D4", "DS4", "D5", "DF6", "D6", "DF7", "D7"}

var degreeOffsets = func() map[Degree]int {
	result := make(map[Degree]int, len(chromaticDegreeOrder))
	for index, degree := range chromaticDegreeOrder {
		result[degree] = index
	}
	return result
}()

var (
	majorDegrees        = []Degree{"D1", "D2", "D3", "D4", "D5", "D6", "D7"}
	naturalMinorDegrees = []Degree{"D1", "D2", "DF3", "D4", "D5", "DF6", "DF7"}
)

// MelodyKey is the hand-labelled key of a MIDI melody: its tonic, the scale
// degrees the melody uses, and an optional human name for the scale. ScaleType
// is derived from the degree set and never trusted from input.
type MelodyKey struct {
	Tonic     Pitch    `json:"tonic"`
	Spelling  string   `json:"spelling"`
	Degrees   []Degree `json:"degrees"`
	ScaleType string   `json:"scale_type,omitempty"`
	ScaleName string   `json:"scale_name,omitempty"`
}

// Validate reports the first problem with the key, prefixing messages with field.
func (key MelodyKey) Validate(field string) error {
	if !validPitches[key.Tonic] {
		return fmt.Errorf("%s.tonic is invalid", field)
	}
	if !validSpellings[strings.ToLower(strings.TrimSpace(key.Spelling))] {
		return fmt.Errorf("%s.spelling must be sharps or flats", field)
	}
	if len(key.Degrees) == 0 {
		return fmt.Errorf("%s.degrees must not be empty", field)
	}
	seen := map[Degree]bool{}
	for index, degree := range key.Degrees {
		if !validDegrees[degree] {
			return fmt.Errorf("%s.degrees[%d] is invalid", field, index)
		}
		if seen[degree] {
			return fmt.Errorf("%s.degrees contains duplicate degree %s", field, degree)
		}
		seen[degree] = true
	}
	if key.ScaleType != "" && !validScaleTypes[key.ScaleType] {
		return fmt.Errorf("%s.scale_type is invalid", field)
	}
	if len([]rune(key.ScaleName)) > 120 {
		return fmt.Errorf("%s.scale_name must be at most 120 characters", field)
	}
	return nil
}

// Normalized returns the canonical form that is stored and served: degrees in
// chromatic order, lower-case spelling, trimmed name, and the derived scale type.
func (key MelodyKey) Normalized() MelodyKey {
	degrees := append([]Degree(nil), key.Degrees...)
	sort.SliceStable(degrees, func(i, j int) bool { return degreeOffsets[degrees[i]] < degreeOffsets[degrees[j]] })
	normalized := MelodyKey{
		Tonic:     key.Tonic,
		Spelling:  strings.ToLower(strings.TrimSpace(key.Spelling)),
		Degrees:   degrees,
		ScaleName: strings.TrimSpace(key.ScaleName),
	}
	normalized.ScaleType = normalized.DerivedScaleType()
	return normalized
}

// DerivedScaleType classifies the degree set as one of the app's scale types.
// Anything that is not exactly major, natural minor, or chromatic is Custom.
func (key MelodyKey) DerivedScaleType() string {
	switch {
	case sameDegreeSet(key.Degrees, majorDegrees):
		return ScaleTypeMajor
	case sameDegreeSet(key.Degrees, naturalMinorDegrees):
		return ScaleTypeNaturalMinor
	case sameDegreeSet(key.Degrees, chromaticDegreeOrder):
		return ScaleTypeChromatic
	default:
		return ScaleTypeCustom
	}
}

func sameDegreeSet(left []Degree, right []Degree) bool {
	if len(left) != len(right) {
		return false
	}
	set := make(map[Degree]bool, len(right))
	for _, degree := range right {
		set[degree] = true
	}
	for _, degree := range left {
		if !set[degree] {
			return false
		}
	}
	return true
}

// ParseMelodyKey validates and normalizes a decoded key.
func ParseMelodyKey(key MelodyKey, field string) (MelodyKey, error) {
	if err := key.Validate(field); err != nil {
		return MelodyKey{}, err
	}
	return key.Normalized(), nil
}
