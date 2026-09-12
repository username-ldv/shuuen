<script lang="ts">
	import { Button } from "$lib/components/ui/button";
	import { Badge } from "$lib/components/ui/badge";
	import { Input } from "$lib/components/ui/input";
	import { Kbd } from "$lib/components/ui/kbd";
	import Eraser from "@lucide/svelte/icons/eraser";
	import {
		DEGREES,
		DEGREE_LABELS,
		PITCHES,
		SCALE_PRESETS,
		deriveScaleType,
		emptyKey,
		keyLabel,
		pitchLabel,
		recognizeScale,
		toggleDegree,
		withPreset,
		withScaleName,
		withSpelling,
		withTonic,
		type Degree,
		type MelodyKey,
		type Pitch,
	} from "$lib/library/key";
	import { cn } from "$lib/utils";

	let {
		value,
		onchange,
		showHotkeys = false,
		nameInput = $bindable(null),
	}: {
		value: MelodyKey | null;
		onchange: (next: MelodyKey | null) => void;
		showHotkeys?: boolean;
		nameInput?: HTMLInputElement | null;
	} = $props();

	const working = $derived(value ?? emptyKey());
	const activeDegrees = $derived(new Set(working.degrees));
	const scaleType = $derived(deriveScaleType(working.degrees));
	const recognized = $derived(recognizeScale(working.degrees));
	const suggestion = $derived(
		scaleType === "Custom" && recognized && recognized.name !== working.scale_name ? recognized.name : null
	);

	const DIGIT_HINTS: Partial<Record<Degree, string>> = {
		D1: "1",
		DF2: "⇧2",
		D2: "2",
		DF3: "⇧3",
		D3: "3",
		D4: "4",
		DS4: "⇧4",
		D5: "5",
		DF6: "⇧6",
		D6: "6",
		DF7: "⇧7",
		D7: "7",
	};

	function pickTonic(pitch: Pitch) {
		onchange(withTonic(working, pitch, working.spelling));
	}

	function pickDegree(degree: Degree, event: MouseEvent) {
		onchange(toggleDegree(working, degree, !event.shiftKey));
	}
</script>

<div class="flex flex-col gap-5">
	<!-- Tonic -->
	<div class="flex flex-col gap-2">
		<div class="flex items-center justify-between gap-3">
			<span class="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Tonic</span>
			<div class="flex items-center gap-1">
				<Button
					size="sm"
					variant={working.spelling === "sharps" ? "default" : "outline"}
					onclick={() => onchange(withSpelling(working, "sharps"))}
					title="Spell the key with sharps"
				>
					♯
				</Button>
				<Button
					size="sm"
					variant={working.spelling === "flats" ? "default" : "outline"}
					onclick={() => onchange(withSpelling(working, "flats"))}
					title="Spell the key with flats"
				>
					♭
				</Button>
			</div>
		</div>
		<div class="grid grid-cols-12 gap-1">
			{#each PITCHES as pitch (pitch)}
				<Button
					size="sm"
					variant={working.tonic === pitch && value ? "default" : "outline"}
					class={cn("px-0 font-medium", pitch.endsWith("Sharp") && "text-muted-foreground", working.tonic === pitch && value && "text-primary-foreground")}
					onclick={() => pickTonic(pitch)}
				>
					{pitchLabel(pitch, working.spelling)}
				</Button>
			{/each}
		</div>
		{#if showHotkeys}
			<p class="text-xs text-muted-foreground">
				<Kbd>A</Kbd>–<Kbd>G</Kbd> pick a natural tonic, <Kbd>=</Kbd> raises it (sharps), <Kbd>-</Kbd> lowers it (flats).
			</p>
		{/if}
	</div>

	<!-- Presets -->
	<div class="flex flex-col gap-2">
		<span class="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Scale</span>
		<div class="flex flex-wrap gap-1">
			{#each SCALE_PRESETS as preset (preset.id)}
				<Button
					size="sm"
					variant={recognized?.id === preset.id ? "secondary" : "ghost"}
					onclick={() => onchange(withPreset(working, preset))}
				>
					{preset.name}
					{#if showHotkeys && preset.hotkey}
						<Kbd>{preset.hotkey}</Kbd>
					{/if}
				</Button>
			{/each}
		</div>
	</div>

	<!-- Degrees -->
	<div class="flex flex-col gap-2">
		<span class="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Degrees</span>
		<div class="grid grid-cols-12 gap-1">
			{#each DEGREES as degree (degree)}
				{@const active = activeDegrees.has(degree) && value !== null}
				<Button
					size="sm"
					variant={active ? "default" : "outline"}
					class={cn("flex-col gap-0 px-0", !active && degree.length === 3 && "text-muted-foreground")}
					onclick={(event) => pickDegree(degree, event)}
					title={`Toggle ${DEGREE_LABELS[degree]} (shift-click keeps its sibling)`}
				>
					<span>{DEGREE_LABELS[degree]}</span>
				</Button>
			{/each}
		</div>
		{#if showHotkeys}
			<div class="grid grid-cols-12 gap-1 text-center text-[10px] text-muted-foreground">
				{#each DEGREES as degree (degree)}
					<span>{DIGIT_HINTS[degree]}</span>
				{/each}
			</div>
			<p class="text-xs text-muted-foreground">
				A digit toggles the natural degree and drops its altered sibling; <Kbd>⇧</Kbd>+digit toggles ♭2 ♭3 ♯4 ♭6 ♭7 instead.
			</p>
		{/if}
	</div>

	<!-- Result -->
	<div class="flex flex-col gap-2 rounded-xl bg-card p-3 ring-1 ring-foreground/10">
		<div class="flex flex-wrap items-center gap-2">
			{#if value}
				<span class="text-sm font-semibold">{keyLabel(working)}</span>
				<Badge variant="outline">{scaleType}</Badge>
			{:else}
				<span class="text-sm text-muted-foreground">No key yet. Pick a tonic to start.</span>
			{/if}
			<div class="ml-auto">
				<Button size="sm" variant="ghost" onclick={() => onchange(null)} disabled={!value}>
					<Eraser data-icon="inline-start" />
					Clear
				</Button>
			</div>
		</div>
		<div class="flex items-center gap-2">
			<Input
				bind:ref={nameInput}
				value={working.scale_name ?? ""}
				placeholder={scaleType === "Custom" ? "Scale name (optional), e.g. Lydian" : `${scaleType === "NaturalMinor" ? "Natural minor" : scaleType} needs no name`}
				disabled={!value || scaleType !== "Custom"}
				aria-label="Scale name"
				oninput={(event) => onchange(withScaleName(working, event.currentTarget.value))}
			/>
			{#if suggestion}
				<Button size="sm" variant="secondary" onclick={() => onchange(withScaleName(working, suggestion))}>
					Use “{suggestion}”
				</Button>
			{/if}
		</div>
	</div>
</div>
