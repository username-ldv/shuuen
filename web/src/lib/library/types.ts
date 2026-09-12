import type { MelodyKey } from "./key";

/** Mirrors the course and catalog schemas in `backend/openapi.yaml`. */

export type CourseSection = {
	library_group_id: number;
	name: string;
	path: string;
	depth: number;
};

export type ProgressionGroup = {
	id: string;
	library_group_id: number;
	name: string;
	description: string;
	sort_order: number;
	level_count: number;
	section_count: number;
	blueprint: boolean;
};

export type CourseModeName = "singles" | "melodies" | "chords";

export type CourseMode = {
	mode: CourseModeName;
	name: string;
	description: string;
	sort_order: number;
	group_count: number;
	level_count: number;
	groups?: ProgressionGroup[];
};

export type StructureSource = "blueprint" | "managed";

export type Course = {
	id: number;
	library_group_id: number;
	slug: string;
	name: string;
	description: string;
	author: string;
	is_public: boolean;
	sort_order: number;
	structure_source: StructureSource;
	mode_count: number;
	progression_group_count: number;
	level_count: number;
	modes?: CourseMode[];
};

export type Pitch =
	| "C"
	| "CSharp"
	| "D"
	| "DSharp"
	| "E"
	| "F"
	| "FSharp"
	| "G"
	| "GSharp"
	| "A"
	| "ASharp"
	| "B";

export type ScaleTypeName = "Major" | "NaturalMinor" | "Chromatic" | "Custom";

export type AbsoluteScaleConfig = {
	type?: "absolute";
	root: Pitch;
	scale_type: ScaleTypeName;
	pitch_states: { pitch: Pitch; active: boolean }[];
};

export type RelativeScaleConfig = {
	type?: "relative";
	scale_type: ScaleTypeName;
	degree_states: { degree: string; active: boolean }[];
};

export type NoteRange = { from: { midi_index: number }; to: { midi_index: number } };

export type MidiFileReference =
	| { type: "backend"; melody_id: number; variant_id: number; file_name: string }
	| { type: "local"; path: string; file_name: string };

/**
 * Only the fields the Library pages summarize are typed; the definition stays
 * open so unknown parameters round-trip untouched on rename.
 */
export type LevelDefinition = {
	context?: unknown;
	questions_number?: number | null;
	range?: NoteRange;
	chord_size?: { min: number; max: number };
	sustain_notes?: boolean;
	answer_order?: string;
	level_config?: {
		type?: "absolute" | "relative";
		scales?: AbsoluteScaleConfig[];
		scale_config?: RelativeScaleConfig;
		rotate_every_questions?: number | null;
		tune_inconsistency_cents?: number;
		[key: string]: unknown;
	};
	config?: {
		type?: "random" | "midi";
		scale_config?: AbsoluteScaleConfig | RelativeScaleConfig;
		questions_number?: number | null;
		notes_per_sequence?: number | null;
		tempo?: number;
		range?: NoteRange;
		rotate_every_questions?: number | null;
		melody_style?: { name?: string };
		file?: MidiFileReference;
		use_original_velocities?: boolean;
		key?: MelodyKey | null;
		[key: string]: unknown;
	};
	[key: string]: unknown;
};

export type CourseMidiResource = {
	melody_id: number;
	variant_id: number;
	download_url: string;
};

export type CourseLevel = {
	id: string;
	progression_group_id: string;
	name: string;
	source: "built_in" | "user" | "imported";
	definition: LevelDefinition;
	sort_order: number;
	is_public: boolean;
	midi?: CourseMidiResource;
	sections: CourseSection[];
};

export type LibraryGroup = {
	id: number;
	parent_id: number | null;
	path: string;
	name: string;
	slug: string;
	description: string;
	sort_order: number;
	is_public: boolean;
};

export type FileVariant = {
	id: number;
	melody_id: number;
	format: "midi" | "musicxml";
	original_name: string;
	is_primary: boolean;
};

export type Melody = {
	id: number;
	group_id: number;
	source_path: string;
	file_stem: string;
	title: string;
	slug: string;
	description: string;
	composer: string;
	difficulty: string;
	sort_order: number;
	is_public: boolean;
	key: MelodyKey | null;
	variants?: FileVariant[];
};

/** One row of the labeling screen: a blueprint MIDI level flattened for editing. */
export type LabelItem = {
	levelId: string;
	melodyId: number;
	variantId: number;
	name: string;
	fileName: string;
	sections: string[];
	key: MelodyKey | null;
};
