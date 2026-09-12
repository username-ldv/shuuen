/**
 * Key labels for MIDI melodies, mirroring `course.MelodyKey` in the backend and
 * the app's Pitch / Degree / ScaleType enums. Everything here is pure so the
 * labeling screen's hotkeys and the key editor share one rule set.
 */

export const PITCHES = [
	"C",
	"CSharp",
	"D",
	"DSharp",
	"E",
	"F",
	"FSharp",
	"G",
	"GSharp",
	"A",
	"ASharp",
	"B",
] as const;
export type Pitch = (typeof PITCHES)[number];

/** Rising chromatic order from the tonic; also the canonical storage order. */
export const DEGREES = [
	"D1",
	"DF2",
	"D2",
	"DF3",
	"D3",
	"D4",
	"DS4",
	"D5",
	"DF6",
	"D6",
	"DF7",
	"D7",
] as const;
export type Degree = (typeof DEGREES)[number];

export const DEGREE_LABELS: Record<Degree, string> = {
	D1: "1",
	DF2: "♭2",
	D2: "2",
	DF3: "♭3",
	D3: "3",
	D4: "4",
	DS4: "♯4",
	D5: "5",
	DF6: "♭6",
	D6: "6",
	DF7: "♭7",
	D7: "7",
};

export type Spelling = "sharps" | "flats";
export type ScaleType = "Major" | "NaturalMinor" | "Chromatic" | "Custom";

export type MelodyKey = {
	tonic: Pitch;
	spelling: Spelling;
	degrees: Degree[];
	/** Derived by the server from `degrees`; recomputed locally with {@link deriveScaleType}. */
	scale_type?: ScaleType;
	scale_name?: string;
};

export const MAJOR_DEGREES: Degree[] = ["D1", "D2", "D3", "D4", "D5", "D6", "D7"];
export const NATURAL_MINOR_DEGREES: Degree[] = ["D1", "D2", "DF3", "D4", "D5", "DF6", "DF7"];

export type ScalePreset = {
	id: string;
	name: string;
	degrees: Degree[];
	/** Single letter shown on the preset button and bound as a hotkey. */
	hotkey?: string;
};

/**
 * Well-known degree sets. The first match by set equality names a custom scale
 * in the editor; Major and Natural minor are the app's built-in scale types.
 */
export const SCALE_PRESETS: ScalePreset[] = [
	{ id: "major", name: "Major", degrees: MAJOR_DEGREES, hotkey: "M" },
	{ id: "natural-minor", name: "Natural minor", degrees: NATURAL_MINOR_DEGREES, hotkey: "N" },
	{ id: "harmonic-minor", name: "Harmonic minor", degrees: ["D1", "D2", "DF3", "D4", "D5", "DF6", "D7"], hotkey: "H" },
	{ id: "melodic-minor", name: "Melodic minor", degrees: ["D1", "D2", "DF3", "D4", "D5", "D6", "D7"] },
	{ id: "dorian", name: "Dorian", degrees: ["D1", "D2", "DF3", "D4", "D5", "D6", "DF7"] },
	{ id: "phrygian", name: "Phrygian", degrees: ["D1", "DF2", "DF3", "D4", "D5", "DF6", "DF7"] },
	{ id: "lydian", name: "Lydian", degrees: ["D1", "D2", "D3", "DS4", "D5", "D6", "D7"] },
	{ id: "mixolydian", name: "Mixolydian", degrees: ["D1", "D2", "D3", "D4", "D5", "D6", "DF7"] },
	{ id: "locrian", name: "Locrian", degrees: ["D1", "DF2", "DF3", "D4", "DS4", "DF6", "DF7"] },
	{ id: "major-pentatonic", name: "Major pentatonic", degrees: ["D1", "D2", "D3", "D5", "D6"] },
	{ id: "minor-pentatonic", name: "Minor pentatonic", degrees: ["D1", "DF3", "D4", "D5", "DF7"] },
	{ id: "blues", name: "Blues", degrees: ["D1", "DF3", "D4", "DS4", "D5", "DF7"] },
	{ id: "whole-tone", name: "Whole tone", degrees: ["D1", "D2", "D3", "DS4", "DF6", "DF7"] },
	{ id: "chromatic", name: "Chromatic", degrees: [...DEGREES] },
];

