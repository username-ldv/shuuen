<script lang="ts">
	import './layout.css';

	let { children } = $props();
</script>

<svelte:head><link rel="icon" href="/images/shuuen-icon.png" /></svelte:head>
{@render children()}
