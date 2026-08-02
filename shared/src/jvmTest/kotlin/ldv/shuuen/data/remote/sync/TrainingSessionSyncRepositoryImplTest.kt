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
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.auth.AuthSession
import ldv.shuuen.core.auth.AuthUser
import ldv.shuuen.data.database.dao.TrainingSessionDao
import ldv.shuuen.data.database.dao.TrainingSessionScoreProjection
import ldv.shuuen.data.database.entity.TrainingSessionDbEntity
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.level_end.domain.QuestionResult

class TrainingSessionSyncRepositoryImplTest {
  @Test
  fun repeatSyncSendsOnlyActualSessionUpsertsAndTombstones() = kotlinx.coroutines.runBlocking {
    val directory = createTempDirectory("shuuen-training-session-sync-test")
    try {
      val dao = FakeTrainingSessionDao(mutableListOf(testTrainingSession()))
      val requests = mutableListOf<TrainingSessionSyncRequestDto>()
      val server = FakeTrainingSessionSyncServer()
      val repository = createRepository(directory.toString(), dao, server, requests)

      val first = repository.sync()
      val unchanged = repository.sync()
      dao.sessions[0] = dao.sessions[0].copy(replays = 3)
      val edited = repository.sync()
      dao.sessions.clear()
      val deleted = repository.sync()

      assertEquals(4, requests.size)
      assertEquals(1, requests[0].changes.size)
      assertEquals("melodies", requests[0].changes.single().flow)
      assertEquals(2, requests[0].changes.single().questionResults.single().missedCount)
      assertTrue(requests[1].changes.isEmpty())
      assertEquals(3, requests[2].changes.single().replays)
      assertTrue(requests[3].changes.single().deleted)
      assertEquals(1, first.pushed)
      assertEquals(0, unchanged.pushed)
      assertEquals(1, edited.pushed)
      assertEquals(1, deleted.pushed)
      assertTrue(server.sessions.values.single().deleted)
    } finally {
      directory.toFile().deleteRecursively()
    }
  }

  @Test
  fun unseenRemoteSessionRestoresHistoryAndStatisticsSource() = kotlinx.coroutines.runBlocking {
    val directory = createTempDirectory("shuuen-training-session-pull-test")
    try {
      val dao = FakeTrainingSessionDao()
      val server = FakeTrainingSessionSyncServer()
      server.revision = 1
      server.sessions["remote-session"] =
        testTrainingSession(id = "remote-session").toServerChange(revision = 1)
      val repository = createRepository(directory.toString(), dao, server, mutableListOf())

      val result = repository.sync()

      assertEquals(1, result.received)
      assertEquals(1, dao.sessions.size)
      val restored = dao.sessions.single()
      assertEquals("remote-session", restored.id)
      assertEquals(8, restored.correctNotes)
      assertEquals(10, restored.notesTotal)
      assertEquals(listOf(QuestionResult(1, 1, 2)), restored.questionResults)
    } finally {
      directory.toFile().deleteRecursively()
    }
  }

