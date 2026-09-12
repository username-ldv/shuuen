<script lang="ts">
	import { enhance } from "$app/forms";
	import { SvelteSet } from "svelte/reactivity";
	import { Button } from "$lib/components/ui/button";
	import { Badge } from "$lib/components/ui/badge";
	import * as Card from "$lib/components/ui/card";
	import { Checkbox } from "$lib/components/ui/checkbox";
	import * as Dialog from "$lib/components/ui/dialog";
	import { Input } from "$lib/components/ui/input";
	import * as Table from "$lib/components/ui/table";
	import CourseTypeBadge from "$lib/components/library/CourseTypeBadge.svelte";
	import KeyBadge from "$lib/components/library/KeyBadge.svelte";
	import KeyEditor from "$lib/components/library/KeyEditor.svelte";
	import ArrowLeft from "@lucide/svelte/icons/arrow-left";
	import Check from "@lucide/svelte/icons/check";
	import ChevronLeft from "@lucide/svelte/icons/chevron-left";
	import ChevronRight from "@lucide/svelte/icons/chevron-right";
	import Eraser from "@lucide/svelte/icons/eraser";
	import EyeOff from "@lucide/svelte/icons/eye-off";
	import KeyboardMusic from "@lucide/svelte/icons/keyboard-music";
	import Music from "@lucide/svelte/icons/music";
	import Pencil from "@lucide/svelte/icons/pencil";
	import X from "@lucide/svelte/icons/x";
	import { emptyKey, normalizeKey, type MelodyKey } from "$lib/library/key";
	import { levelIsMidi, levelKey, summarizeLevel } from "$lib/library/summary";
	import type { CourseLevel } from "$lib/library/types";
	import type { ActionData, PageData, SubmitFunction } from "./$types";

	let { data, form }: { data: PageData; form: ActionData } = $props();

	const blueprint = $derived(data.course.structure_source === "blueprint");
	const fileBased = $derived(data.mode?.mode === "melodies" && data.levels.some(levelIsMidi));
	const totalPages = $derived(Math.max(1, Math.ceil(data.meta.total / data.pageSize)));

	let renamingGroup = $state(false);
	let renamingLevel = $state<string | null>(null);
	let selected = new SvelteSet<number>();
	let batchOpen = $state(false);
	let batchKey = $state<MelodyKey | null>(emptyKey());

	const selectableIds = $derived(data.levels.filter((level) => level.midi).map((level) => level.midi!.melody_id));
	const allSelected = $derived(selectableIds.length > 0 && selectableIds.every((id) => selected.has(id)));

	function href(overrides: { mode?: string; group?: string; page?: number }) {
		const params = new URLSearchParams();
		const mode = overrides.mode ?? data.mode?.mode;
		const group = overrides.group ?? (overrides.mode ? undefined : data.group?.id);
		if (mode) params.set("mode", mode);
		if (group) params.set("group", group);
		if (overrides.page && overrides.page > 1) params.set("page", String(overrides.page));
		const query = params.toString();
		return `/library/${data.course.id}${query ? `?${query}` : ""}`;
	}

	function toggleAll(checked: boolean) {
		if (checked) selectableIds.forEach((id) => selected.add(id));
		else selectableIds.forEach((id) => selected.delete(id));
	}

	function levelMelodyId(level: CourseLevel) {
		return level.midi?.melody_id ?? null;
	}

	const afterMutation: SubmitFunction = () => {
		return async ({ result, update }) => {
			await update({ reset: false });
			if (result.type === "success") {
				renamingGroup = false;
				renamingLevel = null;
				batchOpen = false;
				selected.clear();
			}
		};
	};
</script>

<svelte:head>
	<title>{data.course.name} · Library · Shuuen</title>
	<meta name="description" content={data.course.description || `Levels of the ${data.course.name} course.`} />
</svelte:head>

