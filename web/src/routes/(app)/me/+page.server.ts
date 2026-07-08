import { redirect } from "@sveltejs/kit";
import type { Actions, PageServerLoad } from "./$types";
import { clearAuthCookie, getAuthToken } from "$lib/server/auth";

export const load: PageServerLoad = ({ cookies, locals, url }) => {
	const hasSession = Boolean(getAuthToken(cookies));
	if (!locals.user && !hasSession) {
		throw redirect(303, `/login?redirectTo=${encodeURIComponent(url.pathname)}`);
	}

	return {
		user: locals.user,
		authError: locals.authError ?? null,
	};
};

export const actions = {
	logout: async ({ cookies }) => {
		clearAuthCookie(cookies);
		throw redirect(303, "/");
	},
} satisfies Actions;
