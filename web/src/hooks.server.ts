import type { Handle } from "@sveltejs/kit";
import { clearAuthCookie, getAuthToken, getMe } from "$lib/server/auth";

export const handle: Handle = async ({ event, resolve }) => {
	event.locals.user = null;

	const token = getAuthToken(event.cookies);
	if (!token) {
		return resolve(event);
	}

	const result = await getMe(event.fetch, token);
	if (result.ok) {
		event.locals.user = result.data;
		return resolve(event);
	}

	if (result.status === 401 || result.status === 404) {
		clearAuthCookie(event.cookies);
	} else {
		event.locals.authError = result.message;
	}

	return resolve(event);
};