<section class="flex w-full flex-col gap-8">
	<div class="flex flex-col gap-4">
		<Button href="/library" variant="ghost" size="sm" class="w-fit -ml-2">
			<ArrowLeft data-icon="inline-start" />
			Library
		</Button>
		<div class="flex flex-wrap items-start justify-between gap-3">
			<div class="flex min-w-0 flex-col gap-2">
				<div class="flex flex-wrap items-center gap-2">
					<h1 class="text-2xl font-semibold">{data.course.name}</h1>
					<CourseTypeBadge source={data.course.structure_source} />
					{#if !data.course.is_public}
						<Badge variant="ghost" class="text-muted-foreground">
							<EyeOff />
							Private
						</Badge>
					{/if}
				</div>
				{#if data.course.description}
					<p class="text-sm leading-relaxed text-muted-foreground">{data.course.description}</p>
				{/if}
				{#if data.course.author}
					<p class="text-xs uppercase tracking-[0.15em] text-muted-foreground">{data.course.author}</p>
				{/if}
			</div>
			{#if data.isAdmin && blueprint && data.group}
				<Button href={`/library/${data.course.id}/label?group=${encodeURIComponent(data.group.id)}`}>
					<KeyboardMusic data-icon="inline-start" />
					Label keys
				</Button>
			{/if}
		</div>
	</div>

	{#if form?.message}
		<p class="text-sm text-muted-foreground" role="status">{form.message}</p>
	{/if}

	{#if data.modes.length > 1}
		<nav class="flex flex-wrap gap-1" aria-label="Modes">
			{#each data.modes as mode (mode.mode)}
				<Button href={href({ mode: mode.mode })} size="sm" variant={mode.mode === data.mode?.mode ? "secondary" : "ghost"}>
					{mode.name}
					<span class="text-muted-foreground">{mode.level_count}</span>
				</Button>
			{/each}
		</nav>
	{/if}

	{#if data.groups.length > 0}
		<div class="flex flex-col gap-3">
			<span class="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Groups</span>
			<nav class="flex flex-wrap gap-1" aria-label="Progression groups">
				{#each data.groups as group (group.id)}
					<Button href={href({ group: group.id })} size="sm" variant={group.id === data.group?.id ? "secondary" : "ghost"}>
						{group.name}
						<span class="text-muted-foreground">{group.level_count}</span>
					</Button>
				{/each}
			</nav>
		</div>
	{/if}

	{#if data.group}
		<Card.Root>
			<Card.Header>
				<div class="flex flex-wrap items-center justify-between gap-3">
					{#if renamingGroup && data.isAdmin && data.mode}
						<form method="POST" action="?/renameGroup" use:enhance={afterMutation} class="flex flex-1 items-center gap-2">
							<input type="hidden" name="mode" value={data.mode.mode} />
							<input type="hidden" name="groupId" value={data.group.id} />
							<input type="hidden" name="libraryGroupId" value={data.group.library_group_id} />
							<input type="hidden" name="description" value={data.group.description} />
							<input type="hidden" name="blueprint" value={data.group.blueprint ? "1" : "0"} />
							<Input name="name" value={data.group.name} aria-label="Group name" class="max-w-xs" required />
							<Button type="submit" size="sm">
								<Check data-icon="inline-start" />
								Save
							</Button>
							<Button type="button" size="sm" variant="ghost" onclick={() => (renamingGroup = false)}>
								<X data-icon="inline-start" />
								Cancel
							</Button>
						</form>
					{:else}
						<div class="flex min-w-0 flex-col gap-1">
							<Card.Title class="flex items-center gap-2">
								{data.group.name}
								{#if data.isAdmin}
									<Button size="icon-sm" variant="ghost" aria-label="Rename group" onclick={() => (renamingGroup = true)}>
										<Pencil />
									</Button>
								{/if}
							</Card.Title>
							<Card.Description>
								{data.meta.total} {data.meta.total === 1 ? "level" : "levels"}
								{#if data.group.section_count > 0}
									· {data.group.section_count} {data.group.section_count === 1 ? "section" : "sections"}
								{/if}
								{#if data.group.description}
									· {data.group.description}
								{/if}
							</Card.Description>
						</div>
					{/if}

					{#if data.isAdmin && fileBased}
						<div class="flex items-center gap-2">
							<Dialog.Root bind:open={batchOpen}>
								<Dialog.Trigger>
									{#snippet child({ props })}
										<Button {...props} size="sm" variant="secondary" disabled={selected.size === 0}>
											<Music data-icon="inline-start" />
											Set key for {selected.size || "selected"}
										</Button>
									{/snippet}
								</Dialog.Trigger>
								<Dialog.Content class="sm:max-w-2xl">
									<Dialog.Header>
										<Dialog.Title>Label {selected.size} {selected.size === 1 ? "melody" : "melodies"}</Dialog.Title>
										<Dialog.Description>
											The same tonic and degree set is written to every selected melody.
										</Dialog.Description>
									</Dialog.Header>
									<KeyEditor value={batchKey} onchange={(next) => (batchKey = next)} />
									<Dialog.Footer>
										<form method="POST" action="?/setKeys" use:enhance={afterMutation} class="flex gap-2">
											<input type="hidden" name="melodyIds" value={[...selected].join(",")} />
											<input type="hidden" name="key" value={batchKey ? JSON.stringify(normalizeKey(batchKey)) : "null"} />
											<Button type="submit" disabled={!batchKey}>
												<Check data-icon="inline-start" />
												Apply
											</Button>
										</form>
									</Dialog.Footer>
								</Dialog.Content>
							</Dialog.Root>
							<form method="POST" action="?/setKeys" use:enhance={afterMutation}>
								<input type="hidden" name="melodyIds" value={[...selected].join(",")} />
								<input type="hidden" name="key" value="null" />
								<Button type="submit" size="sm" variant="ghost" disabled={selected.size === 0}>
									<Eraser data-icon="inline-start" />
									Clear keys
								</Button>
							</form>
						</div>
					{/if}
				</div>
			</Card.Header>
			<Card.Content class="flex flex-col gap-4">
				{#if data.levelsError}
					<p class="text-sm text-muted-foreground">{data.levelsError}</p>
				{:else if data.levels.length === 0}
					<p class="text-sm text-muted-foreground">This group has no levels yet.</p>
				{:else}
					<Table.Root>
						<Table.Header>
							<Table.Row>
								{#if data.isAdmin && fileBased}
									<Table.Head class="w-8">
										<Checkbox
											checked={allSelected}
											indeterminate={!allSelected && selectableIds.some((id) => selected.has(id))}
											onCheckedChange={(checked) => toggleAll(checked === true)}
											aria-label="Select all on this page"
										/>
									</Table.Head>
								{/if}
								<Table.Head class="w-10 text-right">#</Table.Head>
								<Table.Head>Name</Table.Head>
								{#if fileBased}
									<Table.Head>Key</Table.Head>
								{/if}
								<Table.Head>Parameters</Table.Head>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{#each data.levels as level, index (level.id)}
								{@const melodyId = levelMelodyId(level)}
								{@const facts = data.mode ? summarizeLevel(data.mode.mode, level.definition) : []}
								<Table.Row>
									{#if data.isAdmin && fileBased}
										<Table.Cell>
											{#if melodyId !== null}
												<Checkbox
													checked={selected.has(melodyId)}
													onCheckedChange={(checked) => (checked ? selected.add(melodyId) : selected.delete(melodyId))}
													aria-label={`Select ${level.name}`}
												/>
											{/if}
										</Table.Cell>
									{/if}
									<Table.Cell class="text-right font-mono text-xs text-muted-foreground">
										{data.meta.offset + index + 1}
									</Table.Cell>
									<Table.Cell>
										{#if renamingLevel === level.id && data.isAdmin && data.mode}
											<form method="POST" action="?/renameLevel" use:enhance={afterMutation} class="flex items-center gap-2">
												<input type="hidden" name="mode" value={data.mode.mode} />
												<input type="hidden" name="levelId" value={level.id} />
												<input type="hidden" name="melodyId" value={melodyId ?? ""} />
												<input type="hidden" name="blueprint" value={blueprint ? "1" : "0"} />
												<Input name="name" value={level.name} aria-label="Level name" class="h-8 max-w-xs" required />
												<Button type="submit" size="icon-sm" aria-label="Save name">
													<Check />
												</Button>
												<Button type="button" size="icon-sm" variant="ghost" aria-label="Cancel" onclick={() => (renamingLevel = null)}>
													<X />
												</Button>
											</form>
										{:else}
											<div class="flex flex-col gap-0.5">
												{#if level.sections.length > 0}
													<span class="text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
														{level.sections.map((section) => section.name).join(" › ")}
													</span>
												{/if}
												<span class="flex items-center gap-1 font-medium">
													{level.name}
													{#if !level.is_public}
														<EyeOff class="size-3 text-muted-foreground" />
													{/if}
													{#if data.isAdmin}
														<Button size="icon-sm" variant="ghost" class="opacity-60" aria-label="Rename level" onclick={() => (renamingLevel = level.id)}>
															<Pencil />
														</Button>
													{/if}
												</span>
											</div>
										{/if}
									</Table.Cell>
									{#if fileBased}
										<Table.Cell>
											<KeyBadge key={levelKey(level)} />
										</Table.Cell>
									{/if}
									<Table.Cell>
										<div class="flex flex-wrap gap-x-3 gap-y-1 text-xs">
											{#each facts as fact (fact.label)}
												{#if !(fileBased && fact.label === "Key")}
													<span>
														<span class="text-muted-foreground">{fact.label}</span>
														<span class="ml-1">{fact.value}</span>
													</span>
												{/if}
											{/each}
										</div>
									</Table.Cell>
								</Table.Row>
							{/each}
						</Table.Body>
					</Table.Root>

					{#if totalPages > 1}
						<div class="flex items-center justify-between gap-3">
							<Button href={href({ page: data.page - 1 })} size="sm" variant="ghost" disabled={data.page <= 1}>
								<ChevronLeft data-icon="inline-start" />
								Previous
							</Button>
							<span class="text-xs text-muted-foreground">Page {data.page} of {totalPages}</span>
							<Button href={href({ page: data.page + 1 })} size="sm" variant="ghost" disabled={data.page >= totalPages}>
								Next
								<ChevronRight data-icon="inline-end" />
							</Button>
						</div>
					{/if}
				{/if}
			</Card.Content>
		</Card.Root>
	{:else}
		<Card.Root>
			<Card.Header>
				<Card.Title>No groups yet</Card.Title>
				<Card.Description>This course has no progression groups in this mode.</Card.Description>
			</Card.Header>
		</Card.Root>
	{/if}
</section>
