<script lang="ts">
	import { Badge } from "$lib/components/ui/badge";
	import FileMusic from "@lucide/svelte/icons/file-music";
	import Sparkles from "@lucide/svelte/icons/sparkles";
	import type { StructureSource } from "$lib/library/types";

	let { source }: { source: StructureSource } = $props();
</script>

{#if source === "blueprint"}
	<Badge variant="secondary" title="Levels are the MIDI files in this folder">
		<FileMusic />
		File-based
	</Badge>
{:else}
	<Badge variant="outline" title="Levels are generated from stored parameters">
		<Sparkles />
		Level-based
	</Badge>
{/if}