/** Altered degree that shares a step with the natural one (and vice versa). */
const SIBLINGS: Partial<Record<Degree, Degree>> = {
	D2: "DF2",
	DF2: "D2",
	D3: "DF3",
	DF3: "D3",
	D4: "DS4",
	DS4: "D4",
	D6: "DF6",
	DF6: "D6",
	D7: "DF7",
	DF7: "D7",
};

/** Digit row → natural degree, and with Shift → its altered sibling. */
export const DIGIT_DEGREES: Record<string, { natural: Degree; altered?: Degree }> = {
	Digit1: { natural: "D1" },
	Digit2: { natural: "D2", altered: "DF2" },
	Digit3: { natural: "D3", altered: "DF3" },
	Digit4: { natural: "D4", altered: "DS4" },
	Digit5: { natural: "D5" },
	Digit6: { natural: "D6", altered: "DF6" },
	Digit7: { natural: "D7", altered: "DF7" },
};

const NATURAL_LETTERS: Record<string, Pitch> = {
	C: "C",
	D: "D",
	E: "E",
	F: "F",
	G: "G",
	A: "A",
	B: "B",
};

const SHARP_NAMES: Record<Pitch, string> = {
	C: "C",
	CSharp: "C♯",
	D: "D",
	DSharp: "D♯",
	E: "E",
	F: "F",
	FSharp: "F♯",
	G: "G",
	GSharp: "G♯",
	A: "A",
	ASharp: "A♯",
	B: "B",
};

const FLAT_NAMES: Record<Pitch, string> = {
	C: "C",
	CSharp: "D♭",
	D: "D",
	DSharp: "E♭",
	E: "E",
	F: "F",
	FSharp: "G♭",
	G: "G",
	GSharp: "A♭",
	A: "A",
	ASharp: "B♭",
	B: "B",
};

export function isAccidental(pitch: Pitch) {
	return pitch.endsWith("Sharp");
}

export function pitchLabel(pitch: Pitch, spelling: Spelling) {
	return spelling === "flats" ? FLAT_NAMES[pitch] : SHARP_NAMES[pitch];
}

export function transposePitch(pitch: Pitch, semitones: number): Pitch {
	const index = PITCHES.indexOf(pitch);
	return PITCHES[(((index + semitones) % 12) + 12) % 12];
}

export function normalizeDegrees(degrees: Iterable<Degree>): Degree[] {
	const set = new Set(degrees);
	return DEGREES.filter((degree) => set.has(degree));
}

function sameSet(left: readonly Degree[], right: readonly Degree[]) {
	if (left.length !== right.length) return false;
	const set = new Set(right);
	return left.every((degree) => set.has(degree));
}

export function deriveScaleType(degrees: readonly Degree[]): ScaleType {
	if (sameSet(degrees, MAJOR_DEGREES)) return "Major";
	if (sameSet(degrees, NATURAL_MINOR_DEGREES)) return "NaturalMinor";
	if (sameSet(degrees, DEGREES)) return "Chromatic";
	return "Custom";
}

/** Name of the first preset whose degree set matches, or null. */
export function recognizeScale(degrees: readonly Degree[]): ScalePreset | null {
	return SCALE_PRESETS.find((preset) => sameSet(preset.degrees, degrees)) ?? null;
}

/** Keys whose conventional signature uses flats (major tonics and their relative minors). */
const FLAT_MAJOR_TONICS = new Set<Pitch>(["F", "ASharp", "DSharp", "GSharp", "CSharp", "FSharp"]);
const FLAT_MINOR_TONICS = new Set<Pitch>(["D", "G", "C", "F", "ASharp", "DSharp"]);

/**
 * Sharps or flats the way a printed key signature would choose. Minor-flavoured
 * degree sets (a flat third) follow the minor convention, everything else the
 * major one. Natural tonics only matter for accidentals inside the scale.
 */
export function defaultSpelling(tonic: Pitch, degrees: readonly Degree[]): Spelling {
	const minorish = degrees.includes("DF3") && !degrees.includes("D3");
	if (minorish) {
		if (tonic === "FSharp" || tonic === "CSharp" || tonic === "GSharp") return "sharps";
		return FLAT_MINOR_TONICS.has(tonic) ? "flats" : "sharps";
	}
	if (tonic === "FSharp") return "sharps";
	return FLAT_MAJOR_TONICS.has(tonic) ? "flats" : "sharps";
}

