package util

import (
	"regexp"
	"strings"
)

var nonSlugChars = regexp.MustCompile(`[^a-z0-9]+`)

func Slugify(value string) string {
	slug := strings.ToLower(strings.TrimSpace(value))
	slug = nonSlugChars.ReplaceAllString(slug, "-")
	slug = strings.Trim(slug, "-")
	if slug == "" {
		return "item"
	}
	return slug
}
