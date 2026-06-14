package com.atlas.hermescompanion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlas.hermescompanion.data.HermesSession
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val vm = viewModel
    val sessions by vm.sessions.collectAsState()
    val activeId by vm.activeSessionId.collectAsState()
    val messages by vm.activeMessages.collectAsState()
    val isStreaming by vm.isStreaming.collectAsState()
    val error by vm.chatError.collectAsState()
    var showDrawer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadSessions() }

    Column(modifier = modifier.fillMaxSize()) {
        // Session drawer (overlays at top when open)
        if (showDrawer) {
            SessionDrawer(
                sessions = sessions,
                activeId = activeId,
                onSelect = { vm.selectSession(it); showDrawer = false },
                onNew = { vm.newSession(); showDrawer = false },
                onDismiss = { showDrawer = false },
                onDelete = { vm.deleteSession(it) },
                searchQuery = vm.sessionSearchQuery.collectAsState().value,
                onSearchChange = { vm.setSessionSearchQuery(it) },
            )
        }

        // Top bar with session switcher + count badge
        TopAppBar(
            title = { Text("Atlas") },
            navigationIcon = {
                BadgedBox(
                    badge = {
                        if (sessions.isNotEmpty()) {
                            Badge { Text("${sessions.size}") }
                        }
                    }
                ) {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Filled.Menu, "Sessions")
                    }
                }
            },
            actions = {
                IconButton(onClick = { vm.newSession() }) {
                    Icon(Icons.Filled.Add, "New session")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )

        // Error banner
        error?.let { msg ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(msg, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        // Message list (fills remaining space)
        MessageList(
            messages = messages,
            isStreaming = isStreaming,
            modifier = Modifier.weight(1f),
        )

        // Composer pinned to bottom
        Composer(
            onSendText = { text -> vm.sendMessage(text) },
            onSendAttachment = { text, bytes, mime -> vm.sendMessageWithAttachment(text, bytes, mime) },
            enabled = !isStreaming,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Relative-time label from epoch seconds. */
private fun formatRelativeTime(epochSeconds: Double?): String {
    if (epochSeconds == null) return ""
    val instant = Instant.ofEpochSecond(epochSeconds.toLong())
    val now = Instant.now()
    val mins = ChronoUnit.MINUTES.between(instant, now)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        mins < 10080 -> "${mins / 1440}d ago"
        else -> {
            val zdt = instant.atZone(ZoneId.systemDefault())
            val year = zdt.year
            val nowYear = now.atZone(ZoneId.systemDefault()).year
            if (year == nowYear)
                java.time.format.DateTimeFormatter.ofPattern("MMM d").format(zdt)
            else
                java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").format(zdt)
        }
    }
}

/** Sidebar with search, session list, and delete. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionDrawer(
    sessions: List<HermesSession>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<HermesSession?>(null) }

    val filtered = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) sessions
        else sessions.filter { s ->
            (s.title ?: "").contains(searchQuery, ignoreCase = true) ||
                s.id.contains(searchQuery, ignoreCase = true)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sessions",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${sessions.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Search sessions…") },
                leadingIcon = { Icon(Icons.Filled.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "No matching sessions"
                        else "No sessions yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filtered, key = { it.id }) { session ->
                        val isActive = session.id == activeId
                        val bgColor = if (isActive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface

                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .combinedClickable(
                                    onClick = { onSelect(session.id) },
                                    onLongClick = { deleteTarget = session },
                                ),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = session.title?.ifBlank { null }
                                            ?: "Session ${session.id.take(8)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                        color = if (isActive)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (session.messageCount > 0) {
                                        Text(
                                            "${session.messageCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    session.model?.let { model ->
                                        Text(
                                            model,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    val timeLabel = formatRelativeTime(session.startedAt)
                                    if (timeLabel.isNotEmpty()) {
                                        Text(
                                            timeLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // New session button
            OutlinedButton(
                onClick = onNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Session")
            }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete session?") },
            text = {
                Text(
                    session.title?.ifBlank { "Session ${session.id.take(8)}" }
                        ?: "Session ${session.id.take(8)}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(session.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
            icon = { Icon(Icons.Filled.DeleteOutline, null) },
        )
    }
}
