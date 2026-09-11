package util

import "testing"

func TestSlugify(t *testing.T) {
	tests := map[string]string{
		"Grade 1: Book A!": "grade-1-book-a",
		"  Already Slug  ": "already-slug",
		"":                 "item",
		"---":              "item",
	}

	for input, expected := range tests {
		if actual := Slugify(input); actual != expected {
			t.Fatalf("Slugify(%q) = %q, want %q", input, actual, expected)
		}
	}
}
