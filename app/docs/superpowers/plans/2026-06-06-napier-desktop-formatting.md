# Napier Desktop Formatting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Napier's localized JVM console output with fixed-format, UTF-8 stdout logs while preserving native Android Logcat behavior.

**Architecture:** The desktop app will supply Napier's JVM `DebugAntilog` with a focused JUL `Handler`. A pure `Formatter` will add the numeric local timestamp, while the handler writes the formatted bytes to stdout and flushes immediately. Napier initialization will move from the shared composable to each platform entry point so each process registers one platform-appropriate backend.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform, Napier 2.7.1, `java.util.logging`, `java.time`, Kotlin Test, JUnit 4

---

## File Structure

- Create `desktopApp/src/main/kotlin/ldv/shuuen/logging/DesktopLogFormatter.kt`: Formats JUL records with a fixed local timestamp.
- Create `desktopApp/src/main/kotlin/ldv/shuuen/logging/Utf8StdoutHandler.kt`: Writes formatted records to stdout as UTF-8 and flushes each record.
- Create `desktopApp/src/test/kotlin/ldv/shuuen/logging/DesktopLogFormatterTest.kt`: Covers timestamp, message, newline, and multiline throwable text.
- Create `desktopApp/src/test/kotlin/ldv/shuuen/logging/Utf8StdoutHandlerTest.kt`: Covers UTF-8 output and immediate flushing behavior.
- Modify `desktopApp/src/main/kotlin/ldv/shuuen/main.kt`: Register the desktop Napier backend before Compose starts.
- Create `androidApp/src/main/kotlin/ldv/shuuen/ShuuenApplication.kt`: Register Napier's native Android backend once per process.
- Modify `androidApp/src/main/AndroidManifest.xml`: Select the logging-aware application class.
- Modify `androidApp/src/main/kotlin/ldv/shuuen/MainActivity.kt`: Remove the abandoned activity-scoped Napier setup.
- Modify `shared/src/commonMain/kotlin/ldv/shuuen/App.kt`: Remove composition-scoped Napier initialization.
- Modify `desktopApp/build.gradle.kts`: Add explicit JVM test dependencies.

### Task 1: Fixed Desktop Log Formatter

**Files:**
- Create: `desktopApp/src/test/kotlin/ldv/shuuen/logging/DesktopLogFormatterTest.kt`
- Create: `desktopApp/src/main/kotlin/ldv/shuuen/logging/DesktopLogFormatter.kt`
- Modify: `desktopApp/build.gradle.kts`

- [ ] **Step 1: Add the desktop test dependencies**

Add these entries inside `dependencies` in `desktopApp/build.gradle.kts`:

```kotlin
testImplementation(libs.kotlin.testJunit)
testImplementation(libs.junit)
```

- [ ] **Step 2: Write the failing formatter tests**

Create `desktopApp/src/test/kotlin/ldv/shuuen/logging/DesktopLogFormatterTest.kt`:

```kotlin
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
  fun formatsTimestampAndNapierMessage() {
    val record = LogRecord(
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
  fun preservesMultilineThrowableTextFromNapier() {
    val record = LogRecord(
      Level.SEVERE,
      "[ERROR] AppNavigator\$navigateTo - Navigation failed\njava.lang.IllegalStateException: broken",
    ).apply {
      instant = Instant.parse("2026-06-06T14:38:03.123Z")
    }

    assertEquals(
      "2026-06-06 14:38:03.123 [ERROR] AppNavigator\$navigateTo - Navigation failed\n" +
        "java.lang.IllegalStateException: broken" +
        System.lineSeparator(),
      formatter.format(record),
    )
  }

  @Test
  fun formatsMissingMessageAsEmptyText() {
    val record = LogRecord(Level.INFO, null).apply {
      instant = Instant.parse("2026-06-06T14:38:03.123Z")
    }

    assertEquals(
      "2026-06-06 14:38:03.123 ${System.lineSeparator()}",
      formatter.format(record),
    )
  }
}
```

- [ ] **Step 3: Run the formatter tests and verify they fail**

Run:

