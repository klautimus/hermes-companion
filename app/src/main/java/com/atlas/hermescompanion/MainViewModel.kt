package com.atlas.hermescompanion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.hermescompanion.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.Json

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionManager(app)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Settings (persisted in DataStore)
    private val _password = MutableStateFlow(SessionManager.DEFAULT_PASSWORD)
    init { viewModelScope.launch { session.password.collect { _password.value = it } } }

    val baseUrl = session.baseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, SessionManager.DEFAULT_URL)
    val username = session.username.stateIn(viewModelScope, SharingStarted.Eagerly, SessionManager.DEFAULT_USERNAME)
    val boardSlug = session.board.stateIn(viewModelScope, SharingStarted.Eagerly, SessionManager.DEFAULT_BOARD)

    // ─── Chat State ─────────────────────────────────────────
    data class ChatMessage(
        val role: String,
        val content: String,
        val isStreaming: Boolean = false,
        val sessionId: String? = null,
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
                val ses = json.decodeFromString<SessionsList>(raw).data.firstOrNull()
                ses?.let { s ->
                    _activeSessionId.value = s.id
                    _chatMessages.value = loadSessionHistory(s.id)
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
            json.decodeFromString<SessionMessages>(raw).data.map { m ->
                ChatMessage(m.role, m.content, sessionId = sessionId)
            }
        } catch (e: Exception) {
            _chatError.value = "History load failed: ${e.message}"
            emptyList()
        }
    }

    /** Send a message in the active session using non-streaming chat. */
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

            // Placeholder for response
            val assistantMsg = ChatMessage("assistant", "", isStreaming = true, sessionId = sid)
            _chatMessages.value = _chatMessages.value + assistantMsg
            _isStreaming.value = true

            val history = _chatMessages.value
                .filter { !it.isStreaming && it.sessionId == sid }
                .map { mapOf("role" to it.role, "content" to it.content) }

            try {
                val reply = c.chat(history)
                _isStreaming.value = false
                finalizeAssistant(sid, reply)
            } catch (e: Exception) {
                _chatError.value = e.message
                _isStreaming.value = false
                finalizeAssistant(sid, "(error: ${e.message})")
            }
        }
    }

    private fun finalizeAssistant(sid: String, text: String) {
        val idx = _chatMessages.value.lastIndex
        if (idx >= 0 && _chatMessages.value[idx].sessionId == sid) {
            _chatMessages.value = _chatMessages.value.toMutableList().apply {
                set(idx, ChatMessage("assistant", text, sessionId = sid))
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
                val raw = c.get("/api/sessions")
                _sessions.value = json.decodeFromString<SessionsList>(raw).data
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
                val body = "{\"text\":\"${text.replace("\"", "\\\"")}\"}"
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
                val body = "{\"assignee\":\"$assignee\"}"
                c.post("/api/kanban/tasks/$taskId/assign?board=${boardSlug.value}", body)
                loadTasks()
            } catch (e: Exception) {
                _kanbanError.value = e.message
            }
        }
    }
    fun setBoard(board: String) {
        viewModelScope.launch {
            session.setBoard(board)
            loadSessions()
            loadBoards()
        }
    }
    fun clearSelectedTask() { _selectedTask.value = null }

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

    // ─── Helpers ────────────────────────────────────────────
    private fun passwordNow() = _password.value
}
