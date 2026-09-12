import type { MelodyKey } from "$lib/library/key";
import type {
	Course,
	CourseLevel,
	CourseModeName,
	LabelItem,
	LibraryGroup,
	Melody,
	ProgressionGroup,
} from "$lib/library/types";
import { levelKey, sectionTrail } from "$lib/library/summary";
import { requestBackend, requestBackendList, type BackendFetch, type BackendResult } from "./backend";

/**
 * Server-side client for the catalog and course endpoints. Reads pass the
 * admin's token with `include_private=true` so private material shows up in
 * the Library; mutations always require the token.
 */
export type Scope = { token?: string; includePrivate?: boolean };

function scoped(path: string, scope: Scope) {
	if (!scope.includePrivate || !scope.token) return path;
	return `${path}${path.includes("?") ? "&" : "?"}include_private=true`;
}

export function listCourses(fetcher: BackendFetch, scope: Scope = {}) {
	return requestBackendList<Course>(fetcher, scoped("/api/v1/courses?limit=200", scope), { token: scope.token });
}

export function getCourse(fetcher: BackendFetch, courseId: number, scope: Scope = {}) {
	return requestBackend<Course>(fetcher, scoped(`/api/v1/courses/${courseId}`, scope), { token: scope.token });
}

export function listLevels(
	fetcher: BackendFetch,
	courseId: number,
	mode: CourseModeName,
	options: { groupId?: string; limit?: number; offset?: number },
	scope: Scope = {}
) {
	const params = new URLSearchParams();
	if (options.groupId) params.set("group_id", options.groupId);
	params.set("limit", String(options.limit ?? 50));
	params.set("offset", String(options.offset ?? 0));
	return requestBackendList<CourseLevel>(
		fetcher,
		scoped(`/api/v1/courses/${courseId}/${mode}/levels?${params}`, scope),
		{ token: scope.token }
	);
}

export function getLevel(
	fetcher: BackendFetch,
	courseId: number,
	mode: CourseModeName,
	levelId: string,
	scope: Scope = {}
) {
	return requestBackend<CourseLevel>(
		fetcher,
		scoped(`/api/v1/courses/${courseId}/${mode}/levels/${encodeURIComponent(levelId)}`, scope),
		{ token: scope.token }
	);
}

/** Every MIDI level of one progression tab, flattened for the labeling screen. */
export async function listLabelItems(
	fetcher: BackendFetch,
	courseId: number,
	groupId: string,
	scope: Scope,
	maxItems = 3000
): Promise<BackendResult<LabelItem[]>> {
	const items: LabelItem[] = [];
	const limit = 200;
	for (let offset = 0; offset < maxItems; offset += limit) {
		const page = await listLevels(fetcher, courseId, "melodies", { groupId, limit, offset }, scope);
		if (!page.ok) return page;
		for (const level of page.data) {
			if (level.definition.config?.type !== "midi" || !level.midi) continue;
			items.push({
				levelId: level.id,
				melodyId: level.midi.melody_id,
				variantId: level.midi.variant_id,
				name: level.name,
				fileName: level.definition.config.file?.file_name ?? "",
				sections: sectionTrail(level),
				key: levelKey(level),
			});
		}
		if (offset + limit >= page.meta.total) break;
	}
	return { ok: true, status: 200, data: items };
}

export function patchMelody(
	fetcher: BackendFetch,
	token: string,
	melodyId: number,
	patch: { title?: string; key?: MelodyKey | null }
) {
	return requestBackend<Melody>(fetcher, `/api/v1/library/melodies/${melodyId}`, {
		method: "PATCH",
		token,
		body: JSON.stringify(patch),
	});
}

export function putMelodyKeys(fetcher: BackendFetch, token: string, melodyIds: number[], key: MelodyKey | null) {
	return requestBackend<{ updated: number }>(fetcher, "/api/v1/library/melodies/keys", {
		method: "PUT",
		token,
		body: JSON.stringify({ melody_ids: melodyIds, key }),
	});
}

export function patchLibraryGroup(
	fetcher: BackendFetch,
	token: string,
	groupId: number,
	patch: { name?: string; description?: string }
) {
	return requestBackend<LibraryGroup>(fetcher, `/api/v1/library/groups/${groupId}`, {
		method: "PATCH",
		token,
		body: JSON.stringify(patch),
	});
}

export function putProgressionGroup(
	fetcher: BackendFetch,
	token: string,
	courseId: number,
	mode: CourseModeName,
	groupId: string,
	payload: { name: string; description: string }
) {
	return requestBackend<ProgressionGroup>(
		fetcher,
		`/api/v1/courses/${courseId}/${mode}/groups/${encodeURIComponent(groupId)}`,
		{ method: "PUT", token, body: JSON.stringify(payload) }
	);
}

export function putLevel(
	fetcher: BackendFetch,
	token: string,
	courseId: number,
	mode: CourseModeName,
	level: CourseLevel,
	name: string
) {
	return requestBackend<CourseLevel>(
		fetcher,
		`/api/v1/courses/${courseId}/${mode}/levels/${encodeURIComponent(level.id)}`,
		{
			method: "PUT",
			token,
			body: JSON.stringify({
				name,
				source: level.source,
				definition: level.definition,
				is_public: level.is_public,
			}),
		}
	);
}

export function isAdmin(user: { role: string } | null | undefined) {
	return user?.role === "admin";
}

export function parseCourseMode(value: string | null | undefined): CourseModeName | null {
	if (value === "singles" || value === "melodies" || value === "chords") return value;
	return null;
}
