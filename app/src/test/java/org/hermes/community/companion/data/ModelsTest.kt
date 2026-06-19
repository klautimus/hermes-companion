package org.hermes.community.companion.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ─── SessionsList ─────────────────────────────────────────

    @Test
    fun sessionsList_serialize_roundTrip() {
        val original = SessionsList(
            data = listOf(
                HermesSession(id = "s1", title = "Test Session", model = "gpt-4", startedAt = 1700000000.0, messageCount = 5),
                HermesSession(id = "s2", title = null, model = null, startedAt = null, messageCount = 0)
            ),
            hasMore = true
        )
        val encoded = json.encodeToString(SessionsList.serializer(), original)
        val decoded = json.decodeFromString(SessionsList.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun sessionsList_deserialize_fromJson() {
        val raw = """{"data":[{"id":"abc123","title":"My Chat","model":"hermes","started_at":1700000000.0,"message_count":3}],"has_more":false}"""
        val result = json.decodeFromString(SessionsList.serializer(), raw)
        assertEquals(1, result.data.size)
        assertEquals("abc123", result.data[0].id)
        assertEquals("My Chat", result.data[0].title)
        assertEquals("hermes", result.data[0].model)
        assertEquals(1700000000.0, result.data[0].startedAt)
        assertEquals(3, result.data[0].messageCount)
        assertFalse(result.hasMore)
    }

    @Test
    fun sessionsList_emptyData() {
        val raw = """{"data":[]}"""
        val result = json.decodeFromString(SessionsList.serializer(), raw)
        assertTrue(result.data.isEmpty())
        assertFalse(result.hasMore)
    }

    @Test
    fun sessionsList_missingOptionalFields() {
        val raw = """{"data":[{"id":"x1"}]}"""
        val result = json.decodeFromString(SessionsList.serializer(), raw)
        assertEquals(1, result.data.size)
        assertEquals("x1", result.data[0].id)
        assertNull(result.data[0].title)
        assertNull(result.data[0].model)
        assertNull(result.data[0].startedAt)
        assertEquals(0, result.data[0].messageCount)
    }

    // ─── KanbanBoard ─────────────────────────────────────────

    @Test
    fun kanbanBoard_withAllFields() {
        val raw = """{"slug":"main","name":"Main Board","description":"Primary","counts":{"todo":5,"scheduled":3,"ready":2,"running":1,"blocked":0,"review":4,"done":10,"archived":3},"total":21,"archived":false}"""
        val board = json.decodeFromString(KanbanBoard.serializer(), raw)
        assertEquals("main", board.slug)
        assertEquals("Main Board", board.name)
        assertEquals("Primary", board.description)
        assertNotNull(board.counts)
        val counts = board.counts!!
        assertEquals(5, counts.todo)
        assertEquals(3, counts.scheduled)
        assertEquals(2, counts.ready)
        assertEquals(1, counts.running)
        assertEquals(0, counts.blocked)
        assertEquals(4, counts.review)
        assertEquals(10, counts.done)
        assertEquals(3, counts.archived)
        assertEquals(21, board.total)
        assertFalse(board.archived)
    }

    @Test
    fun kanbanBoard_missingOptionalFields() {
        val raw = """{"slug":"minimal"}"""
        val board = json.decodeFromString(KanbanBoard.serializer(), raw)
        assertEquals("minimal", board.slug)
        assertEquals("", board.name)
        assertEquals("", board.description)
        assertNull(board.counts)
        assertEquals(0, board.total)
        assertFalse(board.archived)
    }

    @Test
    fun kanbanBoard_archived() {
        val raw = """{"slug":"old","name":"Old Board","archived":true}"""
        val board = json.decodeFromString(KanbanBoard.serializer(), raw)
        assertTrue(board.archived)
    }

    @Test
    fun kanbanBoard_serialize_roundTrip() {
        val original = KanbanBoard(
            slug = "test",
            name = "Test",
            description = "Desc",
            counts = KanbanCounts(todo = 1, scheduled = 7, ready = 2, running = 3, blocked = 4, review = 8, done = 5, archived = 6),
            total = 21,
            archived = false
        )
        val encoded = json.encodeToString(KanbanBoard.serializer(), original)
        val decoded = json.decodeFromString(KanbanBoard.serializer(), encoded)
        assertEquals(original, decoded)
    }

    // ─── KanbanTask ──────────────────────────────────────────

    @Test
    fun kanbanTask_withAllFields() {
        val raw = """{"id":"t1","title":"Fix bug","status":"running","assignee":"kevin","priority":2,"body":"Details here","created_at":1704067200,"updated_at":1704153600}"""
        val task = json.decodeFromString(KanbanTask.serializer(), raw)
        assertEquals("t1", task.id)
        assertEquals("Fix bug", task.title)
        assertEquals("running", task.status)
        assertEquals("kevin", task.assignee)
        assertEquals(2, task.priority)
        assertEquals("Details here", task.body)
        assertEquals(1704067200L, task.created)
        assertEquals(1704153600L, task.updated)
    }

    @Test
    fun kanbanTask_missingOptionalFields() {
        val raw = """{"id":"t2","title":"Simple","status":"todo"}"""
        val task = json.decodeFromString(KanbanTask.serializer(), raw)
        assertEquals("t2", task.id)
        assertEquals("Simple", task.title)
        assertEquals("todo", task.status)
        assertNull(task.assignee)
        assertEquals(1, task.priority)
        assertNull(task.body)
        assertNull(task.created)
        assertNull(task.updated)
    }

    @Test
    fun kanbanTask_variousStatuses() {
        val statuses = listOf("todo", "ready", "running", "blocked", "done", "archived")
        for (status in statuses) {
            val raw = """{"id":"t-$status","title":"Task $status","status":"$status"}"""
            val task = json.decodeFromString(KanbanTask.serializer(), raw)
            assertEquals(status, task.status)
        }
    }

    @Test
    fun kanbanTask_serialize_roundTrip() {
        val original = KanbanTask(
            id = "t3",
            title = "Round Trip",
            status = "done",
            assignee = "ops",
            priority = 3,
            body = "Body text",
            created = 1717200000,
            updated = 1718323200
        )
        val encoded = json.encodeToString(KanbanTask.serializer(), original)
        val decoded = json.decodeFromString(KanbanTask.serializer(), encoded)
        assertEquals(original, decoded)
    }

    // ─── TaskShowResponse ────────────────────────────────────

    @Test
    fun taskShowResponse_withComments() {
        val raw = """{"id":"t1","title":"Task","status":"running","assignee":"kevin","priority":1,"body":"body","comments":[{"author":"kevin","body":"note","at":"2024-01-01"}],"events":[{"kind":"created","at":"2024-01-01","profile":"ops"}]}"""
        val resp = json.decodeFromString(TaskShowResponse.serializer(), raw)
        assertEquals("t1", resp.id)
        assertEquals(1, resp.comments.size)
        assertEquals("kevin", resp.comments[0].author)
        assertEquals("note", resp.comments[0].body)
        assertEquals(1, resp.events.size)
        assertEquals("created", resp.events[0].kind)
    }

    // ─── HermesSession ───────────────────────────────────────

    @Test
    fun hermesSession_roundTrip() {
        val original = HermesSession(id = "s1", title = "Chat", model = "gpt-4", startedAt = 1700000000.0, messageCount = 42)
        val encoded = json.encodeToString(HermesSession.serializer(), original)
        val decoded = json.decodeFromString(HermesSession.serializer(), encoded)
        assertEquals(original, decoded)
    }

    // ─── SessionMessages ─────────────────────────────────────

    @Test
    fun sessionMessages_roundTrip() {
        val original = SessionMessages(
            data = listOf(
                HermesChatMessage(role = "user", content = "Hello", at = 1700000000.0),
                HermesChatMessage(role = "assistant", content = "Hi there", at = 1700000001.0)
            )
        )
        val encoded = json.encodeToString(SessionMessages.serializer(), original)
        val decoded = json.decodeFromString(SessionMessages.serializer(), encoded)
        assertEquals(original, decoded)
    }

    // ─── CompanionHealth ─────────────────────────────────────

    @Test
    fun companionHealth_roundTrip() {
        val original = CompanionHealth(status = "ok", version = "1.0.0", hermesReachable = true)
        val encoded = json.encodeToString(CompanionHealth.serializer(), original)
        val decoded = json.decodeFromString(CompanionHealth.serializer(), encoded)
        assertEquals(original, decoded)
    }

    // ─── DeepLinkConfig Token Parsing ─────────────────────────

    @Test
    fun deepLinkConfig_withToken() {
        val config = DeepLinkConfig(
            serverUrl = "https://example.com",
            username = "kevin",
            password = "",
            token = "abc123token",
            board = "default"
        )
        assertEquals("abc123token", config.token)
        assertEquals("https://example.com", config.serverUrl)
        assertEquals("kevin", config.username)
    }

    @Test
    fun deepLinkConfig_withoutToken_backcompat() {
        val config = DeepLinkConfig(
            serverUrl = "https://example.com",
            username = "kevin",
            password = "plaintextpw",
            board = "default"
        )
        assertNull("Token should be null when not provided", config.token)
        assertEquals("plaintextpw", config.password)
    }

    @Test
    fun deepLinkConfig_tokenTakesPrecedence() {
        // When both token and password are present, token should be non-null
        // and the app logic should prefer token over password
        val config = DeepLinkConfig(
            serverUrl = "https://example.com",
            username = "kevin",
            password = "oldpassword",
            token = "securetoken456",
            board = "main"
        )
        assertNotNull("Token should be present", config.token)
        assertEquals("securetoken456", config.token)
        // Password is still available for backward compat
        assertEquals("oldpassword", config.password)
    }

    @Test
    fun deepLinkConfig_defaults() {
        val config = DeepLinkConfig()
        assertEquals("", config.serverUrl)
        assertEquals("", config.username)
        assertEquals("", config.password)
        assertNull(config.token)
        assertEquals("", config.board)
    }
}
