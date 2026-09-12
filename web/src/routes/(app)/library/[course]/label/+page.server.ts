import { error, redirect } from "@sveltejs/kit";
import type { PageServerLoad } from "./$types";
import { getAuthToken } from "$lib/server/auth";
import { getCourse, isAdmin, listLabelItems } from "$lib/server/library";

export const load: PageServerLoad = async ({ params, url, cookies, fetch, locals }) => {
	const token = getAuthToken(cookies);
	if (!locals.user && !token) {
		throw redirect(303, `/login?redirectTo=${encodeURIComponent(url.pathname + url.search)}`);
	}
	if (!token || !isAdmin(locals.user)) {
		throw error(403, "Only administrators can label melody keys.");
	}
	const courseId = Number(params.course);
	if (!Number.isInteger(courseId) || courseId <= 0) {
		throw error(404, "Course not found.");
	}
	const scope = { token, includePrivate: true };
	const courseResult = await getCourse(fetch, courseId, scope);
	if (!courseResult.ok) {
		throw error(courseResult.status === 404 ? 404 : 502, courseResult.message);
	}
	const course = courseResult.data;
	const melodies = (course.modes ?? []).find((mode) => mode.mode === "melodies");
	const groups = melodies?.groups ?? [];
	const requestedGroup = url.searchParams.get("group");
	const group = groups.find((candidate) => candidate.id === requestedGroup) ?? groups[0] ?? null;
	if (!group) {
		return { course, groups, group: null, items: [], itemsError: "This course has no melody groups." };
	}
	const items = await listLabelItems(fetch, courseId, group.id, scope);
	return {
		course,
		groups,
		group,
		items: items.ok ? items.data : [],
		itemsError: items.ok ? null : items.message,
	};
};
