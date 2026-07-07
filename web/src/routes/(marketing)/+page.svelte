<script lang="ts">
  import { Button } from "$lib/components/ui/button";
  import * as Card from "$lib/components/ui/card";
  import { Separator } from "$lib/components/ui/separator";
  import LogIn from "@lucide/svelte/icons/log-in";
  import CircleUserRound from "@lucide/svelte/icons/circle-user-round";
  import Download from "@lucide/svelte/icons/download";
  import Link2 from "@lucide/svelte/icons/link-2";
  import Copy from "@lucide/svelte/icons/copy";
  import Check from "@lucide/svelte/icons/check";
  import Monitor from "@lucide/svelte/icons/monitor";
  import Smartphone from "@lucide/svelte/icons/smartphone";
  import { PUBLIC_SITE_URL } from "$env/static/public";

  // Friendly alias the native app pastes to pair with the site. In production a
  // reverse proxy forwards `${PUBLIC_SITE_URL}/link` to the backend's /api/link.
  const repoUrl = `${PUBLIC_SITE_URL}/link`;
  const appVersion = "0.0.4";

  let copied = $state(false);
  let resetTimer: ReturnType<typeof setTimeout>;

  async function copyRepo() {
    try {
      await navigator.clipboard?.writeText(repoUrl);
    } catch {
      /* clipboard unavailable — still surface the copied affordance */
    }
    copied = true;
    clearTimeout(resetTimer);
    resetTimer = setTimeout(() => (copied = false), 1800);
  }
</script>

<svelte:head>
  <title>Shuuen · The last ear trainer app you need</title>
  <meta
    name="description"
    content="A next-generation ear-training app for musicians — Windows, Linux and Android."
  />
</svelte:head>

