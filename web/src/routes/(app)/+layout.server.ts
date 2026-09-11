import type { LayoutServerLoad } from "./$types";

export const load: LayoutServerLoad = ({ locals }) => {
	return {
		user: locals.user,
		authError: locals.authError ?? null,
	};
};
