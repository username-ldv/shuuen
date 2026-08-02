package ldv.shuuen.data.remote

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.InternalAPI

/**
 * Ktor's Android HttpURLConnection engine does not always read one byte past a complete JSON value.
 * Explicitly cancelling the consumed raw channel closes Android's transparent gzip stream and its
 * native Inflater instead of leaving it to the finalizer.
 */
@OptIn(InternalAPI::class)
internal suspend inline fun <reified T> HttpResponse.bodyAndClose(): T {
  val content = rawContent
  return try {
    body<T>()
  } finally {
    content.cancel(null)
  }
}

@OptIn(InternalAPI::class)
internal suspend fun HttpResponse.bodyAsTextAndClose(): String {
  val content = rawContent
  return try {
    bodyAsText()
  } finally {
    content.cancel(null)
  }
}
