<script lang="ts">
	import { Button } from "$lib/components/ui/button";
	import CircleUserRound from "@lucide/svelte/icons/circle-user-round";
	import Home from "@lucide/svelte/icons/home";
	import LogIn from "@lucide/svelte/icons/log-in";
	import UserPlus from "@lucide/svelte/icons/user-plus";
	import type { LayoutData } from "./$types";

	let { children, data }: { children: import("svelte").Snippet; data: LayoutData } = $props();
</script>

<div class="flex min-h-screen flex-col bg-background text-foreground">
	<header
		class="sticky top-0 flex items-center justify-between border-b border-border bg-background/80 px-6 py-4 backdrop-blur-sm"
	>
		<a href="/" class="flex items-center gap-3 text-foreground">
			<img src="/images/shuuen-icon.png" alt="" class="size-6 object-contain" />
			<span class="text-sm font-semibold uppercase tracking-[0.28em]">Shuuen</span>
		</a>
		<nav class="flex items-center gap-2">
			<Button href="/" variant="ghost" size="sm" class="hidden sm:inline-flex">
				<Home data-icon="inline-start" />
				Home
			</Button>
			{#if data.user}
				<Button href="/me" size="sm">
					<CircleUserRound data-icon="inline-start" />
					<span class="max-w-28 truncate sm:max-w-36">@{data.user.username}</span>
				</Button>
			{:else}
				<Button href="/login" variant="ghost" size="sm">
					<LogIn data-icon="inline-start" />
					Log In
				</Button>
				<Button href="/sign-up" size="sm">
					<UserPlus data-icon="inline-start" />
					Sign Up
				</Button>
			{/if}
		</nav>
	</header>

	<main class="mx-auto flex w-full max-w-180 flex-1 px-6 py-16">
		{@render children()}
	</main>
</div>
