import { error } from "@sveltejs/kit";
import type { RequestHandler } from "./$types";
import { getAuthToken } from "$lib/server/auth";
import { backendUrl } from "$lib/server/backend";
import { isAdmin } from "$lib/server/library";

/**
 * Streams a catalog file to the browser. Public variants need no session;
 * administrators also receive private ones because their token is forwarded
 * with `include_private=true`.
 */
export const GET: RequestHandler = async ({ params, cookies, fetch, locals }) => {
	const variantId = Number(params.id);
	if (!Number.isInteger(variantId) || variantId <= 0) {
		throw error(400, "Invalid variant id.");
	}
	const headers = new Headers();
	const token = getAuthToken(cookies);
	let path = `/api/v1/library/variants/${variantId}/download`;
	if (token && isAdmin(locals.user)) {
		headers.set("Authorization", `Bearer ${token}`);
		path += "?include_private=true";
	}
	let response: Response;
	try {
		response = await fetch(backendUrl(path), { headers });
	} catch {
		throw error(503, "The Shuuen backend is not reachable.");
	}
	if (!response.ok) {
		throw error(response.status, response.status === 404 ? "File not found." : "The backend refused the download.");
	}
	const passthrough = new Headers();
	passthrough.set("Content-Type", response.headers.get("Content-Type") ?? "audio/midi");
	const length = response.headers.get("Content-Length");
	if (length) passthrough.set("Content-Length", length);
	passthrough.set("Cache-Control", "private, max-age=300");
	return new Response(response.body, { status: 200, headers: passthrough });
};
