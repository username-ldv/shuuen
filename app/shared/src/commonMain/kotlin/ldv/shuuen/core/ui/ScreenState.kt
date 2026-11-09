package ldv.shuuen.core.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * A screen's data while it loads. Repositories either return values or throw; turning that into
 * loading and error states is the ViewModel's job, with this type or a screen-specific one.
 */
sealed interface ScreenState<out T> {
  data object Loading : ScreenState<Nothing>

  data class Error(val message: String) : ScreenState<Nothing> {
    constructor(error: Throwable) : this(error.message ?: "Something went wrong.")
  }

  data class Success<out T>(val uiData: T) : ScreenState<T>
}

/** A repository flow as screen state: every value as [ScreenState.Success], a failure as Error. */
fun <T> Flow<T>.asScreenState(): Flow<ScreenState<T>> =
  map<T, ScreenState<T>> { ScreenState.Success(it) }.catch { emit(ScreenState.Error(it)) }

/** Runs a one-shot load: its result as [ScreenState.Success], or the failure as Error. */
suspend fun <T> loadScreenState(load: suspend () -> T): ScreenState<T> =
  try {
    ScreenState.Success(load())
  } catch (error: CancellationException) {
    throw error
  } catch (error: Throwable) {
    ScreenState.Error(error)
  }
