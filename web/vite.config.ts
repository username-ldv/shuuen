import tailwindcss from '@tailwindcss/vite';
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	server: {
		// Bind on all interfaces so both IPv4 (127.0.0.1) and IPv6 (::1) localhost resolve.
		host: true
	},
	plugins: [
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) => filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},

			// Static output: the whole site prerenders to plain HTML/CSS/JS (no server runtime).
			// See https://svelte.dev/docs/kit/adapters for more information about adapters.
			adapter: adapter(),

			// "News" is a placeholder nav item with no section yet — don't fail the
			// prerender crawl over its missing #news anchor, just warn.
			prerender: {
				handleMissingId: 'warn'
			}
		})
	]
});
