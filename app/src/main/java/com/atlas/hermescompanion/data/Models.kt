package com.atlas.hermescompanion.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KanbanBoard(
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val counts: KanbanCounts? = null,
    val total: Int = 0,
    val archived: Boolean = false,
)

@Serializable
data class KanbanCounts(
    val todo: Int = 0,
    val ready: Int = 0,
    val running: Int = 0,
    val blocked: Int = 0,
    val done: Int = 0,
    val archived: Int = 0,
)

@Serializable
data class KanbanTask(
    val id: String = "",
    val title: String = "",
    val status: String = "",
    val assignee: String? = null,
    val priority: Int = 1,
    val body: String? = null,
    val created: String? = null,
    val updated: String? = null,
)

@Serializable
data class TaskShowResponse(
    val id: String,
    val title: String,
    val status: String,
    val assignee: String? = null,
    val priority: Int = 1,
    val body: String? = null,
    val comments: List<KanbanComment> = emptyList(),
    val events: List<KanbanEvent> = emptyList(),
)

@Serializable
data class KanbanComment(
    val author: String = "",
    val body: String = "",
    val at: String? = null,
)

@Serializable
data class KanbanEvent(
    val kind: String = "",
    val at: String? = null,
    val profile: String? = null,
)

@Serializable
data class HermesSession(
    val id: String = "",
    val title: String? = null,
    val model: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("message_count") val messageCount: Int = 0,
)

@Serializable
data class CompanionHealth(
    val status: String = "",
    @SerialName("companion_version") val version: String = "",
    @SerialName("hermes_api_reachable") val hermesReachable: Boolean = false,
)

/** Real session list response from Hermes API. */
@Serializable
data class SessionsList(
    val data: List<HermesSession> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

/** Real chat message stored on the Hermes side. Used to rehydrate history. */
@Serializable
data class HermesChatMessage(
    val role: String = "",
    val content: String = "",
    val at: Double? = null,
)

@Serializable
data class SessionMessages(
    val data: List<HermesChatMessage> = emptyList(),
)
