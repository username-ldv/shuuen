package ldv.shuuen.logging

import java.io.OutputStream
import java.util.logging.ErrorManager
import java.util.logging.Formatter
import java.util.logging.Handler
import java.util.logging.LogRecord

internal class Utf8StdoutHandler(
  private val output: OutputStream = System.out,
  formatter: Formatter = DesktopLogFormatter(),
) : Handler() {
  init {
    this.formatter = formatter
  }

  @Synchronized
  override fun publish(record: LogRecord) {
    if (!isLoggable(record)) return

    try {
      output.write(formatter.format(record).toByteArray(Charsets.UTF_8))
    } catch (exception: Exception) {
      reportError(
        "Unable to write desktop log record",
        exception,
        ErrorManager.WRITE_FAILURE,
      )
      return
    }

    flush()
  }

  @Synchronized
  override fun flush() {
    try {
      output.flush()
    } catch (exception: Exception) {
      reportError(
        "Unable to flush desktop log output",
        exception,
        ErrorManager.FLUSH_FAILURE,
      )
    }
  }

  @Synchronized
  override fun close() {
    flush()
  }
}
