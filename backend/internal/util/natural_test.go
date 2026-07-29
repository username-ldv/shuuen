package util

import (
	"slices"
	"sort"
	"testing"
)

func TestNaturalLessSortsNumericRunsByValue(t *testing.T) {
	values := []string{
		"Lesson 10 part 2",
		"lesson 2",
		"lesson 1",
		"lesson 01",
		"Lesson 10 part 10",
		"lesson 100000000000000000000",
	}
	sort.Slice(values, func(i, j int) bool { return NaturalLess(values[i], values[j]) })

	want := []string{
		"lesson 1",
		"lesson 01",
		"lesson 2",
		"Lesson 10 part 2",
		"Lesson 10 part 10",
		"lesson 100000000000000000000",
	}
	if !slices.Equal(values, want) {
		t.Fatalf("natural order = %q, want %q", values, want)
	}
}
