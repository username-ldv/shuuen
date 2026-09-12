<script lang="ts">
	import { Badge } from "$lib/components/ui/badge";
	import { degreesLabel, keyLabel, type MelodyKey } from "$lib/library/key";

	let { key, showDegrees = false }: { key: MelodyKey | null; showDegrees?: boolean } = $props();
</script>

{#if key}
	<span class="inline-flex items-center gap-2">
		<Badge variant="outline" title={degreesLabel(key.degrees)}>{keyLabel(key)}</Badge>
		{#if showDegrees}
			<span class="font-mono text-xs text-muted-foreground">{degreesLabel(key.degrees)}</span>
		{/if}
	</span>
{:else}
	<Badge variant="ghost" class="text-muted-foreground">Unlabelled</Badge>
{/if}
