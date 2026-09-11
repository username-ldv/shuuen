package ldv.shuuen.logging

import java.time.Instant
import java.time.ZoneId
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLogFormatterTest {
  private val formatter = DesktopLogFormatter(ZoneId.of("UTC"))

  @Test
  fun `formats Napier message with fixed timestamp`() {
    val record =
      LogRecord(
        Level.FINEST,
        "[VERBOSE] AppNavigator\$navigateTo - Navigate happened",
      ).apply {
        instant = Instant.parse("2026-06-06T14:38:03.123Z")
      }

    assertEquals(
      "2026-06-06 14:38:03.123 [VERBOSE] AppNavigator\$navigateTo - Navigate happened" +
          System.lineSeparator(),
      formatter.format(record),
    )
  }

  @Test
  fun `preserves multiline throwable text without adding a blank line`() {
    val lineSeparator = System.lineSeparator()
    val message =
      listOf(
        "[ERROR] Player - Playback failed",
        "java.lang.IllegalStateException: decoder stopped",
        "\tat ldv.shuuen.Player.play(Player.kt:42)",
      ).joinToString(lineSeparator, postfix = lineSeparator)
    val record =
      LogRecord(Level.SEVERE, message).apply {
        instant = Instant.parse("2026-06-06T14:38:03.123Z")
      }

    assertEquals(
      "2026-06-06 14:38:03.123 $message",
      formatter.format(record),
    )
  }

  @Test
  fun `formats null message as empty text`() {
    val record =
      LogRecord(Level.INFO, null).apply {
        instant = Instant.parse("2026-06-06T14:38:03.123Z")
      }

    assertEquals(
      "2026-06-06 14:38:03.123 ${System.lineSeparator()}",
      formatter.format(record),
    )
  }
}
