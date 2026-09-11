<script lang="ts">
	import { Button } from "$lib/components/ui/button";
	import * as Card from "$lib/components/ui/card";
	import { Separator } from "$lib/components/ui/separator";
	import CircleUserRound from "@lucide/svelte/icons/circle-user-round";
	import LogIn from "@lucide/svelte/icons/log-in";
	import LogOut from "@lucide/svelte/icons/log-out";
	import RefreshCw from "@lucide/svelte/icons/refresh-cw";
	import type { PageData } from "./$types";

	let { data }: { data: PageData } = $props();

	const displayName = $derived(data.user?.display_name || data.user?.username || "Shuuen user");
	const initial = $derived(displayName.slice(0, 1).toUpperCase());
	const joined = $derived(
		data.user ? new Intl.DateTimeFormat("en", { dateStyle: "medium" }).format(new Date(data.user.created_at)) : ""
	);
</script>

<svelte:head>
	<title>Profile · Shuuen</title>
	<meta name="description" content="Your Shuuen profile." />
</svelte:head>

<section class="mx-auto flex w-full max-w-120 flex-col gap-6">
	<div class="flex flex-col gap-2">
		<span class="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Profile</span>
		<h1 class="text-2xl font-semibold">Your Shuuen page</h1>
		<p class="text-sm leading-relaxed text-muted-foreground">
			A small account surface now, ready for sync and personal library features later.
		</p>
	</div>

	{#if data.user}
		<Card.Root>
			<Card.Header>
				<div class="flex items-center gap-4">
					<div class="flex size-12 items-center justify-center rounded-lg bg-muted text-lg font-semibold">
						{initial}
					</div>
					<div class="min-w-0 flex flex-col gap-1">
						<Card.Title class="truncate">{displayName}</Card.Title>
						<Card.Description class="truncate">@{data.user.username}</Card.Description>
					</div>
				</div>
			</Card.Header>
			<Card.Content class="flex flex-col gap-5">
				<dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
					<div class="flex flex-col gap-1">
						<dt class="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Role</dt>
						<dd class="text-sm">{data.user.role}</dd>
					</div>
					<div class="flex flex-col gap-1">
						<dt class="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Joined</dt>
						<dd class="text-sm">{joined}</dd>
					</div>
				</dl>
				<Separator />
				<div class="flex flex-wrap items-center gap-2">
					<Button href="/me" variant="secondary">
						<RefreshCw data-icon="inline-start" />
						Refresh
					</Button>
					<form method="POST" action="?/logout">
						<Button type="submit" variant="outline">
							<LogOut data-icon="inline-start" />
							Log Out
						</Button>
					</form>
				</div>
			</Card.Content>
		</Card.Root>
	{:else}
		<Card.Root>
			<Card.Header>
				<Card.Title>Profile unavailable</Card.Title>
				<Card.Description>{data.authError ?? "Your session could not be loaded."}</Card.Description>
			</Card.Header>
			<Card.Content class="flex flex-wrap items-center gap-2">
				<Button href="/login">
					<LogIn data-icon="inline-start" />
					Log In
				</Button>
				<form method="POST" action="?/logout">
					<Button type="submit" variant="secondary">
						<LogOut data-icon="inline-start" />
						Clear Session
					</Button>
				</form>
			</Card.Content>
		</Card.Root>
	{/if}
</section>
