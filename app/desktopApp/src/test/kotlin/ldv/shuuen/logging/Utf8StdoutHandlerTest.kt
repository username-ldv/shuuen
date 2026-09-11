package ldv.shuuen.logging

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.ErrorManager
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Utf8StdoutHandlerTest {
  @Test
  fun `writes formatted record to UTF-8 output and flushes`() {
    val output = TrackingOutputStream()
    val handler = Utf8StdoutHandler(output, DesktopLogFormatter(ZoneId.of("UTC")))
    val record =
      LogRecord(Level.INFO, "[INFO] App - 啦").apply {
        instant = Instant.parse("2026-06-06T14:38:03.123Z")
      }

    handler.publish(record)

    assertEquals(
      "2026-06-06 14:38:03.123 [INFO] App - 啦${System.lineSeparator()}",
      output.toString(Charsets.UTF_8),
    )
    assertTrue(output.wasFlushed)
  }

  @Test
  fun `close flushes without closing output`() {
    val output = TrackingOutputStream()
    val handler = Utf8StdoutHandler(output)

    handler.close()

    assertTrue(output.wasFlushed)
    assertFalse(output.wasClosed)
  }

  @Test
  fun `publish reports write failure without throwing`() {
    val failure = IOException("write failed")
    val handler = Utf8StdoutHandler(ThrowingOutputStream(failure))
    val errorManager = RecordingErrorManager()
    handler.errorManager = errorManager

    handler.publish(LogRecord(Level.INFO, "[INFO] App - message"))

    assertEquals("Unable to write desktop log record", errorManager.message)
    assertSame(failure, errorManager.exception)
    assertEquals(ErrorManager.WRITE_FAILURE, errorManager.code)
  }

  @Test
  fun `publish reports flush failure without throwing`() {
    val failure = IOException("flush failed")
    val handler = Utf8StdoutHandler(FlushThrowingOutputStream(failure))
    val errorManager = RecordingErrorManager()
    handler.errorManager = errorManager

    handler.publish(LogRecord(Level.INFO, "[INFO] App - message"))

    assertEquals("Unable to flush desktop log output", errorManager.message)
    assertSame(failure, errorManager.exception)
    assertEquals(ErrorManager.FLUSH_FAILURE, errorManager.code)
  }

  @Test
  fun `serializes concurrent publish operations`() {
    val output = BlockingOutputStream()
    val handler = Utf8StdoutHandler(output)
    val first = Thread { handler.publish(LogRecord(Level.INFO, "first")) }
    val secondStarted = CountDownLatch(1)
    val second =
      Thread {
        secondStarted.countDown()
        handler.publish(LogRecord(Level.INFO, "second"))
      }

    first.start()
    assertTrue(output.firstWriteEntered.await(5, TimeUnit.SECONDS))
    second.start()
    assertTrue(secondStarted.await(5, TimeUnit.SECONDS))

    try {
      assertTrue(second.awaitBlockedBeforeConcurrentWrite(output.concurrentWriteEntered))
      assertFalse(output.concurrentWriteEntered.await(0, TimeUnit.SECONDS))
    } finally {
      output.releaseFirstWrite.countDown()
      first.join(5_000)
      second.join(5_000)
    }

    assertFalse(first.isAlive)
    assertFalse(second.isAlive)
  }

  private fun Thread.awaitBlockedBeforeConcurrentWrite(
    concurrentWriteEntered: CountDownLatch,
  ): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
      if (concurrentWriteEntered.count == 0L || !isAlive) return false
      if (state == Thread.State.BLOCKED || state == Thread.State.WAITING) return true
      Thread.yield()
    }
    return false
  }

  private class TrackingOutputStream : ByteArrayOutputStream() {
    var wasFlushed = false
      private set
    var wasClosed = false
      private set

    override fun flush() {
      super.flush()
      wasFlushed = true
    }

    override fun close() {
      wasClosed = true
      super.close()
    }
  }

  private class ThrowingOutputStream(
    private val failure: IOException,
  ) : OutputStream() {
    override fun write(value: Int) {
      throw failure
    }
  }

  private class FlushThrowingOutputStream(
    private val failure: IOException,
  ) : ByteArrayOutputStream() {
    override fun flush() {
      throw failure
    }
  }

  private class RecordingErrorManager : ErrorManager() {
    var message: String? = null
      private set
    var exception: Exception? = null
      private set
    var code: Int? = null
      private set

    override fun error(
      message: String?,
      exception: Exception?,
      code: Int,
    ) {
      this.message = message
      this.exception = exception
      this.code = code
    }
  }

  private class BlockingOutputStream : OutputStream() {
    val firstWriteEntered = CountDownLatch(1)
    val concurrentWriteEntered = CountDownLatch(1)
    val releaseFirstWrite = CountDownLatch(1)
    private val activeWrites = AtomicInteger()

    override fun write(value: Int) = Unit

    override fun write(
      bytes: ByteArray,
      offset: Int,
      length: Int,
    ) {
      if (activeWrites.incrementAndGet() == 1) {
        firstWriteEntered.countDown()
        releaseFirstWrite.await()
      } else {
        concurrentWriteEntered.countDown()
      }
      activeWrites.decrementAndGet()
    }
  }
}
