import { error, json } from "@sveltejs/kit";
import type { RequestHandler } from "./$types";
import { getAuthToken } from "$lib/server/auth";
import { isAdmin, patchMelody } from "$lib/server/library";
import { normalizeKey, type MelodyKey } from "$lib/library/key";

/**
 * Same-origin bridge for the labeling screen: the browser never holds the
 * backend token, so key saves pass through here with the session cookie.
 */
export const PATCH: RequestHandler = async ({ params, request, cookies, fetch, locals }) => {
	const token = getAuthToken(cookies);
	if (!token || !isAdmin(locals.user)) {
		throw error(403, "Only administrators can label melodies.");
	}
	const melodyId = Number(params.id);
	if (!Number.isInteger(melodyId) || melodyId <= 0) {
		throw error(400, "Invalid melody id.");
	}
	let body: { title?: unknown; key?: unknown };
	try {
		body = (await request.json()) as typeof body;
	} catch {
		throw error(400, "Expected a JSON body.");
	}
	const patch: { title?: string; key?: MelodyKey | null } = {};
	if (typeof body.title === "string") {
		patch.title = body.title;
	}
	if (body.key === null) {
		patch.key = null;
	} else if (body.key && typeof body.key === "object") {
		patch.key = normalizeKey(body.key as MelodyKey);
	}
	if (patch.title === undefined && patch.key === undefined) {
		throw error(400, "Nothing to update.");
	}
	const result = await patchMelody(fetch, token, melodyId, patch);
	if (!result.ok) {
		return json({ error: result.message }, { status: result.status });
	}
	return json({ data: result.data });
};
