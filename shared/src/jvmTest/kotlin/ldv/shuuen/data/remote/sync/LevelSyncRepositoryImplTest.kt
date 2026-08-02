package ldv.shuuen.data.remote.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.auth.AuthSession
import ldv.shuuen.core.auth.AuthUser
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.data.database.dao.ChordsLevelDao
import ldv.shuuen.data.database.dao.ContextDao
import ldv.shuuen.data.database.dao.MelodiesLevelDao
import ldv.shuuen.data.database.dao.SinglesLevelDao
import ldv.shuuen.data.database.entity.ChordsLevelDbEntity
import ldv.shuuen.data.database.entity.ContextDbEntity
import ldv.shuuen.data.database.entity.MelodiesLevelDbEntity
import ldv.shuuen.data.database.entity.SinglesLevelDbEntity
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.data.remote.course.LevelDefinitionCodec
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig

class LevelSyncRepositoryImplTest {
  @Test
  fun repeatSyncSendsOnlyActualUpsertsAndTombstones() = kotlinx.coroutines.runBlocking {
    val directory = createTempDirectory("shuuen-level-sync-test")
    try {
      val singles = FakeSinglesDao(mutableListOf(testEntity("Original")))
      val requests = mutableListOf<LevelSyncRequestDto>()
      val server = FakeSyncServer()
      val engine = MockEngine { request ->
        val decoded = ApiJson.decodeFromString<LevelSyncRequestDto>(
          request.body.toByteArray().decodeToString()
        )
        requests += decoded
        respond(
          content = ApiJson.encodeToString(LevelSyncEnvelopeDto(server.handle(decoded))),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
      }
      val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(ApiJson) }
      }
      val repository =
        LevelSyncRepositoryImpl(
          path = kotlinx.io.files.Path(directory.toString()),
          api = LevelSyncApi(client),
          authRepository = FakeAuthRepository(),
          singlesDao = singles,
          melodiesDao = EmptyMelodiesDao,
          chordsDao = EmptyChordsDao,
          contextDao = EmptyContextDao,
          codec = LevelDefinitionCodec(ApiJson),
          json = ApiJson,
        )

      val first = repository.sync()
      val unchanged = repository.sync()
      singles.levels[0] = singles.levels[0].copy(name = "Edited")
      val edited = repository.sync()
      singles.levels.clear()
      val deleted = repository.sync()

      assertEquals(4, requests.size)
      assertEquals(1, requests[0].changes.size)
      assertEquals(
        "absolute",
        requests[0].changes.single().definition!!.jsonObject["level_config"]!!
          .jsonObject["type"]!!.jsonPrimitive.content,
      )
      assertTrue(requests[1].changes.isEmpty())
      assertEquals("Edited", requests[2].changes.single().name)
      assertTrue(requests[3].changes.single().deleted)
      assertEquals(1, first.pushed)
      assertEquals(0, unchanged.pushed)
      assertEquals(1, edited.pushed)
      assertEquals(1, deleted.pushed)
      assertFalse(server.levels.values.single().definition != null)
    } finally {
      directory.toFile().deleteRecursively()
    }
  }
}

private class FakeSyncServer {
  var revision = 0L
  val levels = mutableMapOf<String, LevelSyncChangeDto>()

  fun handle(request: LevelSyncRequestDto): LevelSyncResponseDto {
    var applied = 0
    var conflicts = 0
    val touched = request.changes.map { "${it.kind}\u0000${it.id}" }.toSet()
    request.changes.forEach { mutation ->
      val key = "${mutation.kind}\u0000${mutation.id}"
      val existing = levels[key]
      if ((existing?.revision ?: 0) != mutation.baseRevision) {
        conflicts++
      } else if (!(mutation.deleted && existing == null)) {
        revision++
        levels[key] =
          LevelSyncChangeDto(
            kind = mutation.kind,
            id = mutation.id,
            revision = revision,
            deleted = mutation.deleted,
            name = mutation.name,
            source = mutation.source,
            definition = mutation.definition.takeUnless { mutation.deleted }?.canonicalized(),
          )
        applied++
      }
    }
    val changes =
      levels.entries
        .filter { entry -> entry.value.revision > request.sinceRevision || entry.key in touched }
        .map { it.value }
        .sortedBy { it.revision }
    return LevelSyncResponseDto(revision, applied, conflicts, changes)
  }
}

private fun JsonElement.canonicalized(): JsonElement =
  when (this) {
    is JsonObject -> JsonObject(keys.sorted().associateWith { key -> getValue(key).canonicalized() })
    is JsonArray -> JsonArray(map { it.canonicalized() })
    else -> this
  }

private class FakeAuthRepository : AuthRepository {
  override val session =
    MutableStateFlow<AuthSession?>(
      AuthSession(
        user = AuthUser(7, "learner", "", "user"),
        accessToken = "token",
        backendUrl = "http://backend.test",
      )
    )

  override suspend fun signIn(username: String, password: String) = requireNotNull(session.value)

  override suspend fun signOut() {
    session.value = null
  }
}

private class FakeSinglesDao(val levels: MutableList<SinglesLevelDbEntity>) : SinglesLevelDao {
  override suspend fun getAll() = levels.toList()

  override suspend fun getById(id: String) = levels.firstOrNull { it.id == id }

  override suspend fun upsertLevel(level: SinglesLevelDbEntity) {
    levels.removeAll { it.id == level.id }
    levels += level
  }

  override suspend fun deleteById(id: String) {
    levels.removeAll { it.id == id }
  }
}

private object EmptyMelodiesDao : MelodiesLevelDao {
  override suspend fun getAll() = emptyList<MelodiesLevelDbEntity>()
  override suspend fun getById(id: String): MelodiesLevelDbEntity? = null
  override suspend fun upsertLevel(level: MelodiesLevelDbEntity) = Unit
  override suspend fun deleteById(id: String) = Unit
}

private object EmptyChordsDao : ChordsLevelDao {
  override suspend fun getAll() = emptyList<ChordsLevelDbEntity>()
  override suspend fun getById(id: String): ChordsLevelDbEntity? = null
  override suspend fun upsertLevel(level: ChordsLevelDbEntity) = Unit
  override suspend fun deleteById(id: String) = Unit
}

private object EmptyContextDao : ContextDao {
  override suspend fun getById(id: String): ContextDbEntity? = null
  override suspend fun getAll() = emptyList<ContextDbEntity>()
  override suspend fun upsertContext(context: ContextDbEntity) = Unit
}

private fun testEntity(name: String) =
  SinglesLevelDbEntity(
    id = "level-1",
    name = name,
    config =
      LevelConfig.Singles.Absolute(
        scales =
          listOf(
            ScaleConfig.AbsoluteScaleConfig(
              root = Pitch.C,
              scaleType = ScaleType.Major,
              pitchStates =
                Pitch.entries.map {
                  ScaleConfig.ScaleItemState.ScalePitchState(it, it == Pitch.C)
                },
            )
          )
      ),
    contextId = null,
    source = LevelSource.User,
    questionsNumber = 10,
    range = NoteRange(Note(Pitch.C, 3), Note(Pitch.C, 5)),
  )
