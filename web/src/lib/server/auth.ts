import { dev } from "$app/environment";
import { env } from "$env/dynamic/private";
import type { Cookies } from "@sveltejs/kit";
import type { AuthUser } from "$lib/auth/types";

export const AUTH_COOKIE = "shuuen_session";

type ApiEnvelope<T> = {
	data?: T;
	error?: string;
	details?: unknown;
};

type AuthPayload = {
	user: AuthUser;
	access_token: string;
	token_type: string;
	expires_at: string;
};

type AuthResult<T> =
	| { ok: true; status: number; data: T }
	| { ok: false; status: number; message: string };

type BackendFetch = typeof fetch;

const DEFAULT_BACKEND_ORIGIN = "http://127.0.0.1:9999";

function backendOrigin() {
	return (env.SHUUEN_BACKEND_URL || env.SHUUEN_API_URL || DEFAULT_BACKEND_ORIGIN).replace(/\/$/, "");
}

function backendUrl(path: string) {
	return `${backendOrigin()}${path}`;
}

async function parseJson<T>(response: Response): Promise<ApiEnvelope<T>> {
	try {
		return (await response.json()) as ApiEnvelope<T>;
	} catch {
		return {};
	}
}

async function requestBackend<T>(
	fetcher: BackendFetch,
	path: string,
	init: RequestInit = {}
): Promise<AuthResult<T>> {
	let response: Response;
	const headers = new Headers(init.headers);
	if (!headers.has("Accept")) {
		headers.set("Accept", "application/json");
	}

	try {
		response = await fetcher(backendUrl(path), {
			...init,
			headers,
		});
	} catch {
		return {
			ok: false,
			status: 503,
			message: "The Shuuen backend is not reachable. Start the Go API and try again.",
		};
	}

	const body = await parseJson<T>(response);

	if (!response.ok) {
		return {
			ok: false,
			status: response.status,
			message: body.error ?? `The backend returned HTTP ${response.status}.`,
		};
	}

	if (body.data === undefined) {
		return {
			ok: false,
			status: 502,
			message: "The backend returned an unexpected auth response.",
		};
	}

	return { ok: true, status: response.status, data: body.data };
}

export function getAuthToken(cookies: Cookies) {
	return cookies.get(AUTH_COOKIE);
}

export function setAuthCookie(cookies: Cookies, payload: AuthPayload) {
	const expires = new Date(payload.expires_at);

	cookies.set(AUTH_COOKIE, payload.access_token, {
		path: "/",
		httpOnly: true,
		sameSite: "lax",
		secure: !dev,
		expires: Number.isNaN(expires.getTime()) ? undefined : expires,
	});
}

export function clearAuthCookie(cookies: Cookies) {
	cookies.delete(AUTH_COOKIE, { path: "/" });
}

export function login(
	fetcher: BackendFetch,
	credentials: { username: string; password: string }
) {
	return requestBackend<AuthPayload>(fetcher, "/api/v1/auth/login", {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
		},
		body: JSON.stringify(credentials),
	});
}

export function register(
	fetcher: BackendFetch,
	payload: { username: string; password: string; display_name?: string }
) {
	return requestBackend<AuthPayload>(fetcher, "/api/v1/auth/register", {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
		},
		body: JSON.stringify(payload),
	});
}

export function getMe(fetcher: BackendFetch, token: string) {
	return requestBackend<AuthUser>(fetcher, "/api/v1/auth/me", {
		headers: {
			Authorization: `Bearer ${token}`,
		},
	});
}