```powershell
.\gradlew.bat :desktopApp:test --tests "ldv.shuuen.logging.DesktopLogFormatterTest"
```

Expected: `compileTestKotlin` fails because `DesktopLogFormatter` does not exist.

- [ ] **Step 4: Implement the formatter**

Create `desktopApp/src/main/kotlin/ldv/shuuen/logging/DesktopLogFormatter.kt`:

```kotlin
package ldv.shuuen.logging

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Formatter
import java.util.logging.LogRecord

internal class DesktopLogFormatter(
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) : Formatter() {
  override fun format(record: LogRecord): String {
    val timestamp = TIMESTAMP_FORMATTER.format(record.instant.atZone(zoneId))
    return "$timestamp ${record.message.orEmpty()}${System.lineSeparator()}"
  }

  private companion object {
    val TIMESTAMP_FORMATTER: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
  }
}
```

- [ ] **Step 5: Run the formatter tests and verify they pass**

Run:

```powershell
.\gradlew.bat :desktopApp:test --tests "ldv.shuuen.logging.DesktopLogFormatterTest"
```

Expected: all three formatter tests pass.

- [ ] **Step 6: Commit the formatter**

```powershell
git add -- desktopApp/build.gradle.kts desktopApp/src/main/kotlin/ldv/shuuen/logging/DesktopLogFormatter.kt desktopApp/src/test/kotlin/ldv/shuuen/logging/DesktopLogFormatterTest.kt
git commit -m "add fixed desktop log formatter"
```

### Task 2: UTF-8 Stdout Handler

**Files:**
- Create: `desktopApp/src/test/kotlin/ldv/shuuen/logging/Utf8StdoutHandlerTest.kt`
- Create: `desktopApp/src/main/kotlin/ldv/shuuen/logging/Utf8StdoutHandler.kt`

- [ ] **Step 1: Write the failing handler tests**

Create `desktopApp/src/test/kotlin/ldv/shuuen/logging/Utf8StdoutHandlerTest.kt`:

```kotlin
package ldv.shuuen.logging

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Utf8StdoutHandlerTest {
  @Test
  fun writesUtf8AndFlushesEachRecord() {
    val output = TrackingOutputStream()
    val handler = Utf8StdoutHandler(
      output = output,
      formatter = DesktopLogFormatter(ZoneId.of("UTC")),
    )
    val record = LogRecord(Level.INFO, "[INFO] App - Привет").apply {
      instant = Instant.parse("2026-06-06T14:38:03.123Z")
    }

    handler.publish(record)

    assertEquals(
      "2026-06-06 14:38:03.123 [INFO] App - Привет${System.lineSeparator()}",
      output.toString(Charsets.UTF_8),
    )
    assertTrue(output.wasFlushed)
  }

  private class TrackingOutputStream : ByteArrayOutputStream() {
    var wasFlushed = false

    override fun flush() {
      wasFlushed = true
      super.flush()
    }
  }
}
```

- [ ] **Step 2: Run the handler test and verify it fails**

Run:

```powershell
.\gradlew.bat :desktopApp:test --tests "ldv.shuuen.logging.Utf8StdoutHandlerTest"
```

Expected: `compileTestKotlin` fails because `Utf8StdoutHandler` does not exist.

- [ ] **Step 3: Implement the handler**

Create `desktopApp/src/main/kotlin/ldv/shuuen/logging/Utf8StdoutHandler.kt`:

```kotlin
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

  override fun publish(record: LogRecord) {
    if (!isLoggable(record)) return

    try {
      output.write(formatter.format(record).toByteArray(Charsets.UTF_8))
      output.flush()
    } catch (exception: Exception) {
      reportError(
        "Unable to write desktop log record",
        exception,
        ErrorManager.WRITE_FAILURE,
      )
    }
  }

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

  override fun close() {
    flush()
  }
}
```

- [ ] **Step 4: Run all desktop logging tests and verify they pass**

Run:

```powershell
.\gradlew.bat :desktopApp:test --tests "ldv.shuuen.logging.*"
```

Expected: formatter and handler tests pass.

