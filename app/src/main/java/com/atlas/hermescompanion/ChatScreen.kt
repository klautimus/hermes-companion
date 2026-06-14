package com.atlas.hermescompanion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atlas.hermescompanion.data.HermesSession
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api

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
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // Session drawer
        if (showDrawer) {
            SessionDrawer(
                sessions = sessions,
                activeId = activeId,
                onSelect = { vm.selectSession(it); showDrawer = false },
                onNew = { vm.newSession(); showDrawer = false },
                onDismiss = { showDrawer = false },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                // Top bar with session switcher
                TopAppBar(
                    title = { Text("Atlas") },
                    navigationIcon = {
                        IconButton(onClick = { showDrawer = true }) {
                            Icon(Icons.Filled.Menu, "Sessions")
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

                // Message list
                MessageList(
                    messages = messages,
                    isStreaming = isStreaming,
                    modifier = Modifier.weight(1f),
                )
            }

            // Composer at bottom
            Composer(
                onSend = { text -> vm.sendMessage(text) },
                enabled = !isStreaming,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Sidebar with session list + new-session button. */
@Composable
fun SessionDrawer(
    sessions: List<HermesSession>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sessions", style = MaterialTheme.typography.titleMedium)
            Divider()

            if (sessions.isEmpty()) {
                Text("No sessions yet", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sessions.forEach { s ->
                    val isActive = s.id == activeId
                    Text(
                        text = s.title?.ifBlank { "Session ${s.id.take(8)}" } ?: "Session ${s.id.take(8)}",
                        style = if (isActive) MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            else MaterialTheme.typography.bodyMedium,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onSelect(s.id) }
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Text("New Session")
                Icon(Icons.Filled.Add, null)
            }
        }
    }
}
