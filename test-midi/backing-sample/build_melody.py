# Builds the melody-only MIDI for the backing-track sample:
# "Zankoku na Tenshi no These" (A Cruel Angel's Thesis) - Yoko Takahashi, TV size.
#
# Pitch sequence: Online Sequencer transcription https://onlinesequencer.net/76666
#   (instrument 0, top note per onset; grid unit = 1/16 note).
# Timing: the ranked osu! beatmap set https://osu.ppy.sh/beatmapsets/672960
#   (its audio.mp3 is the reference recording). Its .osu timing points:
#     997 ms        beat = 750 ms       (80 BPM)    slow intro
#     13934 ms      beat = 466.417910447761 ms (128.64 BPM), bar grid resets at 14867 ms
#     kiai (chorus) 67105 ms; chorus vocal pickup = 66989 ms (the map's PreviewTime);
#     last object 88910 ms.
#
# Mapping (verified against those anchors):
#   intro  (OS units 16..140):  8 units = 1 beat @ 80 BPM,  unit 16  <-> 997 ms
#   body   (OS units 156..790): 4 units = 1 beat @ 128.64,  unit 156 <-> 14867 ms
#   => the level's backingOffsetMs = 997 (MIDI time zero sits at 997 ms in the mp3).
#
# Run:  python build_melody.py   (needs: pip install mido)

import mido

TPQ = 480
INTRO_TEMPO = 750_000          # 80 BPM
BODY_TEMPO = 466_418           # 128.64 BPM (466417.9104... rounded to whole us)
TEMPO_CHANGE_TICK = 8280       # 17.25 intro beats * 480
BODY_BAR1_TICK = 9240          # 2 beats after the tempo change (14867 ms)

# "pitch@unit:len" from the Online Sequencer melody line, through the first chorus.
MELODY = """
C5@16:2 D#5@24:2 F5@32:2 D#5@38:2 F5@44:2 F5@48:2 F5@52:2 A#5@56:2 G#5@60:2 G5@64:2 F5@66:2 G5@70:2
G5@80:2 A#5@88:2 C6@96:2 F5@102:2 D#5@108:2 A#5@112:2 A#5@116:2 G5@120:2 A#5@124:2 A#5@128:2 C6@134:2
C5@156:2 D#5@160:2 F5@164:2 D#5@167:1 D#5@170:2 F5@172:2 F5@174:2 A#5@176:2 G#5@178:2 G5@180:1 F5@181:1 G5@183:1
G5@188:2 A#5@192:2 C6@196:2 F5@199:1 D#5@202:2 A#5@204:2 A#5@206:2 G5@208:2 A#5@210:2 A#5@212:2 C6@215:1
D#6@222:2 A#5@224:1 A#5@225:1 D#6@231:1 D#6@233:1 F6@236:2 A#5@238:1 A#5@239:1 A#5@245:1
G6@247:1 G#6@250:1 G6@253:1 F6@255:1 D#6@258:1 F6@261:1 G6@263:1 G#6@266:1 G6@269:1 C6@271:1
C6@277:1 D6@278:1 D#6@279:1 D#6@282:1 D6@285:1 D6@287:1 D#6@293:1 F6@294:1 G#6@295:1 G6@298:1 F6@301:1 D#6@303:1
G6@309:1 G6@311:1 F6@314:1 E6@317:1 F6@319:1 C6@323:1
D#6@347:1 A#5@349:1 A#5@350:1 G5@355:1 A#5@357:1 D#6@359:1 D#6@361:1 F6@365:1 A#5@367:1 A#5@368:1 A#5@376:1
G6@378:1 G#6@381:1 G6@384:2 F6@386:1 D#6@389:1 F6@392:2 G6@394:1 G#6@397:1 G6@400:2 C6@402:1
C6@409:1 D6@410:1 D#6@411:1 D#6@414:1 D6@417:1 D6@419:1 D#6@425:1 F6@426:1 G#6@427:1 G6@430:1 F6@433:1 D#6@435:1
G6@441:1 G6@443:1 F6@446:1 E6@449:1 F6@451:1 G6@454:1 G#6@457:1 C7@459:1 B6@463:1 C7@467:1 D7@471:1
D#6@475:1 D#6@478:1 D6@481:1 D#6@483:1 D#6@486:1 D6@489:1 F6@491:1 F6@494:1 D#6@497:1 D6@499:1 C6@502:1 D6@505:1
D#6@507:1 D#6@510:1 D6@513:1 F6@515:1 D6@518:1 C6@521:1
F6@523:1 G6@527:1 G#6@531:1 A#6@535:1 D#6@539:1 D#6@542:1 D6@545:1 D#6@547:1 D#6@550:1 D6@553:1
F6@555:1 F6@558:1 D#6@561:1 D6@563:1 D#6@566:1 F6@569:1 G6@571:1 G#6@574:1 G6@577:1 F6@579:1 D#6@582:1 F6@585:1
C6@603:1 D#6@607:1 F6@611:1 D#6@614:1 D#6@617:1 F6@619:1 F6@621:1 A#6@623:1 G#6@625:1 G6@627:1 F6@628:1 G6@630:1
G6@635:1 A#6@639:1 C7@643:1 F6@646:1 D#6@649:1 D6@651:1 D6@653:1 C6@655:1 D6@657:1 F6@659:1 D#6@660:1 D#6@662:1
C6@667:1 D#6@671:1 F6@675:1 D#6@678:1 D#6@681:1 F6@683:1 F6@685:1 A#6@687:1 G#6@689:1 G6@691:1 F6@692:1 G6@694:1
G6@699:1 A#6@703:1 C7@707:1 F6@710:1 D#6@713:1 A#6@715:1 A#6@717:1 G6@719:1 A#6@721:1 A#6@723:1 C7@726:1
C5@731:1 D#5@735:1 F5@739:1 D#5@742:1 D#5@745:1 F5@747:1 F5@749:1 A#5@751:1 G#5@753:1 G5@755:1 F5@756:1 G5@758:1
G5@763:1 A#5@767:1 C6@771:1 F5@774:1 D#5@777:1 A#5@779:1 A#5@781:1 G5@783:1 A#5@785:1 A#5@787:1 C6@790:1
""".split()

