package ldv.shuuen.bass

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class BassConstantsTest {
  @Test
  fun constantsMatchBundledJavaBindings() {
    val root = moduleRoot()
    val bass = javaIntConstants(root.resolve("src/androidMain/java/com/un4seen/bass/BASS.java"))
    val midi = javaIntConstants(root.resolve("src/androidMain/java/com/un4seen/bass/BASSMIDI.java"))

    assertMatches(bass, "BASS_OK", Bass.BASS_OK)
    assertMatches(bass, "BASS_ERROR_INIT", Bass.BASS_ERROR_INIT)
    assertMatches(bass, "BASS_CONFIG_BUFFER", Bass.BASS_CONFIG_BUFFER)
    assertMatches(bass, "BASS_CONFIG_UPDATEPERIOD", Bass.BASS_CONFIG_UPDATEPERIOD)
    assertMatches(bass, "BASS_CONFIG_DEV_BUFFER", Bass.BASS_CONFIG_DEV_BUFFER)
    assertMatches(bass, "BASS_CONFIG_DEV_PERIOD", Bass.BASS_CONFIG_DEV_PERIOD)
    assertMatches(bass, "BASS_ATTRIB_BUFFER", Bass.BASS_ATTRIB_BUFFER)
    assertMatches(bass, "BASS_ATTRIB_VOL", Bass.BASS_ATTRIB_VOL)
    assertMatches(bass, "BASS_STREAM_DECODE", Bass.BASS_STREAM_DECODE)
    assertMatches(midi, "BASS_MIDI_DECAYEND", Bass.BASS_MIDI_DECAYEND)
    assertMatches(midi, "BASS_CONFIG_MIDI_VOICES", Bass.BASS_CONFIG_MIDI_VOICES)
    assertMatches(midi, "MIDI_EVENT_NOTE", Bass.MIDI_EVENT_NOTE)
    assertMatches(midi, "MIDI_EVENT_PROGRAM", Bass.MIDI_EVENT_PROGRAM)
    assertMatches(midi, "MIDI_EVENT_BANK", Bass.MIDI_EVENT_BANK)
    assertMatches(midi, "MIDI_EVENT_BANK_LSB", Bass.MIDI_EVENT_BANK_LSB)
    assertMatches(midi, "MIDI_EVENT_VOLUME", Bass.MIDI_EVENT_VOLUME)
    assertMatches(midi, "MIDI_EVENT_EXPRESSION", Bass.MIDI_EVENT_EXPRESSION)
    assertMatches(midi, "MIDI_EVENT_REVERB_LEVEL", Bass.MIDI_EVENT_REVERB_LEVEL)
    assertMatches(midi, "MIDI_EVENT_CHORUS_LEVEL", Bass.MIDI_EVENT_CHORUS_LEVEL)
    assertMatches(midi, "MIDI_EVENT_CHORUS_MACRO", Bass.MIDI_EVENT_CHORUS_MACRO)
    assertMatches(midi, "MIDI_EVENT_SYSTEM", Bass.MIDI_EVENT_SYSTEM)
    assertMatches(midi, "MIDI_EVENT_SYSTEMEX", Bass.MIDI_EVENT_SYSTEMEX)
    assertMatches(midi, "MIDI_EVENT_NOTESOFF", Bass.MIDI_EVENT_NOTESOFF)
    assertMatches(midi, "MIDI_EVENT_NOTES", Bass.MIDI_EVENT_NOTES)
    assertMatches(midi, "BASS_POS_MIDI_TICK", Bass.BASS_POS_MIDI_TICK)
    assertMatches(bass, "BASS_POS_BYTE", Bass.BASS_POS_BYTE)
    assertMatches(bass, "BASS_POS_FLUSH", Bass.BASS_POS_FLUSH)
    assertMatches(bass, "BASS_ACTIVE_STOPPED", Bass.BASS_ACTIVE_STOPPED)
    assertMatches(bass, "BASS_ACTIVE_PLAYING", Bass.BASS_ACTIVE_PLAYING)
    assertMatches(bass, "BASS_ACTIVE_PAUSED", Bass.BASS_ACTIVE_PAUSED)
  }
}

private fun moduleRoot(): Path =
  sequenceOf(Path.of("."), Path.of("bass"))
    .first { it.resolve("src/androidMain/java/com/un4seen/bass/BASS.java").exists() }

private fun javaIntConstants(file: Path): Map<String, Int> {
  val constant = Regex("""public static final int\s+(\w+)\s*=\s*([^;]+);""")
  return constant.findAll(file.readText()).mapNotNull { match ->
    val name = match.groupValues[1]
    val value = match.groupValues[2].trim()
    value.toIntLiteralOrNull()?.let { name to it }
  }.toMap()
}

private fun String.toIntLiteralOrNull(): Int? =
  when {
    startsWith("0x") -> substring(2).toLong(16).toInt()
    matches(Regex("""-?\d+""")) -> toInt()
    else -> null
  }

private fun assertMatches(constants: Map<String, Int>, name: String, actual: Int) {
  assertEquals(constants[name], actual, "$name should match the bundled BASS Java binding")
}
