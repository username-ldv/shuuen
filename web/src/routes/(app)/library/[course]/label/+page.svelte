<script lang="ts">
	import { beforeNavigate } from "$app/navigation";
	import { onDestroy, onMount, tick } from "svelte";
	import { Button } from "$lib/components/ui/button";
	import { Badge } from "$lib/components/ui/badge";
	import { Checkbox } from "$lib/components/ui/checkbox";
	import { Kbd, KbdGroup } from "$lib/components/ui/kbd";
	import { Label } from "$lib/components/ui/label";
	import { ScrollArea } from "$lib/components/ui/scroll-area";
	import KeyBadge from "$lib/components/library/KeyBadge.svelte";
	import KeyEditor from "$lib/components/library/KeyEditor.svelte";
	import ArrowLeft from "@lucide/svelte/icons/arrow-left";
	import ChevronDown from "@lucide/svelte/icons/chevron-down";
	import ChevronUp from "@lucide/svelte/icons/chevron-up";
	import CloudCheck from "@lucide/svelte/icons/cloud-check";
	import CloudOff from "@lucide/svelte/icons/cloud-off";
	import Copy from "@lucide/svelte/icons/copy";
	import Loader from "@lucide/svelte/icons/loader";
	import Play from "@lucide/svelte/icons/play";
	import Square from "@lucide/svelte/icons/square";
	import {
		DIGIT_DEGREES,
		SCALE_PRESETS,
		keysEqual,
		normalizeKey,
		shiftTonic,
		toggleDegree,
		withPreset,
		withTonicLetter,
		type MelodyKey,
	} from "$lib/library/key";
	import type { LabelItem } from "$lib/library/types";
	import { createMidiPlayer, type MidiPlayer } from "$lib/midi/player";
	import { cn } from "$lib/utils";
	import type { PageData } from "./$types";

	let { data }: { data: PageData } = $props();

	type SaveState = "idle" | "saving" | "saved" | "error";

	let items = $state<LabelItem[]>([]);
	let index = $state(0);
	let saveStates = $state<Record<number, SaveState>>({});
	let saveError = $state<string | null>(null);
	let autoplay = $state(true);
	let playing = $state(false);
	let loadingAudio = $state(false);
	let audioError = $state<string | null>(null);
	let nameInput = $state<HTMLInputElement | null>(null);

	const current = $derived(items[index] ?? null);
	const labelled = $derived(items.filter((item) => item.key).length);
	const saveState = $derived(current ? (saveStates[current.melodyId] ?? "idle") : "idle");

	// The load re-runs when the group changes; start over from the fresh list.
	$effect(() => {
		items = data.items.map((item) => ({ ...item, key: item.key ? { ...item.key, degrees: [...item.key.degrees] } : null }));
		index = 0;
		saveStates = {};
		saveError = null;
	});

	// Autosave: every edit is written after a short pause, and always before leaving.
	const timers = new Map<number, ReturnType<typeof setTimeout>>();
	const lastSaved = new Map<number, MelodyKey | null>();

	function scheduleSave(item: LabelItem) {
		const existing = timers.get(item.melodyId);
		if (existing) clearTimeout(existing);
		saveStates = { ...saveStates, [item.melodyId]: "saving" };
		timers.set(
			item.melodyId,
			setTimeout(() => {
				timers.delete(item.melodyId);
				void save(item);
			}, 450)
		);
	}

	async function save(item: LabelItem) {
		const key = item.key ? normalizeKey(item.key) : null;
		if (lastSaved.has(item.melodyId) && keysEqual(lastSaved.get(item.melodyId) ?? null, key)) {
			saveStates = { ...saveStates, [item.melodyId]: "saved" };
			return;
		}
		try {
			const response = await fetch(`/library/api/melodies/${item.melodyId}`, {
				method: "PATCH",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ key }),
				keepalive: true,
			});
			if (!response.ok) {
				const body = (await response.json().catch(() => ({}))) as { error?: string; message?: string };
				throw new Error(body.error ?? body.message ?? `HTTP ${response.status}`);
			}
			lastSaved.set(item.melodyId, key);
			saveStates = { ...saveStates, [item.melodyId]: "saved" };
			saveError = null;
		} catch (cause) {
			saveStates = { ...saveStates, [item.melodyId]: "error" };
			saveError = cause instanceof Error ? cause.message : "Saving failed.";
		}
	}

	function flushAll() {
		for (const [melodyId, timer] of timers) {
			clearTimeout(timer);
			timers.delete(melodyId);
			const item = items.find((candidate) => candidate.melodyId === melodyId);
			if (item) void save(item);
		}
	}

	beforeNavigate(() => flushAll());
	onDestroy(() => {
		flushAll();
		player?.dispose();
	});

	function setKey(next: MelodyKey | null) {
		if (!current) return;
		current.key = next;
		scheduleSave(current);
	}

	function copyPreviousKey() {
		if (!current) return;
		for (let candidate = index - 1; candidate >= 0; candidate -= 1) {
			const previous = items[candidate].key;
			if (previous) {
				setKey({ ...previous, degrees: [...previous.degrees] });
				return;
			}
		}
	}

	// Playback
	let player: MidiPlayer | null = null;
	const audioCache = new Map<number, ArrayBuffer>();
	let loadToken = 0;

	onMount(() => {
		player = createMidiPlayer((state) => (playing = state));
	});

	async function loadCurrent(play: boolean) {
		if (!player || !current) return;
		const item = current;
		const token = ++loadToken;
		loadingAudio = true;
		audioError = null;
		try {
			let bytes = audioCache.get(item.variantId);
			if (!bytes) {
				const response = await fetch(`/library/api/variants/${item.variantId}/download`);
				if (!response.ok) throw new Error(`Could not download ${item.fileName} (HTTP ${response.status}).`);
				bytes = await response.arrayBuffer();
				audioCache.set(item.variantId, bytes);
			}
			if (token !== loadToken) return;
			await player.load(bytes.slice(0));
			if (play && token === loadToken) await player.play();
		} catch (cause) {
			if (token === loadToken) audioError = cause instanceof Error ? cause.message : "Playback failed.";
		} finally {
			if (token === loadToken) loadingAudio = false;
		}
	}

	async function togglePlayback() {
		if (!player) return;
		if (playing) {
			player.stop();
			return;
		}
		await loadCurrent(true);
	}

	async function goTo(next: number) {
		if (items.length === 0) return;
		const clamped = Math.max(0, Math.min(items.length - 1, next));
		if (clamped === index) return;
		player?.stop();
		index = clamped;
		await tick();
		document.getElementById(`label-item-${items[index].melodyId}`)?.scrollIntoView({ block: "nearest" });
		if (autoplay) void loadCurrent(true);
	}

	function isTypingTarget(target: EventTarget | null) {
		if (!(target instanceof HTMLElement)) return false;
		return target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable;
	}

	function onKeydown(event: KeyboardEvent) {
		if (event.metaKey || event.ctrlKey || event.altKey) return;
		if (isTypingTarget(event.target)) {
			if (event.key === "Escape" || event.key === "Enter") {
				(event.target as HTMLElement).blur();
				event.preventDefault();
			}
			return;
		}
		if (!current) return;
		const code = event.code;
		let handled = true;

		if (code in DIGIT_DEGREES) {
			const mapping = DIGIT_DEGREES[code];
			const degree = event.shiftKey ? mapping.altered : mapping.natural;
			if (degree) setKey(toggleDegree(current.key, degree));
		} else if (/^Key[A-G]$/.test(code) && !event.shiftKey) {
			setKey(withTonicLetter(current.key, code.slice(3)));
		} else if (code === "Equal" || code === "NumpadAdd") {
			setKey(shiftTonic(current.key, 1));
		} else if (code === "Minus" || code === "NumpadSubtract") {
			setKey(shiftTonic(current.key, -1));
		} else if (code === "Space") {
			void togglePlayback();
		} else if (code === "Enter" || code === "ArrowDown" || code === "KeyJ") {
			void goTo(index + 1);
		} else if (code === "ArrowUp" || code === "KeyK") {
			void goTo(index - 1);
		} else if (code === "Backspace" || code === "Delete") {
			setKey(null);
		} else if (code === "KeyP") {
			copyPreviousKey();
		} else if (code === "Slash") {
			nameInput?.focus();
		} else {
			const preset = SCALE_PRESETS.find((candidate) => candidate.hotkey && code === `Key${candidate.hotkey}`);
			if (preset) setKey(withPreset(current.key, preset));
			else handled = false;
		}
		if (handled) event.preventDefault();
	}
