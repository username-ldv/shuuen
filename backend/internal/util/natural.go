package util

import (
	"strings"
	"unicode/utf8"
)

// NaturalLess compares strings lexically while treating runs of ASCII digits
// as whole numbers. It keeps filename ordering intuitive without converting
// numeric runs to fixed-width integers, so arbitrarily long numbers are safe.
func NaturalLess(left, right string) bool {
	foldedLeft := strings.ToLower(left)
	foldedRight := strings.ToLower(right)

	if comparison := compareNatural(foldedLeft, foldedRight); comparison != 0 {
		return comparison < 0
	}
	return left < right
}

func compareNatural(left, right string) int {
	for len(left) > 0 && len(right) > 0 {
		if isASCIIDigit(left[0]) && isASCIIDigit(right[0]) {
			leftDigits, leftRest := takeDigits(left)
			rightDigits, rightRest := takeDigits(right)
			if comparison := compareDigitRuns(leftDigits, rightDigits); comparison != 0 {
				return comparison
			}
			left, right = leftRest, rightRest
			continue
		}

		leftRune, leftSize := utf8.DecodeRuneInString(left)
		rightRune, rightSize := utf8.DecodeRuneInString(right)
		if leftRune < rightRune {
			return -1
		}
		if leftRune > rightRune {
			return 1
		}
		left, right = left[leftSize:], right[rightSize:]
	}

	switch {
	case len(left) == 0 && len(right) == 0:
		return 0
	case len(left) == 0:
		return -1
	default:
		return 1
	}
}

func takeDigits(value string) (string, string) {
	end := 0
	for end < len(value) && isASCIIDigit(value[end]) {
		end++
	}
	return value[:end], value[end:]
}

func compareDigitRuns(left, right string) int {
	leftSignificant := strings.TrimLeft(left, "0")
	rightSignificant := strings.TrimLeft(right, "0")
	if leftSignificant == "" {
		leftSignificant = "0"
	}
	if rightSignificant == "" {
		rightSignificant = "0"
	}

	if len(leftSignificant) < len(rightSignificant) {
		return -1
	}
	if len(leftSignificant) > len(rightSignificant) {
		return 1
	}
	if leftSignificant < rightSignificant {
		return -1
	}
	if leftSignificant > rightSignificant {
		return 1
	}
	if len(left) < len(right) {
		return -1
	}
	if len(left) > len(right) {
		return 1
	}
	return 0
}

func isASCIIDigit(value byte) bool {
	return value >= '0' && value <= '9'
}
