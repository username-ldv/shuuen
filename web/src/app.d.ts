import type { AuthUser } from "$lib/auth/types";

declare global {
	namespace App {
		// interface Error {}
		interface Locals {
			user: AuthUser | null;
			authError?: string;
		}
		// interface PageData {}
		// interface PageState {}
		// interface Platform {}
	}
}

export {};
