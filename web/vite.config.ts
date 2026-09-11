import tailwindcss from '@tailwindcss/vite';
import adapter from '@sveltejs/adapter-node';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	server: {
		// Bind on all interfaces so both IPv4 (127.0.0.1) and IPv6 (::1) localhost resolve.
		host: true,
		// Dev-only: forward API calls to the local Go backend so browser client code
		// hits a same-origin `/api` (no CORS) during development. In production the
		// reverse proxy plays this role — this proxy block does nothing in the build.
		proxy: {
			'/api': 'http://localhost:9999'
		}
	},
	plugins: [
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) => filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},

			// Node server output (self-hosted). Static/marketing routes are still
			// prerendered at build time; dynamic app routes will SSR at runtime.
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
