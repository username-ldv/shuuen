<script lang="ts">
	import * as Card from "$lib/components/ui/card";
	import { Badge } from "$lib/components/ui/badge";
	import CourseTypeBadge from "$lib/components/library/CourseTypeBadge.svelte";
	import EyeOff from "@lucide/svelte/icons/eye-off";
	import type { PageData } from "./$types";

	let { data }: { data: PageData } = $props();

	const fileBased = $derived(data.courses.filter((course) => course.structure_source === "blueprint"));
	const levelBased = $derived(data.courses.filter((course) => course.structure_source === "managed"));
</script>

<svelte:head>
	<title>Library · Shuuen</title>
	<meta name="description" content="Browse the courses and melodies served by this Shuuen instance." />
</svelte:head>

{#snippet courseGrid(courses: typeof data.courses)}
	<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
		{#each courses as course (course.id)}
			<a href={`/library/${course.id}`} class="group rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-ring">
				<Card.Root class="h-full transition-colors group-hover:bg-card/80">
					<Card.Header>
						<div class="flex items-start justify-between gap-3">
							<div class="min-w-0 flex flex-col gap-1">
								<Card.Title class="truncate">{course.name}</Card.Title>
								{#if course.author}
									<Card.Description class="truncate">{course.author}</Card.Description>
								{/if}
							</div>
							<CourseTypeBadge source={course.structure_source} />
						</div>
					</Card.Header>
					<Card.Content class="flex flex-col gap-3">
						{#if course.description}
							<p class="line-clamp-2 text-sm leading-relaxed text-muted-foreground">{course.description}</p>
						{/if}
						<div class="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
							<span>{course.progression_group_count} {course.progression_group_count === 1 ? "group" : "groups"}</span>
							<span aria-hidden="true">·</span>
							<span>{course.level_count} {course.level_count === 1 ? "level" : "levels"}</span>
							{#if !course.is_public}
								<Badge variant="ghost" class="text-muted-foreground">
									<EyeOff />
									Private
								</Badge>
							{/if}
						</div>
					</Card.Content>
				</Card.Root>
			</a>
		{/each}
	</div>
{/snippet}

<section class="flex w-full flex-col gap-10">
	<div class="flex flex-col gap-2">
		<span class="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Library</span>
		<h1 class="text-2xl font-semibold">Courses on this server</h1>
		<p class="text-sm leading-relaxed text-muted-foreground">
			Every top-level folder of the catalog is a course. Level-based courses generate their questions
			from stored parameters; file-based courses play the MIDI melodies in their folders.
			{#if data.isAdmin}
				As an administrator you can rename groups and levels and label the key of each melody.
			{/if}
		</p>
	</div>

	{#if data.error}
		<Card.Root>
			<Card.Header>
				<Card.Title>Library unavailable</Card.Title>
				<Card.Description>{data.error}</Card.Description>
			</Card.Header>
		</Card.Root>
	{:else if data.courses.length === 0}
		<Card.Root>
			<Card.Header>
				<Card.Title>No courses yet</Card.Title>
				<Card.Description>Add folders to the backend's data root and rescan the catalog.</Card.Description>
			</Card.Header>
		</Card.Root>
	{:else}
		{#if fileBased.length > 0}
			<div class="flex flex-col gap-4">
				<h2 class="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">File-based</h2>
				{@render courseGrid(fileBased)}
			</div>
		{/if}
		{#if levelBased.length > 0}
			<div class="flex flex-col gap-4">
				<h2 class="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Level-based</h2>
				{@render courseGrid(levelBased)}
			</div>
		{/if}
	{/if}
</section>
