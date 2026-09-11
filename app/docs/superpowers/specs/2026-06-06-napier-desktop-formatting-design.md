# Napier Desktop Formatting Design

## Goal

Keep Napier as the shared logging API while making desktop JVM logs concise,
readable, and encoding-independent. Android logs must continue to use Napier's
native Logcat implementation.

## Architecture

Initialize Napier once in each platform entry point:

- Android registers `DebugAntilog`, which writes through `android.util.Log`.
- Desktop registers `DebugAntilog` with a custom `java.util.logging.Handler`.

The shared `App` composable will not initialize logging. This prevents
registration from depending on composition lifecycle and avoids duplicate
handlers if the composable is recreated.

## Desktop Handler

The desktop handler will:

- Write to standard output rather than standard error.
- Use UTF-8-safe plain output without localized formatter text.
- Add local time using the fixed pattern `yyyy-MM-dd HH:mm:ss.SSS`.
- Preserve the Napier-generated level, inferred caller tag, message, and
  throwable stack trace.
- Flush after each record so logs appear immediately.

Example:

```text
2026-06-06 17:38:03.123 [VERBOSE] AppNavigator$navigateTo - Navigate happened
```

Napier already builds the text after the timestamp:

```text
[VERBOSE] AppNavigator$navigateTo - Navigate happened
```

The handler therefore adds only the timestamp and output behavior.

## Error Handling

If a log record has no message, the handler prints an empty message after the
timestamp. Output failures are reported through the standard JUL handler error
mechanism rather than being allowed to crash the application.

## Testing

Desktop JVM tests will verify:

- The timestamp follows the fixed numeric format.
- The Napier message is preserved.
- Output ends with one platform line separator.
- A throwable included by Napier remains visible in the formatted output.

The project compile and relevant test tasks will verify that Android still uses
`DebugAntilog` and that shared Napier calls compile for both targets.

## Scope

This change does not replace Napier, introduce a new logging facade, alter
Napier's inferred tag format, or customize Android Logcat output.