  private fun createRepository(
    directory: String,
    dao: FakeTrainingSessionDao,
    server: FakeTrainingSessionSyncServer,
    requests: MutableList<TrainingSessionSyncRequestDto>,
  ): TrainingSessionSyncRepositoryImpl {
    val engine = MockEngine { request ->
      val decoded =
        ApiJson.decodeFromString<TrainingSessionSyncRequestDto>(
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
    return TrainingSessionSyncRepositoryImpl(
      path = kotlinx.io.files.Path(directory),
      api = TrainingSessionSyncApi(client),
      authRepository = SessionSyncAuthRepository(),
      trainingSessionDao = dao,
      json = ApiJson,
    )
  }
}

private class FakeTrainingSessionSyncServer {
  var revision = 0L
  val sessions = mutableMapOf<String, TrainingSessionSyncChangeDto>()

  fun handle(request: TrainingSessionSyncRequestDto): TrainingSessionSyncResponseDto {
    var applied = 0
    var conflicts = 0
    val touched = request.changes.map { it.id }.toSet()
    request.changes.forEach { mutation ->
      val existing = sessions[mutation.id]
      if ((existing?.revision ?: 0) != mutation.baseRevision) {
        conflicts++
      } else if (!(mutation.deleted && existing == null)) {
        revision++
        sessions[mutation.id] = mutation.toServerChange(revision)
        applied++
      }
    }
    val changes =
      sessions.values
        .filter { it.revision > request.sinceRevision || it.id in touched }
        .sortedBy { it.revision }
    return TrainingSessionSyncResponseDto(revision, applied, conflicts, changes)
  }
}

private fun TrainingSessionSyncMutationDto.toServerChange(revision: Long) =
  TrainingSessionSyncChangeDto(
    id = id,
    revision = revision,
    deleted = deleted,
    flow = flow,
    levelId = levelId,
    levelName = levelName,
    completedAtEpochMillis = completedAtEpochMillis,
    finishedEarly = finishedEarly,
    questionsAnswered = questionsAnswered,
    notesTotal = notesTotal,
    correctNotes = correctNotes,
    missedNotes = missedNotes,
    replays = replays,
    durationMillis = durationMillis,
    avgAnswerMillis = avgAnswerMillis,
    avgDeltaMillis = avgDeltaMillis,
    bestStreak = bestStreak,
    keysPracticed = keysPracticed,
    questionResults = questionResults,
  )

private fun TrainingSessionDbEntity.toServerChange(revision: Long) =
  TrainingSessionSyncChangeDto(
    id = id,
    revision = revision,
    deleted = false,
    flow = "melodies",
    levelId = levelId,
    levelName = levelName,
    completedAtEpochMillis = completedAtEpochMillis,
    finishedEarly = finishedEarly,
    questionsAnswered = questionsAnswered,
    notesTotal = notesTotal,
    correctNotes = correctNotes,
    missedNotes = missedNotes,
    replays = replays,
    durationMillis = durationMillis,
    avgAnswerMillis = avgAnswerMillis,
    avgDeltaMillis = avgDeltaMillis,
    bestStreak = bestStreak,
    keysPracticed = keysPracticed,
    questionResults =
      questionResults.map {
        TrainingQuestionResultDto(it.questionNumber, it.noteCount, it.missedCount)
      },
  )

private class SessionSyncAuthRepository : AuthRepository {
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

private class FakeTrainingSessionDao(
  val sessions: MutableList<TrainingSessionDbEntity> = mutableListOf(),
) : TrainingSessionDao {
  override suspend fun getAll(): List<TrainingSessionDbEntity> = sessions.toList()

  override suspend fun getById(id: String): TrainingSessionDbEntity? =
    sessions.firstOrNull { it.id == id }

  override fun observeLatest(): Flow<TrainingSessionDbEntity?> = flowOf(sessions.lastOrNull())

  override suspend fun getByLevelId(levelId: String): List<TrainingSessionDbEntity> =
    sessions.filter { it.levelId == levelId }

  override fun observeRecentScoresByLevelId(
    flow: TrainingFlow,
    levelId: String,
    limit: Int,
  ): Flow<List<TrainingSessionScoreProjection>> = flowOf(emptyList())

  override fun observeAttemptedLevelIds(flow: TrainingFlow): Flow<List<String>> = flowOf(emptyList())

  override fun observeCompletedLevelIds(flow: TrainingFlow): Flow<List<String>> = flowOf(emptyList())

  override suspend fun deleteLastByLevelId(flow: TrainingFlow, levelId: String) = Unit

  override suspend fun deleteAllByLevelId(flow: TrainingFlow, levelId: String) = Unit

  override suspend fun deleteAllByCourseReferencePrefix(courseReferencePrefix: String) = Unit

  override suspend fun deleteById(id: String) {
    sessions.removeAll { it.id == id }
  }

  override suspend fun deleteByIds(ids: List<String>) {
    sessions.removeAll { it.id in ids }
  }

  override suspend fun upsertSession(session: TrainingSessionDbEntity) {
    sessions.removeAll { it.id == session.id }
    sessions += session
  }

  override suspend fun upsertSessions(sessions: List<TrainingSessionDbEntity>) {
    sessions.forEach { upsertSession(it) }
  }
}

private fun testTrainingSession(id: String = "session-1") =
  TrainingSessionDbEntity(
    id = id,
    flow = TrainingFlow.Melodies,
    levelId = "course:7:melodies:level-3",
    levelName = "Interval run",
    completedAtEpochMillis = 1_786_000_000_000,
    finishedEarly = false,
    questionsAnswered = 10,
    notesTotal = 10,
    correctNotes = 8,
    missedNotes = 2,
    replays = 1,
    durationMillis = 45_000,
    avgAnswerMillis = null,
    avgDeltaMillis = 250,
    bestStreak = 6,
    keysPracticed = 2,
    questionResults = listOf(QuestionResult(1, 1, 2)),
  )
