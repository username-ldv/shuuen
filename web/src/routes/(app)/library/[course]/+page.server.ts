import { error, fail } from "@sveltejs/kit";
import type { Actions, PageServerLoad } from "./$types";
import { getAuthToken } from "$lib/server/auth";
import {
	getCourse,
	getLevel,
	isAdmin,
	listLevels,
	parseCourseMode,
	patchLibraryGroup,
	patchMelody,
	putLevel,
	putMelodyKeys,
	putProgressionGroup,
} from "$lib/server/library";
import { normalizeKey, type MelodyKey } from "$lib/library/key";
import type { ListMeta } from "$lib/server/backend";
import type { CourseLevel } from "$lib/library/types";

const PAGE_SIZE = 50;

function parseCourseId(raw: string) {
	const id = Number(raw);
	if (!Number.isInteger(id) || id <= 0) {
		throw error(404, "Course not found.");
	}
	return id;
}

function readString(formData: FormData, key: string) {
	const value = formData.get(key);
	return typeof value === "string" ? value.trim() : "";
}

function readIds(formData: FormData, key: string) {
	return readString(formData, key)
		.split(",")
		.map((part) => Number(part.trim()))
		.filter((id) => Number.isInteger(id) && id > 0);
}

export const load: PageServerLoad = async ({ params, url, cookies, fetch, locals }) => {
	const courseId = parseCourseId(params.course);
	const admin = isAdmin(locals.user);
	const scope = { token: admin ? getAuthToken(cookies) : undefined, includePrivate: admin };

	const courseResult = await getCourse(fetch, courseId, scope);
	if (!courseResult.ok) {
		throw error(courseResult.status === 404 ? 404 : 502, courseResult.message);
	}
	const course = courseResult.data;
	const modes = course.modes ?? [];
	const requestedMode = parseCourseMode(url.searchParams.get("mode"));
	const currentMode = modes.find((mode) => mode.mode === requestedMode) ?? modes[0] ?? null;
	const groups = currentMode?.groups ?? [];
	const requestedGroup = url.searchParams.get("group");
	const group = groups.find((candidate) => candidate.id === requestedGroup) ?? groups[0] ?? null;
	const page = Math.max(1, Number(url.searchParams.get("page") ?? "1") || 1);

	let levels: CourseLevel[] = [];
	let meta: ListMeta = { limit: PAGE_SIZE, offset: 0, total: 0 };
	let levelsError: string | null = null;
	if (currentMode && group) {
		const result = await listLevels(
			fetch,
			courseId,
			currentMode.mode,
			{ groupId: group.id, limit: PAGE_SIZE, offset: (page - 1) * PAGE_SIZE },
			scope
		);
		if (result.ok) {
			levels = result.data;
			meta = result.meta;
		} else {
			levelsError = result.message;
		}
	}

	return {
		course,
		modes,
		mode: currentMode,
		groups,
		group,
		levels,
		meta,
		page,
		pageSize: PAGE_SIZE,
		levelsError,
		isAdmin: admin,
	};
};

export const actions = {
	renameGroup: async ({ params, request, cookies, fetch, locals }) => {
		const token = getAuthToken(cookies);
		if (!token || !isAdmin(locals.user)) {
			return fail(403, { action: "renameGroup", message: "Only administrators can rename groups." });
		}
		const courseId = parseCourseId(params.course);
		const formData = await request.formData();
		const name = readString(formData, "name");
		const description = readString(formData, "description");
		const mode = parseCourseMode(readString(formData, "mode"));
		const groupId = readString(formData, "groupId");
		const libraryGroupId = Number(readString(formData, "libraryGroupId"));
		const blueprint = readString(formData, "blueprint") === "1";
		if (!name) {
			return fail(400, { action: "renameGroup", message: "Enter a name for the group." });
		}
		const result = blueprint
			? await patchLibraryGroup(fetch, token, libraryGroupId, { name })
			: mode
				? await putProgressionGroup(fetch, token, courseId, mode, groupId, { name, description })
				: null;
		if (!result) {
			return fail(400, { action: "renameGroup", message: "Unknown course mode." });
		}
		if (!result.ok) {
			return fail(result.status >= 400 && result.status < 600 ? result.status : 400, {
				action: "renameGroup",
				message: result.message,
			});
		}
		return { action: "renameGroup", message: `Renamed to “${name}”.` };
	},

	renameLevel: async ({ params, request, cookies, fetch, locals }) => {
		const token = getAuthToken(cookies);
		if (!token || !isAdmin(locals.user)) {
			return fail(403, { action: "renameLevel", message: "Only administrators can rename levels." });
		}
		const courseId = parseCourseId(params.course);
		const formData = await request.formData();
		const name = readString(formData, "name");
		const mode = parseCourseMode(readString(formData, "mode"));
		const levelId = readString(formData, "levelId");
		const melodyId = Number(readString(formData, "melodyId"));
		const blueprint = readString(formData, "blueprint") === "1";
		if (!name) {
			return fail(400, { action: "renameLevel", message: "Enter a name for the level." });
		}
		if (blueprint) {
			const result = await patchMelody(fetch, token, melodyId, { title: name });
			if (!result.ok) {
				return fail(result.status, { action: "renameLevel", message: result.message });
			}
			return { action: "renameLevel", message: `Renamed to “${name}”.` };
		}
		if (!mode) {
			return fail(400, { action: "renameLevel", message: "Unknown course mode." });
		}
		const scope = { token, includePrivate: true };
		const existing = await getLevel(fetch, courseId, mode, levelId, scope);
		if (!existing.ok) {
			return fail(existing.status, { action: "renameLevel", message: existing.message });
		}
		// The API replaces the whole level, so send the stored definition back untouched.
		const updated = await putLevel(fetch, token, courseId, mode, existing.data, name);
		if (!updated.ok) {
			return fail(updated.status, { action: "renameLevel", message: updated.message });
		}
		return { action: "renameLevel", message: `Renamed to “${name}”.` };
	},

	setKeys: async ({ request, cookies, fetch, locals }) => {
		const token = getAuthToken(cookies);
		if (!token || !isAdmin(locals.user)) {
			return fail(403, { action: "setKeys", message: "Only administrators can label melodies." });
		}
		const formData = await request.formData();
		const melodyIds = readIds(formData, "melodyIds");
		const rawKey = readString(formData, "key");
		if (melodyIds.length === 0) {
			return fail(400, { action: "setKeys", message: "Select at least one melody." });
		}
		let key: MelodyKey | null = null;
		if (rawKey && rawKey !== "null") {
			try {
				key = normalizeKey(JSON.parse(rawKey) as MelodyKey);
			} catch {
				return fail(400, { action: "setKeys", message: "The key could not be read." });
			}
		}
		const result = await putMelodyKeys(fetch, token, melodyIds, key);
		if (!result.ok) {
			return fail(result.status, { action: "setKeys", message: result.message });
		}
		return {
			action: "setKeys",
			message: key
				? `Labelled ${result.data.updated} ${result.data.updated === 1 ? "melody" : "melodies"}.`
				: `Cleared the key of ${result.data.updated} ${result.data.updated === 1 ? "melody" : "melodies"}.`,
		};
	},
} satisfies Actions;
