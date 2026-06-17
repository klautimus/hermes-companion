package org.hermes.community.companion.data

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
    val triage: Int = 0,
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
    @SerialName("created_at") val created: String? = null,
    @SerialName("updated_at") val updated: String? = null,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("link_count") val linkCount: Int = 0,
    @SerialName("link_counts") val linkCounts: LinkCounts? = null,
    val progress: Progress? = null,
    @SerialName("latest_summary") val latestSummary: String? = null,
    val age: Age? = null,
    @SerialName("started_at") val startedAt: Long? = null,
    val warnings: List<String>? = null,
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
    val parents: List<TaskRef> = emptyList(),
    val children: List<TaskRef> = emptyList(),
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("started_at") val startedAt: Long = 0,
    val result: String? = null,
    @SerialName("latest_summary") val latestSummary: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val links: DependencyLinks? = null,
    val runs: List<Run> = emptyList(),
    val tenant: String? = null,
    @SerialName("workspace_kind") val workspaceKind: String? = null,
    val age: Age? = null,
    @SerialName("link_counts") val linkCounts: LinkCounts? = null,
    @SerialName("comment_count") val commentCount: Int = 0,
    val progress: Progress? = null,
    val diagnostics: List<String>? = null,
    val warnings: List<String>? = null,
)

@Serializable
data class TaskRef(
    val id: String = "",
    val title: String = "",
    val status: String = "",
)

@Serializable
data class KanbanComment(
    val author: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class KanbanEvent(
    val kind: String = "",
    val profile: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
)

// ── New data classes for full kanban parity ──

@Serializable
data class Attachment(
    val id: String = "",
    val filename: String = "",
    @SerialName("content_type") val contentType: String? = null,
    val size: Int = 0,
    @SerialName("uploaded_by") val uploadedBy: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class DependencyLinks(
    val parents: List<KanbanTask> = emptyList(),
    val children: List<KanbanTask> = emptyList(),
)

@Serializable
data class Run(
    val id: String = "",
    val profile: String? = null,
    val status: String = "",
    val outcome: String? = null,
    val summary: String? = null,
    @SerialName("started_at") val startedAt: Long = 0,
    @SerialName("ended_at") val endedAt: Long = 0,
    val error: String? = null,
)

@Serializable
data class Age(
    @SerialName("created_age_seconds") val createdAgeSeconds: Long? = null,
    @SerialName("started_age_seconds") val startedAgeSeconds: Long? = null,
    @SerialName("time_to_complete_seconds") val timeToCompleteSeconds: Long? = null,
)

@Serializable
data class LinkCounts(
    val parents: Int = 0,
    val children: Int = 0,
)

@Serializable
data class Progress(
    val done: Int = 0,
    val total: Int = 0,
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

@Serializable
data class BoardStats(
    val total: Int = 0,
    @SerialName("counts_by_status") val countsByStatus: Map<String, Int> = emptyMap(),
    @SerialName("oldest_ready_age_seconds") val oldestReadyAgeSeconds: Long? = null,
)

@Serializable
data class BulkUpdateRequest(
    val task_ids: List<String> = emptyList(),
    val action: String = "",
    val value: String = "",
)

// ── Setup Wizard ──

data class DeepLinkConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val token: String? = null,  // NEW: setup token, takes precedence over password
    val board: String = "",
)