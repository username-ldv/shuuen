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
	const confirmInvalid = $derived(form?.field === "confirmPassword");
	const loginHref = $derived(`/login?redirectTo=${encodeURIComponent(data.redirectTo)}`);

	const enhanceForm: SubmitFunction = () => {
		return async ({ update }) => {
			await update({ reset: false, invalidateAll: false });
		};
	};
</script>

<svelte:head>
	<title>Sign Up · Shuuen</title>
	<meta name="description" content="Create a Shuuen account." />
</svelte:head>

<section class="mx-auto flex w-full max-w-105 flex-col gap-6">
	<div class="flex flex-col gap-2 text-center">
		<span class="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Account</span>
		<h1 class="text-2xl font-semibold">Create your account</h1>
		<p class="text-sm leading-relaxed text-muted-foreground">
			Optional for now, useful when personal pages and sync arrive.
		</p>
	</div>

	<Card.Root>
		<Card.Header>
			<Card.Title>Start a profile</Card.Title>
			<Card.Description>Register with the Shuuen backend.</Card.Description>
			<Card.Action>
				<Button href={loginHref} variant="link" size="sm">
					<LogIn data-icon="inline-start" />
					Log In
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
							value={form?.values?.username ?? ""}
							aria-invalid={usernameInvalid || undefined}
							required
						/>
						{#if usernameInvalid}
							<Field.Error>{form?.message}</Field.Error>
						{:else}
							<Field.Description>3-20 letters, numbers, or underscores.</Field.Description>
						{/if}
					</Field.Field>

					<Field.Field data-invalid={passwordInvalid || undefined}>
						<Field.Label for="password">Password</Field.Label>
						<Input
							id="password"
							name="password"
							type="password"
							autocomplete="new-password"
							aria-invalid={passwordInvalid || undefined}
							required
						/>
						{#if passwordInvalid}
							<Field.Error>{form?.message}</Field.Error>
						{:else}
							<Field.Description>At least 8 characters.</Field.Description>
						{/if}
					</Field.Field>

					<Field.Field data-invalid={confirmInvalid || undefined}>
						<Field.Label for="confirmPassword">Confirm password</Field.Label>
						<Input
							id="confirmPassword"
							name="confirmPassword"
							type="password"
							autocomplete="new-password"
							aria-invalid={confirmInvalid || undefined}
							required
						/>
						{#if confirmInvalid}
							<Field.Error>{form?.message}</Field.Error>
						{/if}
					</Field.Field>
				</Field.Group>

				<Button type="submit" class="w-full">
					<UserPlus data-icon="inline-start" />
					Sign Up
				</Button>
			</form>
		</Card.Content>
		<Card.Footer>
			<p class="text-sm text-muted-foreground">
				Already have an account?
				<a class="text-foreground underline underline-offset-4" href={loginHref}>Log in</a>.
			</p>
		</Card.Footer>
	</Card.Root>
</section>