<div class="flex min-h-screen flex-col bg-background text-foreground">
  <!-- Header -->
  <header
    class="sticky top-0 z-10 flex items-center justify-between border-b border-border bg-background/80 px-6 py-4 backdrop-blur-sm"
  >
    <a href="#top" class="flex items-center gap-3 text-foreground">
      <img src="/images/shuuen-icon.png" alt="" class="size-6 object-contain" />
      <span class="text-sm font-semibold uppercase tracking-[0.28em]"
        >Shuuen</span
      >
    </a>
    <nav class="flex items-center gap-6">
      <a
        href="#repository"
        class="hidden text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground transition-colors hover:text-foreground sm:inline"
      >
        Repository
      </a>
      <a
        href="#news"
        class="hidden text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground transition-colors hover:text-foreground sm:inline"
      >
        News
      </a>
      <Button href="#login" size="sm">
        <LogIn data-icon="inline-start" />
        Log In
      </Button>
    </nav>
  </header>

  <main class="mx-auto w-full max-w-180 flex-1 px-6 pb-28">
    <!-- Hero -->
    <section
      id="top"
      class="flex flex-col items-center pt-24 pb-20 text-center"
    >
      <span
        lang="ja-JP"
        class="mb-8 text-[10px] font-semibold uppercase tracking-[0.25em] text-muted-foreground/70"
      >
        終焉 · The End / Final Moment
      </span>
      <img
        src="/images/shuuen-logo.png"
        alt="Shuuen"
        class="mb-9 w-full max-w-90"
      />
      <h1 class="max-w-115 text-3xl font-semibold leading-tight text-balance">
        The last ear trainer app you need.
      </h1>
      <p
        class="mt-5 max-w-120 text-sm leading-relaxed text-muted-foreground text-pretty"
      >
        A next-generation ear-training app for musicians. Identify singles,
        transcribe melodies, drill chords and progressions — across Windows,
        Linux and Android. Build your own levels, set the scale, context and
        range, then practice with precision.
      </p>
      <div class="mt-9 flex flex-wrap items-center justify-center gap-3.5">
        <Button
          href="#login"
          class="h-11 px-6 has-data-[icon=inline-start]:pl-6"
        >
          <CircleUserRound data-icon="inline-start" />
          My Page
        </Button>
        <Button
          href="#download"
          variant="secondary"
          class="h-11 px-6 has-data-[icon=inline-start]:pl-6"
        >
          <Download data-icon="inline-start" />
          Download
        </Button>
      </div>
    </section>

    <!-- Repository -->
    <section id="repository" class="pt-2 pb-3">
      <div class="mb-5 flex flex-col gap-1.5">
        <span class="text-xs font-semibold uppercase tracking-[0.2em]"
          >Link This Site To The App</span
        >
        <span class="text-xs leading-relaxed text-muted-foreground">
          Copy the URL and paste it into the app to sync with the server. No
          login required.
        </span>
      </div>
      <div
        class="flex items-center gap-2 rounded-xl bg-card py-2 pr-2 pl-4 ring-1 ring-foreground/10"
      >
        <Link2 class="size-5 shrink-0 text-muted-foreground" />
        <code class="min-w-0 flex-1 truncate font-mono text-sm text-foreground"
          >{repoUrl}</code
        >
        <Button size="sm" class="shrink-0" onclick={copyRepo}>
          {#if copied}
            <Check data-icon="inline-start" />
            Copied
          {:else}
            <Copy data-icon="inline-start" />
            Copy
          {/if}
        </Button>
      </div>
    </section>

    <Separator class="my-16" />

    <!-- Download -->
    <section id="download">
      <div class="mb-7 flex flex-col gap-1.5">
        <span class="text-xs font-semibold uppercase tracking-[0.2em]"
          >Download</span
        >
        <span class="text-xs leading-relaxed text-muted-foreground">
          Free and open. Pick your platform — version {appVersion}.
        </span>
      </div>
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Card.Root>
          <Card.Header class="flex flex-col items-start gap-4">
            <div
              class="flex size-13 items-center justify-center rounded-full bg-muted"
            >
              <Monitor class="size-6 text-foreground" />
            </div>
            <div class="flex flex-col gap-1">
              <Card.Title class="text-base">PC</Card.Title>
              <Card.Description
                class="text-[11px] font-medium uppercase tracking-[0.15em]"
              >
                Windows · Linux
              </Card.Description>
            </div>
          </Card.Header>
          <Card.Footer>
            <Button size="sm" class="w-full" href="#download">
              <Download data-icon="inline-start" />
              Download
            </Button>
          </Card.Footer>
        </Card.Root>

        <Card.Root>
          <Card.Header class="flex flex-col items-start gap-4">
            <div
              class="flex size-13 items-center justify-center rounded-full bg-muted"
            >
              <Smartphone class="size-6 text-foreground" />
            </div>
            <div class="flex flex-col gap-1">
              <Card.Title class="text-base">Mobile</Card.Title>
              <Card.Description
                class="text-[11px] font-medium uppercase tracking-[0.15em]"
              >
                Android · APK
              </Card.Description>
            </div>
          </Card.Header>
          <Card.Footer>
            <Button size="sm" class="w-full" href="#download">
              <Download data-icon="inline-start" />
              Download
            </Button>
          </Card.Footer>
        </Card.Root>
      </div>
    </section>
  </main>

  <!-- Footer -->
  <footer class="border-t border-border px-6 py-7">
    <div
      class="mx-auto flex max-w-180 flex-wrap items-center justify-between gap-4"
    >
      <span
        lang="ja-JP"
        class="text-[10px] font-medium uppercase tracking-[0.2em] text-muted-foreground/70"
      >
        終焉 · Shuuen
      </span>
      <div class="flex items-center gap-6">
        <a
          href="#repository"
          class="text-[10px] font-medium uppercase tracking-[0.2em] text-muted-foreground/70 transition-colors hover:text-muted-foreground"
        >
          Repository
        </a>
        <a
          href="#news"
          class="text-[10px] font-medium uppercase tracking-[0.2em] text-muted-foreground/70 transition-colors hover:text-muted-foreground"
        >
          News
        </a>
        <a
          href="#download"
          class="text-[10px] font-medium uppercase tracking-[0.2em] text-muted-foreground/70 transition-colors hover:text-muted-foreground"
        >
          Download
        </a>
      </div>
    </div>
  </footer>
</div>