</script>

<svelte:head>
	<title>Label keys · {data.course.name} · Shuuen</title>
</svelte:head>

<svelte:window onkeydown={onKeydown} onbeforeunload={flushAll} />

<section class="flex w-full flex-col gap-6">
	<div class="flex flex-wrap items-center justify-between gap-3">
		<div class="flex flex-col gap-1">
			<Button href={`/library/${data.course.id}${data.group ? `?group=${encodeURIComponent(data.group.id)}` : ""}`} variant="ghost" size="sm" class="-ml-2 w-fit">
				<ArrowLeft data-icon="inline-start" />
				{data.course.name}
			</Button>
			<h1 class="text-2xl font-semibold">Label keys</h1>
		</div>
		<div class="flex flex-wrap items-center gap-2">
			{#each data.groups as group (group.id)}
				<Button href={`?group=${encodeURIComponent(group.id)}`} size="sm" variant={group.id === data.group?.id ? "secondary" : "ghost"}>
					{group.name}
				</Button>
			{/each}
		</div>
	</div>

	{#if data.itemsError}
		<p class="text-sm text-muted-foreground">{data.itemsError}</p>
	{:else if items.length === 0}
		<p class="text-sm text-muted-foreground">This group has no MIDI melodies.</p>
	{:else}
		<div class="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,18rem)_minmax(0,1fr)]">
			<!-- Melody list -->
			<div class="flex flex-col gap-2">
				<div class="flex items-center justify-between text-xs text-muted-foreground">
					<span class="font-semibold uppercase tracking-[0.18em]">{data.group?.name}</span>
					<span>{labelled} / {items.length} labelled</span>
				</div>
				<ScrollArea class="h-[32rem] rounded-xl bg-card ring-1 ring-foreground/10">
					<ul class="flex flex-col p-1">
						{#each items as item, position (item.melodyId)}
							<li>
								<button
									type="button"
									id={`label-item-${item.melodyId}`}
									class={cn(
										"flex w-full flex-col gap-0.5 rounded-lg px-3 py-2 text-left transition-colors hover:bg-muted/60",
										position === index && "bg-muted"
									)}
									onclick={() => goTo(position)}
								>
									{#if item.sections.length > 0}
										<span class="truncate text-[10px] uppercase tracking-[0.12em] text-muted-foreground">{item.sections.join(" › ")}</span>
									{/if}
									<span class="flex items-center justify-between gap-2">
										<span class="truncate text-sm">{position + 1}. {item.name}</span>
										<KeyBadge key={item.key} />
									</span>
								</button>
							</li>
						{/each}
					</ul>
				</ScrollArea>
			</div>

			<!-- Editor -->
			{#if current}
				<div class="flex flex-col gap-5">
					<div class="flex flex-wrap items-start justify-between gap-3 rounded-xl bg-card p-4 ring-1 ring-foreground/10">
						<div class="flex min-w-0 flex-col gap-1">
							{#if current.sections.length > 0}
								<span class="text-[11px] uppercase tracking-[0.12em] text-muted-foreground">{current.sections.join(" › ")}</span>
							{/if}
							<h2 class="truncate text-lg font-semibold">{index + 1}. {current.name}</h2>
							<span class="font-mono text-xs text-muted-foreground">{current.fileName}</span>
						</div>
						<div class="flex items-center gap-2">
							<Button size="sm" variant="secondary" onclick={togglePlayback} disabled={loadingAudio}>
								{#if loadingAudio}
									<Loader data-icon="inline-start" class="animate-spin" />
									Loading
								{:else if playing}
									<Square data-icon="inline-start" />
									Stop
								{:else}
									<Play data-icon="inline-start" />
									Play
								{/if}
							</Button>
							<Label class="flex items-center gap-2 text-xs text-muted-foreground">
								<Checkbox bind:checked={autoplay} aria-label="Play automatically when a melody is selected" />
								Auto-play
							</Label>
						</div>
					</div>
					{#if audioError}
						<p class="text-xs text-destructive">{audioError}</p>
					{/if}

					<KeyEditor value={current.key} onchange={setKey} showHotkeys bind:nameInput />

					<div class="flex flex-wrap items-center justify-between gap-3">
						<div class="flex items-center gap-2 text-xs text-muted-foreground" role="status">
							{#if saveState === "saving"}
								<Loader class="size-3.5 animate-spin" />
								Saving…
							{:else if saveState === "saved"}
								<CloudCheck class="size-3.5" />
								Saved
							{:else if saveState === "error"}
								<CloudOff class="size-3.5 text-destructive" />
								<span class="text-destructive">{saveError ?? "Saving failed"}</span>
							{:else}
								<span>Changes save automatically.</span>
							{/if}
						</div>
						<div class="flex items-center gap-1">
							<Button size="sm" variant="ghost" onclick={copyPreviousKey} disabled={index === 0} title="Copy the previous melody's key (P)">
								<Copy data-icon="inline-start" />
								Same as previous
							</Button>
							<Button size="sm" variant="outline" onclick={() => goTo(index - 1)} disabled={index === 0}>
								<ChevronUp data-icon="inline-start" />
								Previous
							</Button>
							<Button size="sm" onclick={() => goTo(index + 1)} disabled={index >= items.length - 1}>
								Next
								<ChevronDown data-icon="inline-end" />
							</Button>
						</div>
					</div>

					<div class="flex flex-wrap gap-x-5 gap-y-2 text-xs text-muted-foreground">
						<span class="flex items-center gap-1.5"><Kbd>Space</Kbd> play / stop</span>
						<span class="flex items-center gap-1.5"><KbdGroup><Kbd>Enter</Kbd><Kbd>J</Kbd></KbdGroup> next</span>
						<span class="flex items-center gap-1.5"><Kbd>K</Kbd> previous</span>
						<span class="flex items-center gap-1.5"><Kbd>P</Kbd> same as previous</span>
						<span class="flex items-center gap-1.5"><Kbd>⌫</Kbd> clear</span>
						<span class="flex items-center gap-1.5"><Kbd>/</Kbd> name the scale</span>
						<Badge variant="ghost" class="text-muted-foreground">Digits and letters edit the key while nothing is focused</Badge>
					</div>
				</div>
			{/if}
		</div>
	{/if}
</section>
