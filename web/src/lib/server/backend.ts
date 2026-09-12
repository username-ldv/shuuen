import { env } from "$env/dynamic/private";

/** The Go API wraps every JSON payload as `{ data }` or `{ error, details }`. */
export type ApiEnvelope<T> = {
	data?: T;
	error?: string;
	details?: unknown;
};

export type BackendResult<T> =
	| { ok: true; status: number; data: T }
	| { ok: false; status: number; message: string };

export type BackendFetch = typeof fetch;

const DEFAULT_BACKEND_ORIGIN = "http://127.0.0.1:9999";

export function backendOrigin() {
	return (env.SHUUEN_BACKEND_URL || env.SHUUEN_API_URL || DEFAULT_BACKEND_ORIGIN).replace(/\/$/, "");
}

export function backendUrl(path: string) {
	return `${backendOrigin()}${path}`;
}

async function parseJson<T>(response: Response): Promise<ApiEnvelope<T>> {
	try {
		return (await response.json()) as ApiEnvelope<T>;
	} catch {
		return {};
	}
}

/**
 * Calls the Go API from server code and unwraps its envelope. Pass `token` to
 * send the user's bearer token; `allowEmpty` accepts 204 / body-less replies.
 */
export async function requestBackend<T>(
	fetcher: BackendFetch,
	path: string,
	init: RequestInit & { token?: string; allowEmpty?: boolean } = {}
): Promise<BackendResult<T>> {
	const { token, allowEmpty, ...rest } = init;
	const headers = new Headers(rest.headers);
	if (!headers.has("Accept")) {
		headers.set("Accept", "application/json");
	}
	if (token) {
		headers.set("Authorization", `Bearer ${token}`);
	}
	if (rest.body && !headers.has("Content-Type")) {
		headers.set("Content-Type", "application/json");
	}

	let response: Response;
	try {
		response = await fetcher(backendUrl(path), { ...rest, headers });
	} catch {
		return {
			ok: false,
			status: 503,
			message: "The Shuuen backend is not reachable. Start the Go API and try again.",
		};
	}

	if (allowEmpty && response.status === 204) {
		return { ok: true, status: response.status, data: undefined as T };
	}

	const body = await parseJson<T>(response);

	if (!response.ok) {
		const details =
			typeof body.details === "string" && body.details ? ` (${body.details})` : "";
		return {
			ok: false,
			status: response.status,
			message: body.error ? `${body.error}${details}` : `The backend returned HTTP ${response.status}.`,
		};
	}

	if (body.data === undefined) {
		return {
			ok: false,
			status: 502,
			message: "The backend returned an unexpected response.",
		};
	}

	return { ok: true, status: response.status, data: body.data };
}

/** Lists come back as `{ data: T[], meta }` rather than inside the envelope's `data`. */
export type ListMeta = { limit: number; offset: number; total: number };

export type ListResult<T> =
	| { ok: true; status: number; data: T[]; meta: ListMeta }
	| { ok: false; status: number; message: string };

export async function requestBackendList<T>(
	fetcher: BackendFetch,
	path: string,
	init: RequestInit & { token?: string } = {}
): Promise<ListResult<T>> {
	const { token, ...rest } = init;
	const headers = new Headers(rest.headers);
	headers.set("Accept", "application/json");
	if (token) {
		headers.set("Authorization", `Bearer ${token}`);
	}
	let response: Response;
	try {
		response = await fetcher(backendUrl(path), { ...rest, headers });
	} catch {
		return {
			ok: false,
			status: 503,
			message: "The Shuuen backend is not reachable. Start the Go API and try again.",
		};
	}
	let body: { data?: T[]; meta?: ListMeta; error?: string } = {};
	try {
		body = (await response.json()) as typeof body;
	} catch {
		body = {};
	}
	if (!response.ok) {
		return {
			ok: false,
			status: response.status,
			message: body.error ?? `The backend returned HTTP ${response.status}.`,
		};
	}
	if (!Array.isArray(body.data) || !body.meta) {
		return { ok: false, status: 502, message: "The backend returned an unexpected list response." };
	}
	return { ok: true, status: response.status, data: body.data, meta: body.meta };
}
