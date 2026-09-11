import { fail, redirect } from "@sveltejs/kit";
import type { Actions, PageServerLoad } from "./$types";
import { register, setAuthCookie } from "$lib/server/auth";

function readString(formData: FormData, key: string) {
	const value = formData.get(key);
	return typeof value === "string" ? value.trim() : "";
}

function safeRedirect(raw: string | null) {
	if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return "/me";
	return raw;
}

function validUsername(value: string) {
	return /^[A-Za-z0-9_]{3,20}$/.test(value);
}

export const load: PageServerLoad = ({ locals, url }) => {
	const redirectTo = safeRedirect(url.searchParams.get("redirectTo"));
	if (locals.user) {
		throw redirect(303, redirectTo);
	}

	return { redirectTo };
};

export const actions = {
	default: async ({ request, cookies, fetch, url }) => {
		const formData = await request.formData();
		const username = readString(formData, "username");
		const password = readString(formData, "password");
		const confirmPassword = readString(formData, "confirmPassword");

		if (!username) {
			return fail(400, {
				values: { username },
				field: "username",
				message: "Enter a username.",
			});
		}

		if (!validUsername(username)) {
			return fail(400, {
				values: { username },
				field: "username",
				message: "Use 3-20 letters, numbers, or underscores.",
			});
		}

		if (password.length < 8) {
			return fail(400, {
				values: { username },
				field: "password",
				message: "Use at least 8 characters.",
			});
		}

		if (password !== confirmPassword) {
			return fail(400, {
				values: { username },
				field: "confirmPassword",
				message: "Passwords do not match.",
			});
		}

		const result = await register(fetch, {
			username,
			password,
		});
		if (!result.ok) {
			return fail(result.status >= 400 && result.status < 600 ? result.status : 400, {
				values: { username },
				field: null,
				message: result.message,
			});
		}

		setAuthCookie(cookies, result.data);
		throw redirect(303, safeRedirect(url.searchParams.get("redirectTo")));
	},
} satisfies Actions;
