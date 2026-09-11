package ldv.shuuen.logging

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Formatter
import java.util.logging.LogRecord

internal class DesktopLogFormatter(
  zoneId: ZoneId = ZoneId.systemDefault(),
) : Formatter() {
  private val timestampFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(zoneId)

  override fun format(record: LogRecord): String =
    "${timestampFormatter.format(record.instant)} " +
        record.message.orEmpty().removeSuffix(System.lineSeparator()) +
        System.lineSeparator()
}
