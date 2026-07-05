package ldv.shuuen.core.audio.input

import kotlin.test.Test
import kotlin.test.assertEquals

class MidiMessageParserTest {
  private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

  @Test
  fun parsesNoteOnAndOff() {
    val parser = MidiMessageParser()
    assertEquals(
      listOf(MidiKeyboardEvent.NoteOn(60, 100), MidiKeyboardEvent.NoteOff(60)),
      parser.feed(bytes(0x90, 60, 100, 0x80, 60, 64)),
    )
  }

  @Test
  fun noteOnWithZeroVelocityIsNoteOff() {
    val parser = MidiMessageParser()
    assertEquals(
      listOf(MidiKeyboardEvent.NoteOn(60, 100), MidiKeyboardEvent.NoteOff(60)),
      parser.feed(bytes(0x90, 60, 100, 0x90, 60, 0)),
    )
  }

  @Test
  fun runningStatusReusesTheLastStatusByte() {
    val parser = MidiMessageParser()
    assertEquals(
      listOf(
        MidiKeyboardEvent.NoteOn(60, 100),
        MidiKeyboardEvent.NoteOn(64, 90),
        MidiKeyboardEvent.NoteOff(60),
      ),
      parser.feed(bytes(0x90, 60, 100, 64, 90, 60, 0)),
    )
  }

  @Test
  fun runningStatusSurvivesAcrossFeeds() {
    val parser = MidiMessageParser()
    assertEquals(
      listOf(MidiKeyboardEvent.NoteOn(60, 100)),
      parser.feed(bytes(0x90, 60, 100)),
    )
    assertEquals(listOf(MidiKeyboardEvent.NoteOn(64, 90)), parser.feed(bytes(64, 90)))
  }

  @Test
  fun messageSplitAcrossFeedsIsReassembled() {
    val parser = MidiMessageParser()
    assertEquals(emptyList(), parser.feed(bytes(0x90, 60)))
    assertEquals(listOf(MidiKeyboardEvent.NoteOn(60, 100)), parser.feed(bytes(100)))
  }

  @Test
  fun ignoresOtherChannelMessages() {
    val parser = MidiMessageParser()
    // Control change, program change, channel pressure, pitch bend around a note-on.
    assertEquals(
      listOf(MidiKeyboardEvent.NoteOn(60, 100)),
      parser.feed(bytes(0xB0, 64, 127, 0xC0, 5, 0xD0, 90, 0x90, 60, 100, 0xE0, 0, 64)),
    )
  }

  @Test
  fun realTimeBytesInterleavedInsideMessagesAreSkipped() {
    val parser = MidiMessageParser()
    assertEquals(
      listOf(MidiKeyboardEvent.NoteOn(60, 100)),
      parser.feed(bytes(0x90, 0xF8, 60, 0xFE, 100)),
    )
  }

  @Test
  fun sysExDataIsSkippedUntilItsEnd() {
    val parser = MidiMessageParser()
    assertEquals(
      listOf(MidiKeyboardEvent.NoteOn(60, 100)),
      parser.feed(bytes(0xF0, 0x7E, 0x09, 0x01, 0xF7, 0x90, 60, 100)),
    )
  }

  @Test
  fun notesOnAnyMidiChannelCount() {
    val parser = MidiMessageParser()
    // Channel 5 note-on (0x95) still counts: channel is irrelevant to answering.
    assertEquals(
      listOf(MidiKeyboardEvent.NoteOn(72, 88)),
      parser.feed(bytes(0x95, 72, 88)),
    )
  }

  @Test
  fun strayDataBytesWithoutStatusAreDropped() {
    val parser = MidiMessageParser()
    assertEquals(
      listOf(MidiKeyboardEvent.NoteOn(60, 100)),
      parser.feed(bytes(23, 42, 0x90, 60, 100)),
    )
  }
}
