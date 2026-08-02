package model

import (
	"database/sql/driver"
	"encoding/json"
	"errors"

	"gorm.io/gorm"
	"gorm.io/gorm/schema"
)

// JSONDocument keeps typed API structures such as level definitions and
// per-question results intact while using the native JSON type on both
// supported databases.
type JSONDocument []byte

func NewJSONDocument(value any) (JSONDocument, error) {
	encoded, err := json.Marshal(value)
	return JSONDocument(encoded), err
}

func (document JSONDocument) Value() (driver.Value, error) {
	if len(document) == 0 {
		return nil, nil
	}
	if !json.Valid(document) {
		return nil, errors.New("invalid JSON document")
	}
	return string(document), nil
}

func (document *JSONDocument) Scan(value any) error {
	if value == nil {
		*document = nil
		return nil
	}
	var raw []byte
	switch typed := value.(type) {
	case []byte:
		raw = typed
	case string:
		raw = []byte(typed)
	default:
		return errors.New("unsupported JSON database value")
	}
	if !json.Valid(raw) {
		return errors.New("database contains invalid JSON")
	}
	*document = append((*document)[:0], raw...)
	return nil
}

func (document JSONDocument) MarshalJSON() ([]byte, error) {
	if len(document) == 0 {
		return []byte("null"), nil
	}
	if !json.Valid(document) {
		return nil, errors.New("invalid JSON document")
	}
	return document, nil
}

func (document *JSONDocument) UnmarshalJSON(value []byte) error {
	if !json.Valid(value) {
		return errors.New("invalid JSON document")
	}
	*document = append((*document)[:0], value...)
	return nil
}

func (JSONDocument) GormDataType() string { return "json" }

func (JSONDocument) GormDBDataType(db *gorm.DB, _ *schema.Field) string {
	if db.Dialector.Name() == "postgres" {
		return "JSONB"
	}
	return "JSON"
}
