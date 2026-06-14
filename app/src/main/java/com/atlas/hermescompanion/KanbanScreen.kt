package com.atlas.hermescompanion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val STATUS_COLUMNS = listOf("todo", "ready", "running", "blocked", "done")
private val STATUS_LABELS = mapOf(
    "todo" to "To Do", "ready" to "Ready", "running" to "Running",
    "blocked" to "Blocked", "done" to "Done",
)
private val STATUS_COLORS = mapOf(
    "todo" to androidx.compose.ui.graphics.Color(0xFFF9E2AF),
    "ready" to androidx.compose.ui.graphics.Color(0xFF89B4FA),
    "running" to androidx.compose.ui.graphics.Color(0xFFCBA6F7),
    "blocked" to androidx.compose.ui.graphics.Color(0xFFF38BA8),
    "done" to androidx.compose.ui.graphics.Color(0xFFA6E3A1),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanScreen(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val boards by viewModel.boards.collectAsState()
    val tasksByStatus by viewModel.tasksByStatus.collectAsState()
    val selectedTask by viewModel.selectedTask.collectAsState()
    val boardSlug by viewModel.boardSlug.collectAsState()
    val error by viewModel.kanbanError.collectAsState()
    var boardExpanded by remember { mutableStateOf(false) }
    var detailSheet by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadBoards(); viewModel.loadTasks() }

    Column(modifier = modifier.fillMaxSize()) {
        // Board picker
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Board:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { boardExpanded = true }) {
                        Text(boardSlug, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = boardExpanded, onDismissRequest = { boardExpanded = false }) {
                        boards.forEach { board ->
                            DropdownMenuItem(
                                text = { Text("${board.name} (${board.slug})") },
                                onClick = {
                                    viewModel.setBoard(board.slug)
                                    boardExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.loadTasks() }) {
                    Icon(Icons.Filled.Refresh, "Refresh")
                }
            }
        }

        // Error
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall)
        }

        // Kanban columns (horizontal scroll)
        Row(
            modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
        ) {
            STATUS_COLUMNS.forEach { status ->
                KanbanColumn(
                    status = status,
                    label = STATUS_LABELS[status] ?: status,
                    color = STATUS_COLORS[status] ?: MaterialTheme.colorScheme.primary,
                    tasks = tasksByStatus[status] ?: emptyList(),
                    onTaskClick = { taskId -> viewModel.loadTask(taskId); detailSheet = true },
                )
            }
        }
    }

    // Task detail bottom sheet
    selectedTask?.let { task ->
        if (detailSheet) {
        ModalBottomSheet(onDismissRequest = { detailSheet = false; viewModel.clearSelectedTask() }) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(task.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = STATUS_COLORS[task.status]?.copy(alpha = 0.2f) ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)) {
                        Text(task.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (task.assignee != null) {
                        Text("Assignee: ${task.assignee}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (!task.body.isNullOrBlank()) {
                    Text(task.body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Comments
                if (task.comments.isNotEmpty()) {
                    Text("Comments", style = MaterialTheme.typography.titleSmall)
                    task.comments.forEach { c ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(c.author, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(c.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Add comment
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add comment...") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            viewModel.commentOnTask(task.id, commentText)
                            commentText = ""
                        }, enabled = commentText.isNotBlank()) {
                            Icon(Icons.Filled.Send, "Send")
                        }
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.status != "done") {
                        Button(onClick = { viewModel.completeTask(task.id); detailSheet = false },
                            colors = ButtonDefaults.buttonColors(containerColor = STATUS_COLORS["done"]!!.copy(alpha = 0.3f))) {
                            Text("Complete", color = STATUS_COLORS["done"]!!)
                        }
                    }
                    OutlinedButton(onClick = {
                        viewModel.assignTask(task.id, "analyst")
                    }) {
                        Text("Assign")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        }  // let
    }
}

@Composable
fun KanbanColumn(
    status: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    tasks: List<com.atlas.hermescompanion.data.KanbanTask>,
    onTaskClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.width(180.dp).padding(4.dp),
    ) {
        // Header
        Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Surface(color = color.copy(alpha = 0.4f), shape = CircleShape) {
                    Text("${tasks.size}", modifier = Modifier.padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Task cards
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 60.dp, max = 400.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(tasks) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onTaskClick(task.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(task.title, style = MaterialTheme.typography.bodySmall,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (task.assignee != null) {
                            Text(task.assignee, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (tasks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}
