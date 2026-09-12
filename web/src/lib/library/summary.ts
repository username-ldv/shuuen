import { DEGREE_LABELS, keyLabel, pitchLabel, type Degree, type MelodyKey } from "./key";
import type {
	AbsoluteScaleConfig,
	CourseLevel,
	CourseModeName,
	LevelDefinition,
	NoteRange,
	RelativeScaleConfig,
} from "./types";

export type LevelFact = { label: string; value: string };

const NOTE_NAMES = ["C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"];

export function noteName(midiIndex: number) {
	const octave = Math.floor(midiIndex / 12) - 1;
	return `${NOTE_NAMES[midiIndex % 12]}${octave}`;
}

function rangeLabel(range?: NoteRange) {
	if (!range) return null;
	return `${noteName(range.from.midi_index)} – ${noteName(range.to.midi_index)}`;
}

function scaleTypeLabel(scaleType: string) {
	switch (scaleType) {
		case "Major":
			return "major";
		case "NaturalMinor":
			return "natural minor";
		case "Chromatic":
			return "chromatic";
		default:
			return "custom";
	}
}

function absoluteScaleLabel(scale: AbsoluteScaleConfig) {
	const active = scale.pitch_states.filter((state) => state.active).map((state) => pitchLabel(state.pitch, "sharps"));
	return `${pitchLabel(scale.root, "sharps")} ${scaleTypeLabel(scale.scale_type)} · ${active.join(" ")}`;
}

function relativeScaleLabel(scale: RelativeScaleConfig) {
	const active = scale.degree_states
		.filter((state) => state.active)
		.map((state) => DEGREE_LABELS[state.degree as Degree] ?? state.degree);
	return `${scaleTypeLabel(scale.scale_type)} · ${active.join(" ")}`;
}

function scaleConfigLabel(scale: AbsoluteScaleConfig | RelativeScaleConfig | undefined) {
	if (!scale) return null;
	if ("root" in scale && scale.root) return absoluteScaleLabel(scale);
	if ("degree_states" in scale) return relativeScaleLabel(scale);
	return null;
}

function push(facts: LevelFact[], label: string, value: string | number | null | undefined) {
	if (value === null || value === undefined || value === "") return;
	facts.push({ label, value: String(value) });
}

/** Human-readable parameters of a level for the Library tables. */
export function summarizeLevel(mode: CourseModeName, definition: LevelDefinition): LevelFact[] {
	const facts: LevelFact[] = [];
	if (mode === "melodies") {
		const config = definition.config ?? {};
		if (config.type === "midi") {
			push(facts, "File", config.file?.file_name);
			push(facts, "Key", config.key ? keyLabel(config.key) : "Unlabelled");
			push(facts, "Velocities", config.use_original_velocities ? "original" : "uniform");
			return facts;
		}
		push(facts, "Scale", scaleConfigLabel(config.scale_config));
		push(facts, "Tempo", config.tempo ? `${config.tempo} BPM` : null);
		push(facts, "Notes / question", config.notes_per_sequence ?? "endless stream");
		push(facts, "Questions", config.questions_number ?? "unlimited");
		push(facts, "Range", rangeLabel(config.range));
		push(facts, "Rotate tonic", config.rotate_every_questions ? `every ${config.rotate_every_questions}` : null);
		push(facts, "Style", config.melody_style?.name);
		return facts;
	}
	const levelConfig = definition.level_config ?? {};
	if (levelConfig.type === "absolute" && levelConfig.scales) {
		push(facts, "Scales", levelConfig.scales.map(absoluteScaleLabel).join(" | "));
	} else if (levelConfig.scale_config) {
		push(facts, "Scale", relativeScaleLabel(levelConfig.scale_config));
	}
	push(facts, "Questions", definition.questions_number ?? "unlimited");
	push(facts, "Range", rangeLabel(definition.range));
	push(facts, "Rotate tonic", levelConfig.rotate_every_questions ? `every ${levelConfig.rotate_every_questions}` : null);
	if (mode === "singles") {
		push(facts, "Detune", levelConfig.tune_inconsistency_cents ? `±${levelConfig.tune_inconsistency_cents} cents` : null);
	}
	if (mode === "chords") {
		push(facts, "Chord size", definition.chord_size ? `${definition.chord_size.min}–${definition.chord_size.max}` : null);
		push(facts, "Sustain", definition.sustain_notes === undefined ? null : definition.sustain_notes ? "yes" : "no");
		push(facts, "Answer order", definition.answer_order);
	}
	return facts;
}

export function levelIsMidi(level: CourseLevel) {
	return level.definition.config?.type === "midi";
}

export function levelKey(level: CourseLevel): MelodyKey | null {
	return level.definition.config?.key ?? null;
}

export function sectionTrail(level: CourseLevel) {
	return level.sections.map((section) => section.name);
}