- [ ] **Step 5: Commit the handler**

```powershell
git add -- desktopApp/src/main/kotlin/ldv/shuuen/logging/Utf8StdoutHandler.kt desktopApp/src/test/kotlin/ldv/shuuen/logging/Utf8StdoutHandlerTest.kt
git commit -m "write Napier desktop logs to UTF-8 stdout"
```

### Task 3: One-Time Platform Initialization

**Files:**
- Modify: `desktopApp/src/main/kotlin/ldv/shuuen/main.kt`
- Create: `androidApp/src/main/kotlin/ldv/shuuen/ShuuenApplication.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/src/main/kotlin/ldv/shuuen/MainActivity.kt`
- Modify: `shared/src/commonMain/kotlin/ldv/shuuen/App.kt`

- [ ] **Step 1: Register the desktop backend before starting Compose**

Replace `desktopApp/src/main/kotlin/ldv/shuuen/main.kt` with:

```kotlin
package ldv.shuuen

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import ldv.shuuen.logging.Utf8StdoutHandler

fun main() {
  Napier.base(DebugAntilog(handler = listOf(Utf8StdoutHandler())))

  application {
    Window(
      onCloseRequest = ::exitApplication,
      title = "Shuuen",
    ) {
      App()
    }
  }
}
```

- [ ] **Step 2: Register Android's native backend once per process**

Create `androidApp/src/main/kotlin/ldv/shuuen/ShuuenApplication.kt`:

```kotlin
package ldv.shuuen

import android.app.Application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class ShuuenApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    Napier.base(DebugAntilog())
  }
}
```

- [ ] **Step 3: Select the application class and clean the activity**

Add the application name to the existing `<application>` element in
`androidApp/src/main/AndroidManifest.xml`:

```xml
<application
    android:name=".ShuuenApplication"
    ...>
```

Remove the commented Napier initialization and its unused imports from
`androidApp/src/main/kotlin/ldv/shuuen/MainActivity.kt`. Its `onCreate` method
must contain only the existing edge-to-edge setup and `setContent { App() }`.

- [ ] **Step 4: Remove logging initialization from the shared composable**

Remove the `LaunchedEffect`, `DebugAntilog`, and `Napier` imports and initialization from `shared/src/commonMain/kotlin/ldv/shuuen/App.kt`. The resulting function must begin:

```kotlin
@Composable
fun App() {
  KoinApplication(configuration = koinConfiguration {
    modules(listOf(commonModule, platformModule))
  }) {
```

- [ ] **Step 5: Compile and test both targets**

Run:

```powershell
.\gradlew.bat :desktopApp:test :desktopApp:compileKotlin :shared:compileAndroidMain :androidApp:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`; all desktop tests pass and both platform entry points compile.

- [ ] **Step 6: Manually verify desktop output**

Run:

```powershell
.\gradlew.bat :desktopApp:run
```

Navigate once in the application, then stop it. Expected output contains a line shaped like:

```text
2026-06-06 17:38:03.123 [VERBOSE] AppNavigator$navigateTo - Navigate happened
```

It must appear on stdout without JUL's localized header, `DebugAntilog performLog`, or replacement characters.

- [ ] **Step 7: Commit platform initialization**

```powershell
git add -- desktopApp/src/main/kotlin/ldv/shuuen/main.kt androidApp/src/main/kotlin/ldv/shuuen/ShuuenApplication.kt androidApp/src/main/AndroidManifest.xml androidApp/src/main/kotlin/ldv/shuuen/MainActivity.kt shared/src/commonMain/kotlin/ldv/shuuen/App.kt
git commit -m "initialize Napier once per platform"
```

### Task 4: Final Verification

**Files:**
- Verify all files changed in Tasks 1-3.

- [ ] **Step 1: Run focused verification**

```powershell
.\gradlew.bat :desktopApp:check :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Check the patch for whitespace errors**

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 3: Inspect final repository state**

```powershell
git status --short
git log -4 --oneline
```

Expected: the three implementation commits are present. Any unrelated pre-existing user changes remain intact and are not reverted.
