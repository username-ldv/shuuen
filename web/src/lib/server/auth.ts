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

export function login(
	fetcher: BackendFetch,
	credentials: { username: string; password: string }
) {
	return requestBackend<AuthPayload>(fetcher, "/api/v1/auth/login", {
		method: "POST",
		body: JSON.stringify(credentials),
	});
}

export function register(
	fetcher: BackendFetch,
	payload: { username: string; password: string; display_name?: string }
) {
	return requestBackend<AuthPayload>(fetcher, "/api/v1/auth/register", {
		method: "POST",
		body: JSON.stringify(payload),
	});
}

export function getMe(fetcher: BackendFetch, token: string) {
	return requestBackend<AuthUser>(fetcher, "/api/v1/auth/me", { token });
}
