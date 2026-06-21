package org.hermes.community.companion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.hermes.community.companion.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionManager(app)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    companion object {
        private val STATUSES = listOf("triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done")
    }

    // Settings (persisted in DataStore)
    private val _password = MutableStateFlow(SessionManager.DEFAULT_PASSWORD)
    init {
        viewModelScope.launch {
            session.password.collect { _password.value = it }
        }
        // Keep the Application's auth credentials in sync with settings
        val app = app as? CompanionApp
        if (app != null) {
            viewModelScope.launch {
                combine(session.baseUrl, session.username, session.password) { u, user, pass ->
                    app.setAuth(user, pass, u)
                }.collect {}
            }
        }
    }

    val baseUrl = session.baseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, SessionManager.DEFAULT_URL)
    val username = session.username.stateIn(viewModelScope, SharingStarted.Eagerly, SessionManager.DEFAULT_USERNAME)
    val boardSlug = session.board.stateIn(viewModelScope, SharingStarted.Eagerly, SessionManager.DEFAULT_BOARD)

    // ─── Chat State ─────────────────────────────────────────
    data class AttachmentMeta(
        val id: String,
        val url: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
    )

    data class ChatMessage(
        val role: String,
        val content: String,
        val isStreaming: Boolean = false,
        val sessionId: String? = null,
        val messageId: String = java.util.UUID.randomUUID().toString(),
        val attachmentUrl: String? = null,
        val attachmentMeta: AttachmentMeta? = null,
    )

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    // Session list + active session
    private val _sessions = MutableStateFlow<List<HermesSession>>(emptyList())
    val sessions: StateFlow<List<HermesSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    // Search / filtering for session drawer
    private val _sessionSearchQuery = MutableStateFlow("")
    val sessionSearchQuery: StateFlow<String> = _sessionSearchQuery.asStateFlow()

    val filteredSessions: StateFlow<List<HermesSession>> = combine(_sessions, _sessionSearchQuery) { all, q ->
        if (q.isBlank()) all
        else all.filter { s ->
            (s.title ?: "").contains(q, ignoreCase = true) ||
                s.id.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Update the session drawer search query. */
    fun setSessionSearchQuery(query: String) {
        _sessionSearchQuery.value = query
    }

    // Derived: messages for the active session only
    val activeMessages: StateFlow<List<ChatMessage>> = combine(_chatMessages, _activeSessionId) { msgs, sid ->
        msgs.filter { it.sessionId == sid }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ─── Kanban State ───────────────────────────────────────
    private val _boards = MutableStateFlow<List<KanbanBoard>>(emptyList())
    val boards: StateFlow<List<KanbanBoard>> = _boards.asStateFlow()

    private val _tasks = MutableStateFlow<List<KanbanTask>>(emptyList())
    val tasks: StateFlow<List<KanbanTask>> = _tasks.asStateFlow()

    private val _selectedTask = MutableStateFlow<TaskShowResponse?>(null)
    val selectedTask: StateFlow<TaskShowResponse?> = _selectedTask.asStateFlow()

    private val _kanbanError = MutableStateFlow<String?>(null)
    val kanbanError: StateFlow<String?> = _kanbanError.asStateFlow()

    val tasksByStatus: StateFlow<Map<String, List<KanbanTask>>> = _tasks.map { it.groupBy { it.status } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun client(): ApiClient? {
        val url = baseUrl.value
        val user = username.value
        val pass = _password.value
        return if (url.isNotBlank()) ApiClient(url, user, pass) else null
    }

    // ─── Chat Actions ───────────────────────────────────────

    /** Create a new session on Hermes, select it, clear messages, load history. */
    fun newSession() {
        val c = client() ?: return
        _chatError.value = null
        viewModelScope.launch {
            try {
                val raw = c.post("/api/sessions", "{}")
                // Companion normalizes response to {data:[{id:...}]}
                val id = json.decodeFromString<SessionsList>(raw).data.firstOrNull()?.id
                if (id != null) {
                    _activeSessionId.value = id
                    _chatMessages.value = loadSessionHistory(id)
                } else {
                    _chatError.value = "Failed to create session"
                }
            } catch (e: Exception) {
                _chatError.value = e.message
            }
        }
    }

    /** Select an existing session and load its history. */
    fun selectSession(id: String) {
        _activeSessionId.value = id
        viewModelScope.launch {
            _chatMessages.value = loadSessionHistory(id)
        }
    }

    /** Suspend — returns loaded messages (or empty on error). Callers decide when to set _chatMessages. */
    private suspend fun loadSessionHistory(sessionId: String): List<ChatMessage> {
        val c = client() ?: return emptyList()
        return try {
            val raw = c.get("/api/sessions/$sessionId/messages")
            val sessionMsgs = json.decodeFromString<SessionMessages>(raw)
            // Build a map of attachment URL by index for quick lookup
            sessionMsgs.data.map { m ->
                ChatMessage(
                    role = m.role,
                    content = m.content,
                    sessionId = sessionId,
                    attachmentUrl = m.attachmentUrl,
                )
            }
        } catch (e: Exception) {
            _chatError.value = "History load failed: ${e.message}"
            emptyList()
        }
    }

    /** Send a message in the active session using SSE streaming chat. */
    fun sendMessage(content: String) {
        val c = client() ?: return
        _chatError.value = null
        viewModelScope.launch {
            // Ensure session exists before sending — await history so messages aren't wiped
            if (_activeSessionId.value == null) {
                try {
                    val raw = c.post("/api/sessions", "{}")
                    val ses = json.decodeFromString<SessionsList>(raw).data.firstOrNull()
                    if (ses != null) {
                        _activeSessionId.value = ses.id
                        _chatMessages.value = loadSessionHistory(ses.id)
                    } else {
                        _chatError.value = "Failed to create session"
                        return@launch
                    }
                } catch (e: Exception) {
                    _chatError.value = e.message
                    return@launch
                }
            }
            val sid = _activeSessionId.value ?: return@launch

            // Add user message immediately
            val userMsg = ChatMessage("user", content, sessionId = sid)
            _chatMessages.value = _chatMessages.value + userMsg

            // Placeholder for response with unique ID for race-free finalization
            val msgId = java.util.UUID.randomUUID().toString()
            val assistantMsg = ChatMessage("assistant", "", isStreaming = true, sessionId = sid, messageId = msgId)
            _chatMessages.value = _chatMessages.value + assistantMsg
            _isStreaming.value = true

            val history = _chatMessages.value
                .filter { !it.isStreaming && it.sessionId == sid }
                .map { mapOf("role" to it.role, "content" to it.content) }

            try {
                c.chatStream(
                    messages = history,
                    sessionId = sid,
                    onChunk = { delta ->
                        // Append delta to the streaming assistant message
                        _chatMessages.value = _chatMessages.value.map { msg ->
                            if (msg.messageId == msgId && msg.isStreaming) {
                                msg.copy(content = msg.content + delta)
                            } else {
                                msg
                            }
                        }
                    },
                )
                _isStreaming.value = false
                // Finalize: ensure the message is marked non-streaming
                finalizeAssistant(msgId, _chatMessages.value
                    .firstOrNull { it.messageId == msgId }?.content ?: "")
            } catch (e: Exception) {
                _chatError.value = e.message
                _isStreaming.value = false
                finalizeAssistant(msgId, "(Error: ${e.message})")
            }
        }
    }

    /** Send a message with an attached image in the active session. */
    fun sendMessageWithAttachment(content: String, imageBytes: ByteArray, mimeType: String, fileName: String = "image.jpg") {
        val c = client() ?: return
        _chatError.value = null
        viewModelScope.launch {
            try {
                // Upload attachment first
                val upResp = c.uploadAttachment(imageBytes, fileName, mimeType)
                val attJson = json.parseToJsonElement(upResp).jsonObject
                val attId = attJson["id"]?.jsonPrimitive?.content ?: return@launch
                val attUrl = "${baseUrl.value}/api/attachments/$attId"
                val attWidth = attJson["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val attHeight = attJson["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val attMeta = AttachmentMeta(attId, attUrl, mimeType, attWidth, attHeight)

                // Ensure session exists
                if (_activeSessionId.value == null) {
                    val raw = c.post("/api/sessions", "{}")
                    val ses = json.decodeFromString<SessionsList>(raw).data.firstOrNull()
                    if (ses != null) {
                        _activeSessionId.value = ses.id
                        _chatMessages.value = loadSessionHistory(ses.id)
                    } else {
                        _chatError.value = "Failed to create session"
                        return@launch
                    }
                }
                val sid = _activeSessionId.value ?: return@launch

                // Add user message with attachment
                val userMsg = ChatMessage("user", content, sessionId = sid,
                    attachmentUrl = attUrl, attachmentMeta = attMeta)
                _chatMessages.value = _chatMessages.value + userMsg

                // Placeholder for response
                val msgId = java.util.UUID.randomUUID().toString()
                val assistantMsg = ChatMessage("assistant", "", isStreaming = true, sessionId = sid, messageId = msgId)
                _chatMessages.value = _chatMessages.value + assistantMsg
                _isStreaming.value = true

                val history = _chatMessages.value
                    .filter { !it.isStreaming && it.sessionId == sid }
                    .map { mapOf("role" to it.role, "content" to it.content) }

                try {
                    val reply = c.chat(history, sessionId = sid, attachmentIds = listOf(attId))
                    _isStreaming.value = false
                    finalizeAssistant(msgId, reply)
                } catch (e: Exception) {
                    _chatError.value = e.message
                    _isStreaming.value = false
                    finalizeAssistant(msgId, "(Error: ${e.message})")
                }
            } catch (e: Exception) {
                _chatError.value = e.message
                _isStreaming.value = false
            }
        }
    }

    /** Replace the streaming placeholder with the final response, identified by unique messageId.
     *  Uses messageId instead of lastIndex to avoid race conditions when multiple
     *  sendMessage() calls are in-flight concurrently. */
    private fun finalizeAssistant(messageId: String, text: String) {
        _chatMessages.value = _chatMessages.value.map { msg ->
            if (msg.messageId == messageId && msg.isStreaming) {
                ChatMessage("assistant", text, sessionId = msg.sessionId, messageId = msg.messageId)
            } else {
                msg
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        _chatError.value = null
    }

    /** Load all visible sessions (sidebar list). */
    fun loadSessions() {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                val all = mutableListOf<HermesSession>()
                var offset = 0
                var hasMore = true
                while (hasMore) {
                    val raw = c.get("/api/sessions?limit=100&offset=$offset")
                    val list = json.decodeFromString<SessionsList>(raw)
                    all.addAll(list.data)
                    hasMore = list.data.size >= 100
                    offset += list.data.size
                }
                _sessions.value = all
            } catch (e: Exception) {
                _chatError.value = e.message
            }
        }
    }

    // ─── Kanban Actions ─────────────────────────────────────
    fun loadBoards() {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val raw = c.get("/api/kanban/boards")
                _boards.value = json.decodeFromString<List<KanbanBoard>>(raw)
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }

    fun createBoard(slug: String, name: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("slug", JsonPrimitive(slug))
                    put("name", JsonPrimitive(name))
                }.toString()
                c.post("/api/kanban/boards", body)
                loadBoards()
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }

    fun renameBoard(slug: String, newName: String) {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("name", JsonPrimitive(newName))
                }.toString()
                c.post("/api/kanban/boards/$slug/rename", body)
                loadBoards()
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }

    fun archiveBoard(slug: String) {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                c.post("/api/kanban/boards/$slug/archive")
                loadBoards()
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }

    fun deleteBoard(slug: String) {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                c.delete("/api/kanban/boards/$slug")
                loadBoards()
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }
    fun loadTasks(board: String? = null) {
        val c = client() ?: return
        val b = board ?: boardSlug.value
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val raw = c.get("/api/kanban/tasks?board=$b")
                _tasks.value = json.decodeFromString<List<KanbanTask>>(raw)
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }
    fun loadTask(taskId: String) {
        val c = client() ?: return
        val b = boardSlug.value
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val raw = c.get("/api/kanban/tasks/$taskId?board=$b")
                _selectedTask.value = json.decodeFromString<TaskShowResponse>(raw)
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }
    fun completeTask(taskId: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                c.post("/api/kanban/tasks/$taskId/complete?board=${boardSlug.value}")
                loadTasks()
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }
    fun commentOnTask(taskId: String, text: String) {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("text", JsonPrimitive(text))
                }.toString()
                c.post("/api/kanban/tasks/$taskId/comment?board=${boardSlug.value}", body)
                loadTask(taskId)  // Refresh the task to see new comment
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }
    fun assignTask(taskId: String, assignee: String) {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("assignee", JsonPrimitive(assignee))
                }.toString()
                c.post("/api/kanban/tasks/$taskId/assign?board=${boardSlug.value}", body)
                loadTasks()
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }

    // ─── New T12 kanban actions ─────────────────────────────

    fun updateTaskTitle(taskId: String, title: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject { put("title", JsonPrimitive(title)) }.toString()
                c.patch("/api/kanban/tasks/$taskId?board=${boardSlug.value}", body)
                loadTask(taskId)
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    fun updateTaskBody(taskId: String, body: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject { put("body", JsonPrimitive(body)) }.toString()
                c.patch("/api/kanban/tasks/$taskId?board=${boardSlug.value}", body)
                loadTask(taskId)
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    fun updateTaskStatus(taskId: String, status: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject { put("status", JsonPrimitive(status)) }.toString()
                c.patch("/api/kanban/tasks/$taskId?board=${boardSlug.value}", body)
                loadTask(taskId)
                loadTasks()
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    fun updateTaskPriority(taskId: String, priority: Int) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject { put("priority", JsonPrimitive(priority)) }.toString()
                c.patch("/api/kanban/tasks/$taskId?board=${boardSlug.value}", body)
                loadTask(taskId)
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    fun deleteTask(taskId: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                c.delete("/api/kanban/tasks/$taskId?board=${boardSlug.value}")
                _selectedTask.value = null
                loadTasks()
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    /** Upload an attachment to a task. */
    fun uploadTaskAttachment(taskId: String, bytes: ByteArray, fileName: String, mimeType: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                c.uploadAttachment(bytes, fileName, mimeType)
                loadTask(taskId)
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }

    fun addDependency(parentId: String, childId: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("parent_id", JsonPrimitive(parentId))
                    put("child_id", JsonPrimitive(childId))
                }.toString()
                c.post("/api/kanban/links?board=${boardSlug.value}", body)
                loadTask(childId)
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    // Profiles for assignee dropdown
    private val _profiles = MutableStateFlow<List<String>>(emptyList())
    val profiles: StateFlow<List<String>> = _profiles.asStateFlow()

    fun loadProfiles() {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                val raw = c.get("/api/kanban/profiles")
                val arr = json.parseToJsonElement(raw).jsonArray
                _profiles.value = arr.map { it.jsonPrimitive.content }
            } catch (_: Exception) {
                _profiles.value = listOf("analyst", "ops", "researcher", "writer")
            }
        }
    }

    fun setBoard(board: String) {
        viewModelScope.launch {
            session.setBoard(board)
            loadSessions()
            loadBoards()
            loadTasks(board)
        }
    }
    
    // Snackbar state
    private val _snackbarText = MutableStateFlow<String?>(null)
    val snackbarText: StateFlow<String?> = _snackbarText.asStateFlow()

    fun showSnackbar(text: String) {
        _snackbarText.value = text
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_snackbarText.value == text) _snackbarText.value = null
        }
    }

    fun clearSelectedTask() { _selectedTask.value = null }

    // ── T13: Board Stats ──
    private val _stats = MutableStateFlow(BoardStats())
    val stats: StateFlow<BoardStats> = _stats.asStateFlow()

    fun loadStats() {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                val raw = c.get("/api/kanban/stats?board=${boardSlug.value}")
                _stats.value = json.decodeFromString<BoardStats>(raw)
            } catch (_: Exception) {
                // Compute from local task list as fallback
                val tasks = _tasks.value
                val counts = mutableMapOf<String, Int>()
                STATUSES.forEach { s -> counts[s] = tasks.count { it.status == s } }
                val oldestReady = tasks.filter { it.status == "ready" }
                    .minOfOrNull { it.age?.createdAgeSeconds ?: Long.MAX_VALUE }
                _stats.value = BoardStats(
                    total = tasks.size,
                    countsByStatus = counts,
                    oldestReadyAgeSeconds = if (oldestReady == Long.MAX_VALUE) null else oldestReady,
                )
            }
        }
    }

    // ── T13: Create Task ──
    fun createTask(title: String, status: String, assignee: String, priority: Int) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("title", JsonPrimitive(title))
                    put("status", JsonPrimitive(status))
                    put("assignee", JsonPrimitive(assignee))
                    put("priority", JsonPrimitive(priority))
                }.toString()
                c.post("/api/kanban/tasks?board=${boardSlug.value}", body)
                loadTasks()
                loadStats()
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    // ── T13: Bulk Update Status ──
    fun bulkUpdateStatus(taskIds: List<String>, status: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("task_ids", kotlinx.serialization.json.JsonArray(taskIds.map { JsonPrimitive(it) }))
                    put("action", JsonPrimitive("set_status"))
                    put("value", JsonPrimitive(status))
                }.toString()
                c.post("/api/kanban/tasks/bulk?board=${boardSlug.value}", body)
                loadTasks()
                loadStats()
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    // ── T13: Bulk Reassign ──
    fun bulkReassign(taskIds: List<String>, assignee: String) {
        val c = client() ?: return
        _kanbanError.value = null
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("task_ids", kotlinx.serialization.json.JsonArray(taskIds.map { JsonPrimitive(it) }))
                    put("action", JsonPrimitive("set_assignee"))
                    put("value", JsonPrimitive(assignee))
                }.toString()
                c.post("/api/kanban/tasks/bulk?board=${boardSlug.value}", body)
                loadTasks()
                loadStats()
            } catch (e: Exception) { _kanbanError.value = e.message }
        }
    }

    /** Delete a session on the server and remove it from the local list. */
    fun deleteSession(id: String) {
        val c = client() ?: return
        viewModelScope.launch {
            try {
                c.delete("/api/sessions/$id")
                _sessions.value = _sessions.value.filter { it.id != id }
                if (_activeSessionId.value == id) {
                    _activeSessionId.value = null
                    _chatMessages.value = emptyList()
                }
            } catch (e: Exception) {
                _chatError.value = "Delete failed: ${e.message}"
            }
        }
    }

    fun saveSettings(url: String, user: String, password: String) {
        viewModelScope.launch {
            session.setBaseUrl(url)
            session.setUsername(user)
            if (password.isNotBlank()) session.setPassword(password)
            _password.value = password.ifBlank { _password.value }
            // Retry with new credentials
            _chatError.value = null
            loadSessions()
        }
    }

    // ─── Composer Input State ────────────────────────────────
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun clearInput() {
        _inputText.value = ""
    }

    // ─── Setup Wizard ────────────────────────────────────────
    private val _deepLinkConfig = MutableStateFlow<DeepLinkConfig?>(null)
    val deepLinkConfig: StateFlow<DeepLinkConfig?> = _deepLinkConfig.asStateFlow()

    fun setDeepLinkConfig(config: DeepLinkConfig) {
        _deepLinkConfig.value = config
    }

    fun clearDeepLinkConfig() {
        _deepLinkConfig.value = null
    }

    // ─── Setup Token Redemption ─────────────────────────────

    /**
     * Redeem a setup token against the daemon's /api/setup/redeem endpoint.
     * On success, populates SessionManager with the returned credentials.
     */
    suspend fun redeemSetupToken(baseUrl: String, token: String): Result<Unit> {
        val response = org.hermes.community.companion.data.redeemSetupToken(baseUrl, token).getOrElse {
            return Result.failure(it)
        }
        session.setBaseUrl("https://${response.host}:${response.port}")
        session.setUsername(response.username)
        session.setPassword(response.password)
        session.setBoard(response.board)
        return Result.success(Unit)
    }

    // ─── Helpers ────────────────────────────────────────────
}
