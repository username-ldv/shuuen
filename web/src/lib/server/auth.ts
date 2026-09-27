import { dev } from "$app/environment";
import type { Cookies } from "@sveltejs/kit";
import type { AuthUser } from "$lib/auth/types";
import { requestBackend, type BackendFetch } from "./backend";

export const AUTH_COOKIE = "shuuen_session";

type AuthPayload = {
	user: AuthUser;
	access_token: string;
	token_type: string;
	expires_at: string;
};

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

/**
 * The API rate-limits sign-in per client address. Every call from this server
 * shares one address, so name the visitor to give each their own limit.
 */
function forwardedFor(clientAddress: string) {
	return { "X-Forwarded-For": clientAddress };
}

export function login(
	fetcher: BackendFetch,
	credentials: { username: string; password: string },
	clientAddress: string
) {
	return requestBackend<AuthPayload>(fetcher, "/api/v1/auth/login", {
		method: "POST",
		headers: forwardedFor(clientAddress),
		body: JSON.stringify(credentials),
	});
}

export function register(
	fetcher: BackendFetch,
	payload: { username: string; password: string; display_name?: string },
	clientAddress: string
) {
	return requestBackend<AuthPayload>(fetcher, "/api/v1/auth/register", {
		method: "POST",
		headers: forwardedFor(clientAddress),
		body: JSON.stringify(payload),
	});
}

export function getMe(fetcher: BackendFetch, token: string) {
	return requestBackend<AuthUser>(fetcher, "/api/v1/auth/me", { token });
}
