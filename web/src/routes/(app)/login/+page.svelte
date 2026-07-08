<script lang="ts">
	import { enhance } from "$app/forms";
	import { Button } from "$lib/components/ui/button";
	import * as Card from "$lib/components/ui/card";
	import * as Field from "$lib/components/ui/field";
	import { Input } from "$lib/components/ui/input";
	import LogIn from "@lucide/svelte/icons/log-in";
	import UserPlus from "@lucide/svelte/icons/user-plus";
	import type { ActionData, PageData, SubmitFunction } from "./$types";

	let { data, form }: { data: PageData; form: ActionData } = $props();

	const usernameInvalid = $derived(form?.field === "username");
	const passwordInvalid = $derived(form?.field === "password");
	const signUpHref = $derived(`/sign-up?redirectTo=${encodeURIComponent(data.redirectTo)}`);

	const enhanceForm: SubmitFunction = () => {
		return async ({ update }) => {
			await update({ reset: false, invalidateAll: false });
		};
	};
</script>

<svelte:head>
	<title>Log In · Shuuen</title>
	<meta name="description" content="Log in to your Shuuen profile." />
</svelte:head>

<section class="mx-auto flex w-full max-w-105 flex-col gap-6">
	<div class="flex flex-col gap-2 text-center">
		<span class="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Account</span>
		<h1 class="text-2xl font-semibold">Log in to Shuuen</h1>
		<p class="text-sm leading-relaxed text-muted-foreground">
			Use your account when you want a personal page. The app still works without one.
		</p>
	</div>

	<Card.Root>
		<Card.Header>
			<Card.Title>Welcome back</Card.Title>
			<Card.Description>Enter your username and password.</Card.Description>
			<Card.Action>
				<Button href={signUpHref} variant="link" size="sm">
					<UserPlus data-icon="inline-start" />
					Sign Up
				</Button>
			</Card.Action>
		</Card.Header>
		<Card.Content>
			<form method="POST" class="flex flex-col gap-5" use:enhance={enhanceForm}>
				{#if form?.message}
					<p role="alert" class="rounded-lg border border-border bg-muted px-3 py-2 text-sm text-foreground">
						{form.message}
					</p>
				{/if}

				<Field.Group>
					<Field.Field data-invalid={usernameInvalid || undefined}>
						<Field.Label for="username">Username</Field.Label>
						<Input
							id="username"
							name="username"
							autocomplete="username"
							value={form?.username ?? ""}
							aria-invalid={usernameInvalid || undefined}
							required
						/>
						{#if usernameInvalid}
							<Field.Error>{form?.message}</Field.Error>
						{/if}
					</Field.Field>

					<Field.Field data-invalid={passwordInvalid || undefined}>
						<Field.Label for="password">Password</Field.Label>
						<Input
							id="password"
							name="password"
							type="password"
							autocomplete="current-password"
							aria-invalid={passwordInvalid || undefined}
							required
						/>
						{#if passwordInvalid}
							<Field.Error>{form?.message}</Field.Error>
						{/if}
					</Field.Field>
				</Field.Group>

				<Button type="submit" class="w-full">
					<LogIn data-icon="inline-start" />
					Log In
				</Button>
			</form>
		</Card.Content>
		<Card.Footer>
			<p class="text-sm text-muted-foreground">
				No account yet?
				<a class="text-foreground underline underline-offset-4" href={signUpHref}>Create one</a>.
			</p>
		</Card.Footer>
	</Card.Root>
</section>