NOTE_OFFSETS = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5,
                "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}

# Past the hook, the transcription drifts into its piano arrangement instead of the sung
# melody, so the sample ends with the hook (intro 23 notes + hook 23 notes). The data above
# is kept in full in case the later sections ever get re-transcribed from the vocals.
LAST_UNIT = 215


def parse(entry):
    pitch, rest = entry.split("@")
    unit, length = rest.split(":")
    name, octave = (pitch[:-1], int(pitch[-1]))
    midi = (octave + 1) * 12 + NOTE_OFFSETS[name]
    return midi, float(unit), float(length)


def tick_for_unit(unit):
    if unit <= 140:  # intro: 8 units per 80-BPM beat, unit 16 == tick 0
        return round((unit - 16) * 60)  # 480 / 8
    # body: 4 units per 128.64-BPM beat, unit 156 == bar 1 (tick 9240)
    return round(BODY_BAR1_TICK + (unit - 156) * 120)  # 480 / 4


def tick_to_ms(tick):
    if tick <= TEMPO_CHANGE_TICK:
        return tick / TPQ * INTRO_TEMPO / 1000
    intro_ms = TEMPO_CHANGE_TICK / TPQ * INTRO_TEMPO / 1000
    return intro_ms + (tick - TEMPO_CHANGE_TICK) / TPQ * 466.417910447761


def main():
    events = []  # (tick, on/off, midi note)
    notes = []
    for entry in MELODY:
        midi, unit, _length = parse(entry)
        if unit > LAST_UNIT:
            continue
        # The hook lead (units 156..215) is the instrumental synth playing in its own high
        # register; every sung section sits an octave below where the transcription wrote it.
        if not 156 <= unit <= 215:
            midi -= 12
        notes.append((tick_for_unit(unit), midi))
    notes.sort()

    two_beats = 2 * TPQ
    for i, (tick, midi) in enumerate(notes):
        next_tick = notes[i + 1][0] if i + 1 < len(notes) else tick + two_beats
        duration = min(max(next_tick - tick - 30, 60), two_beats)
        events.append((tick, 1, midi))
        events.append((tick + duration, 0, midi))
    events.sort(key=lambda e: (e[0], e[1]))

    mid = mido.MidiFile(type=0, ticks_per_beat=TPQ)
    track = mido.MidiTrack()
    mid.tracks.append(track)
    track.append(mido.MetaMessage("track_name",
                                  name="Cruel Angel's Thesis (TV size) - vocal melody", time=0))
    track.append(mido.MetaMessage("set_tempo", tempo=INTRO_TEMPO, time=0))

    pending = [(TEMPO_CHANGE_TICK, "tempo")] + [(t, kind, n) for t, kind, n in events]
    pending.sort(key=lambda e: (e[0], 0 if e[1] == "tempo" else 1))
    last_tick = 0
    for event in pending:
        delta = event[0] - last_tick
        last_tick = event[0]
        if event[1] == "tempo":
            track.append(mido.MetaMessage("set_tempo", tempo=BODY_TEMPO, time=delta))
        elif event[1] == 1:
            track.append(mido.Message("note_on", note=event[2], velocity=100, time=delta))
        else:
            track.append(mido.Message("note_off", note=event[2], velocity=0, time=delta))
    track.append(mido.MetaMessage("end_of_track", time=0))

    out = "cruel-angels-thesis-tv-melody.mid"
    mid.save(out)

    # Verify the anchors against the beatmap's measured positions (mp3 ms = midi ms + 997).
    checks = [
        (16, 997, "intro start"),
        (156, 14867, "body bar 1 (hook)"),
        (215, 21745, "hook's final note"),
    ]
    for unit, expected_ms, label in checks:
        got = tick_to_ms(tick_for_unit(unit)) + 997
        print(f"{label}: {got:.0f} ms (expected ~{expected_ms}) diff {got - expected_ms:+.0f}")
    print(f"saved {out}: {len(notes)} notes, length {mid.length:.1f}s")


if __name__ == "__main__":
    main()
