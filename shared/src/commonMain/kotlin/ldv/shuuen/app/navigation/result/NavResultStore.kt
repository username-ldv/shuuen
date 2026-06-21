package ldv.shuuen.app.navigation.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ldv.shuuen.app.navigation.result.NavResultStore.Companion.SavedNavResultsStateSaver

class NavResultStore(private val state: MutableState<SavedNavResults>) {
  fun <T : Any> send(key: NavResultKey<T>, result: T) {
    val payload = Json.encodeToJsonElement(key.serializer, result)
    state.value = state.value.copy(
      pending = state.value.pending + (key.id to payload)
    )
  }

  fun <T : Any> peek(key: NavResultKey<T>): T? {
    return state.value.pending[key.id]?.let {
      Json.decodeFromJsonElement(key.serializer, it)
    }
  }

  fun <T : Any> clear(key: NavResultKey<T>) {
    state.value = state.value.copy(pending = state.value.pending - key.id)
  }

  companion object {
    val SavedNavResultsStateSaver =
        Saver<NavResultStore, String>(
            save = { store ->
              Json.encodeToString(store.state.value)
            },
            restore = { encoded ->
              NavResultStore(mutableStateOf(Json.decodeFromString<SavedNavResults>(encoded)))
            },
        )
  }
}

class NavResultKey<T>(
  val id: String,
  val serializer: KSerializer<T>,
)

@Serializable data class SavedNavResults(val pending: Map<String, JsonElement> = emptyMap())

val LocalNavResultStore =
    staticCompositionLocalOf<NavResultStore> { error("LocalNavResultStore was not provided") }

@Composable
fun rememberNavResultStore() =
    rememberSaveable(saver = SavedNavResultsStateSaver) {
      NavResultStore(mutableStateOf(SavedNavResults()))
    }
