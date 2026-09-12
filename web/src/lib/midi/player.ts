/**
 * A small in-browser MIDI preview for the labeling screen: parses a Standard
 * MIDI File with @tonejs/midi and plays every note on a Tone.js synth. Tone
 * is imported lazily so the module is safe to reference during SSR.
 */
import type * as ToneModule from "tone";

type ToneLib = typeof ToneModule;

type ScheduledNote = { time: number; name: string; duration: number; velocity: number };

export type MidiPlayer = {
	load(bytes: ArrayBuffer): Promise<{ duration: number; noteCount: number }>;
	play(): Promise<void>;
	stop(): void;
	toggle(): Promise<void>;
	readonly playing: boolean;
	dispose(): void;
};

export function createMidiPlayer(onStateChange: (playing: boolean) => void): MidiPlayer {
	let tone: ToneLib | null = null;
	let synth: ToneModule.PolySynth | null = null;
	let part: ToneModule.Part<ScheduledNote> | null = null;
	let endEvent: number | null = null;
	let duration = 0;
	let playing = false;

	async function ensureTone() {
		if (!tone) {
			tone = await import("tone");
		}
		if (!synth) {
			synth = new tone.PolySynth(tone.Synth, {
				oscillator: { type: "triangle" },
				envelope: { attack: 0.01, decay: 0.15, sustain: 0.4, release: 0.3 },
				volume: -10,
			}).toDestination();
			synth.maxPolyphony = 64;
		}
		return tone;
	}

	function setPlaying(value: boolean) {
		if (playing === value) return;
		playing = value;
		onStateChange(value);
	}

	function clearSchedule() {
		if (!tone) return;
		const transport = tone.getTransport();
		transport.stop();
		if (endEvent !== null) {
			transport.clear(endEvent);
			endEvent = null;
		}
		part?.dispose();
		part = null;
		transport.position = 0;
		synth?.releaseAll();
	}

	async function load(bytes: ArrayBuffer) {
		const [lib, { Midi }] = await Promise.all([ensureTone(), import("@tonejs/midi")]);
		clearSchedule();
		setPlaying(false);
		const midi = new Midi(bytes);
		const notes: ScheduledNote[] = midi.tracks.flatMap((track) =>
			track.notes.map((note) => ({
				time: note.time,
				name: note.name,
				duration: Math.max(note.duration, 0.05),
				velocity: note.velocity,
			}))
		);
		duration = midi.duration;
		part = new lib.Part<ScheduledNote>((time, note) => {
			synth?.triggerAttackRelease(note.name, note.duration, time, note.velocity);
		}, notes).start(0);
		return { duration, noteCount: notes.length };
	}

	async function play() {
		const lib = await ensureTone();
		if (!part) return;
		await lib.start();
		const transport = lib.getTransport();
		transport.stop();
		transport.position = 0;
		if (endEvent !== null) transport.clear(endEvent);
		endEvent = transport.scheduleOnce(() => {
			transport.stop();
			transport.position = 0;
			setPlaying(false);
		}, duration + 0.3);
		transport.start();
		setPlaying(true);
	}

	function stop() {
		if (!tone) return;
		const transport = tone.getTransport();
		transport.stop();
		transport.position = 0;
		synth?.releaseAll();
		setPlaying(false);
	}

	return {
		load,
		play,
		stop,
		async toggle() {
			if (playing) stop();
			else await play();
		},
		get playing() {
			return playing;
		},
		dispose() {
			clearSchedule();
			synth?.dispose();
			synth = null;
		},
	};
}
