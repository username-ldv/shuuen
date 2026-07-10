package httpapi

import (
	"strings"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"shuuen-backend/internal/model"
	dbquery "shuuen-backend/internal/query"
)

type groupTreeResponse struct {
	Group        model.LibraryGroup   `json:"group"`
	Children     []model.LibraryGroup `json:"children"`
	Melodies     []model.Melody       `json:"melodies"`
	MelodiesMeta listMeta             `json:"melodies_meta"`
}

func (h *Handler) ListGroups(c fiber.Ctx) error {
	limit, offset := parsePagination(c)

	query := gorm.G[model.LibraryGroup](h.db).Preload(dbquery.LibraryGroup.Tags.Name(), nil)
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	} else if value, err := parseOptionalBool(c, "public"); err != nil {
		return sendError(c, fiber.StatusBadRequest, err.Error())
	} else if value != nil {
		query = query.Where("is_public = ?", *value)
	}
	if parentID := parseQueryInt(c, "parent_id", -1); parentID >= 0 {
		if parentID == 0 {
			query = query.Where("parent_id IS NULL")
		} else {
			query = query.Where("parent_id = ?", parentID)
		}
	}
	if parentPath := cleanURLPath(c.Query("parent_path")); parentPath != "" || strings.TrimSpace(c.Query("parent_path")) != "" {
		parentQuery := gorm.G[model.LibraryGroup](h.db).Where(dbquery.LibraryGroup.Path.Eq(parentPath))
		if !includePrivate(c) {
			parentQuery = parentQuery.Where("is_public = ?", true)
		}
		parent, err := parentQuery.First(c.Context())
		if err != nil {
			return notFoundOrError(c, err, "parent group not found")
		}
		query = query.Where("parent_id = ?", parent.ID)
	}
	if prefix := cleanURLPath(c.Query("path_prefix")); prefix != "" {
		query = query.Where("path = ? OR path LIKE ? ESCAPE '\\'", prefix, descendantPattern(prefix))
	}
	if q := strings.TrimSpace(c.Query("q")); q != "" {
		needle := containsPattern(strings.ToLower(q))
		query = query.Where("LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(path) LIKE ? ESCAPE '\\'", needle, needle)
	}

	total, err := query.Count(c.Context(), "*")
	if err != nil {
		return err
	}
	rows, err := query.
		Order("path asc, sort_order asc, name asc").
		Limit(limit).
		Offset(offset).
		Find(c.Context())
	if err != nil {
		return err
	}
	return c.JSON(listResponse{Data: rows, Meta: listMeta{Limit: limit, Offset: offset, Total: total}})
}

func (h *Handler) GetGroup(c fiber.Ctx) error {
	id, err := parseParamUint(c, "id")
	if err != nil {
		return err
	}

	query := gorm.G[model.LibraryGroup](h.db).
		Preload(dbquery.LibraryGroup.Parent.Name(), nil).
		Preload(dbquery.LibraryGroup.Tags.Name(), nil)
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	}
	group, err := query.Where(dbquery.LibraryGroup.ID.Eq(id)).First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "group not found")
	}
	return h.sendGroupTree(c, group, parseBoolQuery(c, "recursive", false))
}

func (h *Handler) GetGroupByVersionedPath(c fiber.Ctx) error {
	return h.getGroupByPath(c, c.Params("*"))
}

func (h *Handler) GetGroupByDynamicPath(c fiber.Ctx) error {
	prefix := "/api/"
	requestPath := c.Path()
	if !strings.HasPrefix(requestPath, prefix) {
		return sendError(c, fiber.StatusNotFound, "group not found")
	}
	groupPath := strings.TrimPrefix(requestPath, prefix)
	if strings.HasPrefix(groupPath, "v1/") {
		return sendError(c, fiber.StatusNotFound, "group not found")
	}
	return h.getGroupByPath(c, groupPath)
}

func (h *Handler) getGroupByPath(c fiber.Ctx, rawPath string) error {
	groupPath := cleanURLPath(rawPath)
	query := gorm.G[model.LibraryGroup](h.db).
		Preload(dbquery.LibraryGroup.Parent.Name(), nil).
		Preload(dbquery.LibraryGroup.Tags.Name(), nil).
		Where(dbquery.LibraryGroup.Path.Eq(groupPath))
	if !includePrivate(c) {
		query = query.Where("is_public = ?", true)
	}
	group, err := query.First(c.Context())
	if err != nil {
		return notFoundOrError(c, err, "group not found")
	}
	return h.sendGroupTree(c, group, parseBoolQuery(c, "recursive", false))
}

func (h *Handler) sendGroupTree(c fiber.Ctx, group model.LibraryGroup, recursive bool) error {
	childrenQuery := gorm.G[model.LibraryGroup](h.db).
		Preload(dbquery.LibraryGroup.Tags.Name(), nil).
		Where(dbquery.LibraryGroup.ParentID.Eq(group.ID))
	if !includePrivate(c) {
		childrenQuery = childrenQuery.Where("is_public = ?", true)
	}
	children, err := childrenQuery.
		Order("sort_order asc, name asc").
		Find(c.Context())
	if err != nil {
		return err
	}

	limit, offset := parsePagination(c)
	melodyQuery := gorm.G[model.Melody](h.db).
		Preload(dbquery.Melody.Tags.Name(), nil).
		Preload(dbquery.Melody.Variants.Name(), nil).
		Preload(dbquery.Melody.Group.Name(), nil).
		Joins(clause.InnerJoin.Association(dbquery.Melody.Group.Name()), nil)
	if !includePrivate(c) {
		melodyQuery = melodyQuery.Where(
			dbquery.Melody.IsPublic.WithTable("melodies").Eq(true),
			dbquery.LibraryGroup.IsPublic.WithTable("Group").Eq(true),
		)
	}
	if recursive {
		if group.Path == "" {
			// The root group includes every visible descendant.
		} else {
			groupPath := dbquery.LibraryGroup.Path.WithTable("Group").Column()
			melodyQuery = melodyQuery.Where(clause.Expr{
				SQL:  "? = ? OR ? LIKE ? ESCAPE '\\'",
				Vars: []any{groupPath, group.Path, groupPath, descendantPattern(group.Path)},
			})
		}
	} else {
		melodyQuery = melodyQuery.Where("melodies.group_id = ?", group.ID)
	}

	total, err := melodyQuery.Distinct("melodies.id").Count(c.Context(), "melodies.id")
	if err != nil {
		return err
	}
	melodies, err := melodyQuery.
		Select("melodies.*").
		Order("melodies.sort_order asc, melodies.title asc, melodies.id asc").
		Limit(limit).Offset(offset).
		Find(c.Context())
	if err != nil {
		return err
	}

	return sendData(c, fiber.StatusOK, groupTreeResponse{
		Group: group, Children: children, Melodies: melodies,
		MelodiesMeta: listMeta{Limit: limit, Offset: offset, Total: total},
	})
}

func withGroupPathFilter(query gorm.ChainInterface[model.Melody], groupPath string, recursive bool) gorm.ChainInterface[model.Melody] {
	groupPath = cleanURLPath(groupPath)
	if groupPath == "" {
		return query
	}
	if recursive {
		column := dbquery.LibraryGroup.Path.WithTable("Group").Column()
		return query.Where(clause.Expr{
			SQL:  "? = ? OR ? LIKE ? ESCAPE '\\'",
			Vars: []any{column, groupPath, column, descendantPattern(groupPath)},
		})
	}
	return query.Where(dbquery.LibraryGroup.Path.WithTable("Group").Eq(groupPath))
}
