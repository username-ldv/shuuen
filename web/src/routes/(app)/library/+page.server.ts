import type { PageServerLoad } from "./$types";
import { getAuthToken } from "$lib/server/auth";
import { isAdmin, listCourses } from "$lib/server/library";

export const load: PageServerLoad = async ({ cookies, fetch, locals }) => {
	const admin = isAdmin(locals.user);
	const result = await listCourses(fetch, { token: admin ? getAuthToken(cookies) : undefined, includePrivate: admin });
	return {
		user: locals.user,
		isAdmin: admin,
		courses: result.ok ? result.data : [],
		error: result.ok ? null : result.message,
	};
};