export function scaleLabel(key: MelodyKey) {
	const scaleType = deriveScaleType(key.degrees);
	switch (scaleType) {
		case "Major":
			return "major";
		case "NaturalMinor":
			return "natural minor";
		case "Chromatic":
			return "chromatic";
		default: {
			const name = key.scale_name?.trim();
			if (name) return name.toLowerCase();
			const recognized = recognizeScale(key.degrees);
			return recognized ? recognized.name.toLowerCase() : "custom";
		}
	}
}

/** "E♭ major", "A harmonic minor", "F custom". */
export function keyLabel(key: MelodyKey) {
	return `${pitchLabel(key.tonic, key.spelling)} ${scaleLabel(key)}`;
}

export function degreesLabel(degrees: readonly Degree[]) {
	return normalizeDegrees(degrees)
		.map((degree) => DEGREE_LABELS[degree])
		.join(" ");
}

export function emptyKey(tonic: Pitch = "C"): MelodyKey {
	return { tonic, spelling: defaultSpelling(tonic, MAJOR_DEGREES), degrees: [...MAJOR_DEGREES] };
}

/** Canonical form for comparisons and for sending to the API. */
export function normalizeKey(key: MelodyKey): MelodyKey {
	const degrees = normalizeDegrees(key.degrees);
	const name = key.scale_name?.trim();
	return {
		tonic: key.tonic,
		spelling: key.spelling,
		degrees,
		scale_type: deriveScaleType(degrees),
		...(name ? { scale_name: name } : {}),
	};
}

export function keysEqual(left: MelodyKey | null, right: MelodyKey | null) {
	if (!left || !right) return left === right;
	const a = normalizeKey(left);
	const b = normalizeKey(right);
	return (
		a.tonic === b.tonic &&
		a.spelling === b.spelling &&
		(a.scale_name ?? "") === (b.scale_name ?? "") &&
		sameSet(a.degrees, b.degrees)
	);
}

/** Letter key A–G: natural tonic, spelling re-decided from the current degrees. */
export function withTonicLetter(key: MelodyKey | null, letter: string): MelodyKey {
	const tonic = NATURAL_LETTERS[letter.toUpperCase()];
	const base = key ?? emptyKey();
	return { ...base, tonic, spelling: defaultSpelling(tonic, base.degrees) };
}

export function withTonic(key: MelodyKey | null, tonic: Pitch, spelling?: Spelling): MelodyKey {
	const base = key ?? emptyKey();
	return { ...base, tonic, spelling: spelling ?? defaultSpelling(tonic, base.degrees) };
}

/** `+` raises the tonic a semitone and spells with sharps; `-` lowers it and spells with flats. */
export function shiftTonic(key: MelodyKey | null, direction: 1 | -1): MelodyKey {
	const base = key ?? emptyKey();
	const tonic = transposePitch(base.tonic, direction);
	const spelling: Spelling = isAccidental(tonic)
		? direction > 0
			? "sharps"
			: "flats"
		: defaultSpelling(tonic, base.degrees);
	return { ...base, tonic, spelling };
}

export function withSpelling(key: MelodyKey | null, spelling: Spelling): MelodyKey {
	return { ...(key ?? emptyKey()), spelling };
}

/** Replaces the degree set with a preset, keeping the tonic and re-deciding the spelling. */
export function withPreset(key: MelodyKey | null, preset: ScalePreset): MelodyKey {
	const base = key ?? emptyKey();
	const degrees = [...preset.degrees];
	const builtIn = deriveScaleType(degrees) !== "Custom";
	return {
		...base,
		degrees,
		spelling: defaultSpelling(base.tonic, degrees),
		scale_name: builtIn ? undefined : preset.name,
	};
}

/**
 * Toggles one degree. Turning a degree on also turns off its natural/altered
 * sibling (pressing 7 in a minor scale swaps ♭7 for 7) unless `swap` is false.
 */
export function toggleDegree(key: MelodyKey | null, degree: Degree, swap = true): MelodyKey {
	const base = key ?? emptyKey();
	const set = new Set(base.degrees);
	if (set.has(degree)) {
		if (set.size > 1) set.delete(degree);
	} else {
		set.add(degree);
		const sibling = SIBLINGS[degree];
		if (swap && sibling) set.delete(sibling);
	}
	return { ...base, degrees: normalizeDegrees(set) };
}

export function withScaleName(key: MelodyKey | null, scaleName: string): MelodyKey {
	const trimmed = scaleName.trim();
	return { ...(key ?? emptyKey()), scale_name: trimmed || undefined };
}
