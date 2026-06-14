package com.atlas.hermescompanion

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val STATUS_COLUMNS = listOf("todo", "ready", "running", "blocked", "done")
private val STATUS_LABELS = mapOf(
    "todo" to "To Do", "ready" to "Ready", "running" to "Running",
    "blocked" to "Blocked", "done" to "Done",
)
private val STATUS_COLORS = mapOf(
    "todo" to Color(0xFFF9E2AF),
    "ready" to Color(0xFF89B4FA),
    "running" to Color(0xFFCBA6F7),
    "blocked" to Color(0xFFF38BA8),
    "done" to Color(0xFFA6E3A1),
)

private val BOARD_COLORS = listOf(
    Color(0xFF89B4FA), Color(0xFFA6E3A1), Color(0xFFF9E2AF),
    Color(0xFFF38BA8), Color(0xFFCBA6F7), Color(0xFF94E2D5),
    Color(0xFFFAB387), Color(0xFFB4BEFE),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanScreen(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val boards by viewModel.boards.collectAsState()
    val tasksByStatus by viewModel.tasksByStatus.collectAsState()
    val selectedTask by viewModel.selectedTask.collectAsState()
    val boardSlug by viewModel.boardSlug.collectAsState()
    val error by viewModel.kanbanError.collectAsState()
    var detailSheet by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    // Drawer state
    var drawerOpen by remember { mutableStateOf(false) }
    var boardSearch by remember { mutableStateOf("") }

    // Context menu state
    var contextMenuBoard by remember { mutableStateOf<com.atlas.hermescompanion.data.KanbanBoard?>(null) }
    var activeBoardForDialog by remember { mutableStateOf<com.atlas.hermescompanion.data.KanbanBoard?>(null) }

    // Dialogs
    var createDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var archiveDialog by remember { mutableStateOf(false) }
    var newBoardSlug by remember { mutableStateOf("") }
    var newBoardName by remember { mutableStateOf("") }
    var renameBoardName by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadBoards(); viewModel.loadTasks() }

    Box(modifier = modifier.fillMaxSize()) {
        // Main content
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with board picker
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Board:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { drawerOpen = true }) {
                        Text(boardSlug, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Filled.ArrowDropDown, null)
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

            // Kanban columns
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
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

        // ── Board Drawer Overlay ──────────────────────────────
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
        ) {
            BoardDrawer(
                boards = boards,
                currentSlug = boardSlug,
                searchQuery = boardSearch,
                onSearchChange = { boardSearch = it },
                onBoardClick = { slug ->
                    viewModel.setBoard(slug)
                    drawerOpen = false
                },
                onBoardLongPress = { board ->
                    contextMenuBoard = board
                },
                onNewBoard = { createDialog = true },
                onDismiss = { drawerOpen = false },
            )
        }

        // ── Board Context Menu ────────────────────────────────
        contextMenuBoard?.let { board ->
            DropdownMenu(
                expanded = true,
                onDismissRequest = { contextMenuBoard = null },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        activeBoardForDialog = board
                        renameBoardName = board.name
                        contextMenuBoard = null
                        renameDialog = true
                    },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                )
                if (!board.archived) {
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        onClick = {
                            activeBoardForDialog = board
                            contextMenuBoard = null
                            archiveDialog = true
                        },
                        leadingIcon = { Icon(Icons.Filled.Archive, null) },
                    )
                }
                if (board.slug != "default") {
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            activeBoardForDialog = board
                            contextMenuBoard = null
                            deleteDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                    )
                }
            }
        }

        // ── Create Board Dialog ───────────────────────────────
        if (createDialog) {
            AlertDialog(
                onDismissRequest = { createDialog = false; dialogError = null },
                title = { Text("New Board") },
                text = {
                    Column {
                        dialogError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = newBoardSlug,
                            onValueChange = { newBoardSlug = it.lowercase().replace(Regex("[^a-z0-9-]"), "-") },
                            label = { Text("Slug") },
                            placeholder = { Text("my-project") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newBoardName,
                            onValueChange = { newBoardName = it },
                            label = { Text("Display Name (optional)") },
                            placeholder = { Text("My Project") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val slug = newBoardSlug.trim()
                            if (slug.isBlank()) {
                                dialogError = "Slug is required"
                                return@TextButton
                            }
                            viewModel.createBoard(slug, newBoardName.trim())
                            createDialog = false
                            newBoardSlug = ""
                            newBoardName = ""
                            dialogError = null
                        },
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        createDialog = false
                        newBoardSlug = ""
                        newBoardName = ""
                        dialogError = null
                    }) { Text("Cancel") }
                },
            )
        }

        // ── Rename Board Dialog ───────────────────────────────
        if (renameDialog) {
            val board = activeBoardForDialog
            AlertDialog(
                onDismissRequest = { renameDialog = false; dialogError = null },
                title = { Text("Rename Board") },
                text = {
                    Column {
                        dialogError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = renameBoardName,
                            onValueChange = { renameBoardName = it },
                            label = { Text("New Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = renameBoardName.trim()
                            if (name.isBlank()) {
                                dialogError = "Name cannot be empty"
                                return@TextButton
                            }
                            board?.let { viewModel.renameBoard(it.slug, name) }
                            renameDialog = false
                            dialogError = null
                        },
                    ) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        renameDialog = false; dialogError = null
                    }) { Text("Cancel") }
                },
            )
        }

        // ── Archive Board Dialog ──────────────────────────────
        if (archiveDialog) {
            val board = activeBoardForDialog
            AlertDialog(
                onDismissRequest = { archiveDialog = false },
                title = { Text("Archive Board") },
                text = {
                    Text("Archive \"${board?.name ?: board?.slug}\"?\n\n" +
                        "Archived boards are hidden by default but can be restored.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        board?.let { viewModel.archiveBoard(it.slug) }
                        archiveDialog = false
                    }) { Text("Archive") }
                },
                dismissButton = {
                    TextButton(onClick = { archiveDialog = false }) { Text("Cancel") }
                },
            )
        }

        // ── Delete Board Dialog ───────────────────────────────
        if (deleteDialog) {
            val board = activeBoardForDialog
            AlertDialog(
                onDismissRequest = { deleteDialog = false },
                title = { Text("Delete Board") },
                text = {
                    Text("Permanently delete \"${board?.name ?: board?.slug}\"?\n\n" +
                        "This cannot be undone. Tasks on this board will be lost.",
                        color = MaterialTheme.colorScheme.error)
                },
                confirmButton = {
                    TextButton(onClick = {
                        board?.let { viewModel.deleteBoard(it.slug) }
                        deleteDialog = false
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteDialog = false }) { Text("Cancel") }
                },
            )
        }
    }

    // ── Task Detail Bottom Sheet ─────────────────────────────
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (task.status != "done") {
                            Button(onClick = { viewModel.completeTask(task.id); detailSheet = false },
                                colors = ButtonDefaults.buttonColors(containerColor = STATUS_COLORS["done"]!!.copy(alpha = 0.3f))) {
                                Text("Complete", color = STATUS_COLORS["done"]!!)
                            }
                        }
                        OutlinedButton(onClick = {
                            viewModel.assignTask(task.id, viewModel.username.value)
                        }) {
                            Text("Assign")
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ── Board Drawer ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardDrawer(
    boards: List<com.atlas.hermescompanion.data.KanbanBoard>,
    currentSlug: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBoardClick: (String) -> Unit,
    onBoardLongPress: (com.atlas.hermescompanion.data.KanbanBoard) -> Unit,
    onNewBoard: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        onClick = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.8f)
                .clickable(enabled = false) {},  // consume clicks to prevent dismiss
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(tonalElevation = 2.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Boards", style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, "Close")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Search field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search boards...") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(Icons.Filled.Clear, "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                // Board list
                val filtered = if (searchQuery.isBlank()) boards
                else boards.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.slug.contains(searchQuery, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (searchQuery.isNotBlank()) "No boards matching \"$searchQuery\""
                                    else "No boards",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(filtered, key = { it.slug }) { board ->
                        val isCurrent = board.slug == currentSlug
                        val colorIndex = kotlin.math.abs(board.slug.hashCode()) % BOARD_COLORS.size
                        val boardColor = BOARD_COLORS[colorIndex]

                        Surface(
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onBoardClick(board.slug) },
                                    onLongClick = { onBoardLongPress(board) },
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Color indicator
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(boardColor),
                                )
                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = board.name.ifBlank { board.slug },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (board.name.isNotBlank()) {
                                        Text(
                                            text = board.slug,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                // Task count
                                val c = board.counts
                                if (c != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (c.todo > 0) TaskCountChip(c.todo, STATUS_COLORS["todo"]!!)
                                        if (c.running > 0) TaskCountChip(c.running, STATUS_COLORS["running"]!!)
                                        if (c.blocked > 0) TaskCountChip(c.blocked, STATUS_COLORS["blocked"]!!)
                                    }
                                }

                                // Current indicator
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Current",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // New board button
                Surface(tonalElevation = 4.dp) {
                    Button(
                        onClick = onNewBoard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Board")
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCountChip(count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            "$count",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun KanbanColumn(
    status: String,
    label: String,
    color: Color,
    tasks: List<com.atlas.hermescompanion.data.KanbanTask>,
    onTaskClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.width(180.dp).padding(4.dp),
    ) {
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
