package query

import "gorm.io/gorm"

// Unscoped includes soft-deleted rows in a Generics API operation.
func Unscoped(statement *gorm.Statement) {
	statement.Unscoped = true
}

// SkipHooks prevents maintenance-only updates from changing timestamps.
func SkipHooks(statement *gorm.Statement) {
	statement.SkipHooks = true
}
