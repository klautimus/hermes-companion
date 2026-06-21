package org.hermes.community.companion

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.net.Uri
import java.util.concurrent.TimeUnit

private val STATUS_COLUMNS = listOf("triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done")
private val STATUS_LABELS = mapOf(
    "triage" to "Triage", "todo" to "To Do", "scheduled" to "Scheduled",
    "ready" to "Ready", "running" to "Running",
    "blocked" to "Blocked", "review" to "Review", "done" to "Done",
)
private val STATUS_COLORS = mapOf(
    "triage" to Color(0xFF94E2D5),
    "todo" to Color(0xFFF9E2AF),
    "scheduled" to Color(0xFFB4BEFE),
    "ready" to Color(0xFF89B4FA),
    "running" to Color(0xFFCBA6F7),
    "blocked" to Color(0xFFF38BA8),
    "review" to Color(0xFFFAB387),
    "done" to Color(0xFFA6E3A1),
)

private val PRIORITY_COLORS = mapOf(
    5 to Color(0xFFF38BA8),  // urgent — red
    4 to Color(0xFFFAB387),  // high — orange
    3 to Color(0xFFF9E2AF),  // med — yellow
    2 to Color(0xFF89B4FA),  // low — blue
    1 to Color(0xFF6C7086),  // none — gray
)
private val PRIORITY_LABELS = mapOf(
    5 to "Urgent", 4 to "High", 3 to "Medium", 2 to "Low", 1 to "None",
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
    var commentText by rememberSaveable { mutableStateOf("") }

    // Drawer state
    var drawerOpen by remember { mutableStateOf(false) }
    var boardSearch by remember { mutableStateOf("") }

    // Context menu state
    var contextMenuBoard by remember { mutableStateOf<org.hermes.community.companion.data.KanbanBoard?>(null) }
    var activeBoardForDialog by remember { mutableStateOf<org.hermes.community.companion.data.KanbanBoard?>(null) }

    // Dialogs
    var createDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var archiveDialog by remember { mutableStateOf(false) }
    var newBoardSlug by remember { mutableStateOf("") }
    var newBoardName by remember { mutableStateOf("") }
    var renameBoardName by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    // ── T13: Search + Assignee filter state ──
    var searchQuery by remember { mutableStateOf("") }
    var assigneeFilter by remember { mutableStateOf("All") }
    var showAssigneeFilterDropdown by remember { mutableStateOf(false) }
    val profiles by viewModel.profiles.collectAsState()

    // ── T13: Inline task creation dialog state ──
    var inlineCreateDialog by remember { mutableStateOf(false) }
    var inlineCreateStatus by remember { mutableStateOf("todo") }
    var inlineCreateTitle by remember { mutableStateOf("") }
    var inlineCreateAssignee by remember { mutableStateOf("ops") }
    var inlineCreatePriority by remember { mutableStateOf(3) }
    var inlineCreateError by remember { mutableStateOf<String?>(null) }

    // ── T13: Task card context menu state ──
    var taskContextMenu by remember { mutableStateOf<org.hermes.community.companion.data.KanbanTask?>(null) }
    var showBlockReasonDialog by remember { mutableStateOf(false) }
    var blockReason by remember { mutableStateOf("") }

    // ── T13: Multi-select state ──
    var selectMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkReassignDialog by remember { mutableStateOf(false) }
    var bulkReassignProfile by remember { mutableStateOf("ops") }

    // ── T13: Board stats overlay ──
    var showStats by remember { mutableStateOf(false) }
    val stats by viewModel.stats.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadBoards(); viewModel.loadTasks(); viewModel.loadProfiles(); viewModel.loadStats() }

    // Filtered tasks: apply search + assignee filter
    val filteredTasksByStatus = remember(tasksByStatus, searchQuery, assigneeFilter) {
        tasksByStatus.mapValues { (_, tasks) ->
            tasks.filter { task ->
                val matchesSearch = searchQuery.isBlank() || task.title.contains(searchQuery, ignoreCase = true)
                val matchesAssignee = assigneeFilter == "All" || task.assignee == assigneeFilter
                matchesSearch && matchesAssignee
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Main content
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with board picker + search + filter
            Surface(tonalElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    // Row 1: Board picker + actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Board:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = { drawerOpen = true }) {
                            Text(boardSlug, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        Spacer(modifier = Modifier.weight(1f))

                        // Select mode toggle
                        IconButton(onClick = {
                            selectMode = !selectMode
                            if (!selectMode) selectedTaskIds = emptySet()
                        }) {
                            Icon(
                                if (selectMode) Icons.Filled.Close else Icons.Filled.CheckCircle,
                                if (selectMode) "Exit select" else "Select",
                                tint = if (selectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Stats button
                        IconButton(onClick = { showStats = true }) {
                            Icon(Icons.Filled.BarChart, "Stats")
                        }

                        // Refresh
                        IconButton(onClick = { viewModel.loadTasks(); viewModel.loadStats() }) {
                            Icon(Icons.Filled.Refresh, "Refresh")
                        }
                    }

                    // Row 2: Search + Assignee filter
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Search field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search tasks...", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Filled.Clear, "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )

                        // Assignee filter dropdown
                        Box {
                            OutlinedButton(
                                onClick = { showAssigneeFilterDropdown = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(assigneeFilter, style = MaterialTheme.typography.labelSmall)
                                Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = showAssigneeFilterDropdown,
                                onDismissRequest = { showAssigneeFilterDropdown = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All") },
                                    onClick = { assigneeFilter = "All"; showAssigneeFilterDropdown = false },
                                )
                                profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile) },
                                        onClick = { assigneeFilter = profile; showAssigneeFilterDropdown = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall)
            }

            // ── T13: Bulk action bar ──
            AnimatedVisibility(visible = selectMode && selectedTaskIds.isNotEmpty()) {
                Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${selectedTaskIds.size} selected", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            viewModel.bulkUpdateStatus(selectedTaskIds.toList(), "done")
                            selectedTaskIds = emptySet()
                            selectMode = false
                        }) {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Complete All", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            viewModel.bulkUpdateStatus(selectedTaskIds.toList(), "archived")
                            selectedTaskIds = emptySet()
                            selectMode = false
                        }) {
                            Icon(Icons.Filled.Archive, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Archive All", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { showBulkReassignDialog = true }) {
                            Icon(Icons.Filled.Person, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reassign...", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Kanban columns
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                STATUS_COLUMNS.forEach { status ->
                    KanbanColumn(
                        status = status,
                        label = STATUS_LABELS[status] ?: status,
                        color = STATUS_COLORS[status] ?: MaterialTheme.colorScheme.primary,
                        tasks = filteredTasksByStatus[status] ?: emptyList(),
                        selectMode = selectMode,
                        selectedTaskIds = selectedTaskIds,
                        onTaskClick = { taskId ->
                            if (selectMode) {
                                selectedTaskIds = if (selectedTaskIds.contains(taskId))
                                    selectedTaskIds - taskId else selectedTaskIds + taskId
                            } else {
                                viewModel.loadTask(taskId); detailSheet = true
                            }
                        },
                        onTaskLongPress = { task ->
                            if (!selectMode) taskContextMenu = task
                        },
                        onCheckChanged = { taskId, checked ->
                            selectedTaskIds = if (checked) selectedTaskIds + taskId else selectedTaskIds - taskId
                        },
                        onCreateTask = {
                            inlineCreateStatus = status
                            inlineCreateTitle = ""
                            inlineCreateAssignee = "ops"
                            inlineCreatePriority = 3
                            inlineCreateError = null
                            inlineCreateDialog = true
                        },
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
                            onValueChange = { s: String -> newBoardSlug = s.lowercase().replace(Regex("[^a-z0-9-]"), "-") },
                            label = { Text("Slug") },
                            placeholder = { Text("my-project") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newBoardName,
                            onValueChange = { s: String -> newBoardName = s },
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
                            onValueChange = { s: String -> renameBoardName = s },
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

        // ── T13: Inline Task Creation Dialog ──────────────────
        if (inlineCreateDialog) {
            AlertDialog(
                onDismissRequest = { inlineCreateDialog = false; inlineCreateError = null },
                title = { Text("New Task — ${STATUS_LABELS[inlineCreateStatus] ?: inlineCreateStatus}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        inlineCreateError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(
                            value = inlineCreateTitle,
                            onValueChange = { inlineCreateTitle = it },
                            label = { Text("Title *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Assignee dropdown
                        var assigneeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = assigneeExpanded,
                            onExpandedChange = { assigneeExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = inlineCreateAssignee,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Assignee") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = assigneeExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = assigneeExpanded,
                                onDismissRequest = { assigneeExpanded = false },
                            ) {
                                profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile) },
                                        onClick = { inlineCreateAssignee = profile; assigneeExpanded = false },
                                    )
                                }
                            }
                        }
                        // Priority selector
                        Text("Priority", style = MaterialTheme.typography.labelMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            (1..5).forEach { p ->
                                val pColor = PRIORITY_COLORS[p] ?: Color(0xFF6C7086)
                                FilterChip(
                                    selected = inlineCreatePriority == p,
                                    onClick = { inlineCreatePriority = p },
                                    label = { Text("$p", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = pColor.copy(alpha = 0.3f),
                                        selectedLabelColor = pColor,
                                    ),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val title = inlineCreateTitle.trim()
                            if (title.isBlank()) {
                                inlineCreateError = "Title is required"
                                return@TextButton
                            }
                            viewModel.createTask(
                                title = title,
                                status = inlineCreateStatus,
                                assignee = inlineCreateAssignee,
                                priority = inlineCreatePriority,
                            )
                            inlineCreateDialog = false
                            inlineCreateError = null
                        },
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        inlineCreateDialog = false; inlineCreateError = null
                    }) { Text("Cancel") }
                },
            )
        }

        // ── T13: Task Card Context Menu ───────────────────────
        taskContextMenu?.let { task ->
            DropdownMenu(
                expanded = true,
                onDismissRequest = { taskContextMenu = null },
            ) {
                DropdownMenuItem(
                    text = { Text("Move to Triage") },
                    onClick = {
                        viewModel.updateTaskStatus(task.id, "triage")
                        taskContextMenu = null
                    },
                    leadingIcon = { Icon(Icons.Filled.LowPriority, null) },
                )
                DropdownMenuItem(
                    text = { Text("Move to Ready") },
                    onClick = {
                        viewModel.updateTaskStatus(task.id, "ready")
                        taskContextMenu = null
                    },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                )
                DropdownMenuItem(
                    text = { Text("Move to Done") },
                    onClick = {
                        viewModel.updateTaskStatus(task.id, "done")
                        taskContextMenu = null
                    },
                    leadingIcon = { Icon(Icons.Filled.Check, null) },
                )
                DropdownMenuItem(
                    text = { Text("Block...") },
                    onClick = {
                        taskContextMenu = null
                        blockReason = ""
                        showBlockReasonDialog = true
                    },
                    leadingIcon = { Icon(Icons.Filled.Block, null) },
                )
                DropdownMenuItem(
                    text = { Text("Archive") },
                    onClick = {
                        viewModel.updateTaskStatus(task.id, "archived")
                        taskContextMenu = null
                    },
                    leadingIcon = { Icon(Icons.Filled.Archive, null) },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        viewModel.deleteTask(task.id)
                        taskContextMenu = null
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                )
            }
        }

        // ── T13: Block Reason Dialog ──────────────────────────
        if (showBlockReasonDialog) {
            AlertDialog(
                onDismissRequest = { showBlockReasonDialog = false; blockReason = "" },
                title = { Text("Block Task") },
                text = {
                    OutlinedTextField(
                        value = blockReason,
                        onValueChange = { blockReason = it },
                        label = { Text("Reason") },
                        placeholder = { Text("Why is this blocked?") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        taskContextMenu?.let { task ->
                            viewModel.updateTaskStatus(task.id, "blocked")
                            if (blockReason.isNotBlank()) {
                                viewModel.commentOnTask(task.id, "Blocked: $blockReason")
                            }
                        }
                        showBlockReasonDialog = false
                        blockReason = ""
                    }) { Text("Block") }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockReasonDialog = false; blockReason = "" }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // ── T13: Bulk Reassign Dialog ─────────────────────────
        if (showBulkReassignDialog) {
            AlertDialog(
                onDismissRequest = { showBulkReassignDialog = false },
                title = { Text("Reassign ${selectedTaskIds.size} tasks") },
                text = {
                    Column {
                        Text("Select assignee:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        var reassignExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = reassignExpanded,
                            onExpandedChange = { reassignExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = bulkReassignProfile,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reassignExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = reassignExpanded,
                                onDismissRequest = { reassignExpanded = false },
                            ) {
                                profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile) },
                                        onClick = { bulkReassignProfile = profile; reassignExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.bulkReassign(selectedTaskIds.toList(), bulkReassignProfile)
                        selectedTaskIds = emptySet()
                        selectMode = false
                        showBulkReassignDialog = false
                    }) { Text("Reassign") }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkReassignDialog = false }) { Text("Cancel") }
                },
            )
        }

        // ── T13: Board Stats Overlay ──────────────────────────
        if (showStats) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
                onClick = { showStats = false },
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.85f)
                        .clickable(enabled = false) {},
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Board Stats", style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = { showStats = false }) {
                                Icon(Icons.Filled.Close, "Close")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Total tasks
                        Text(
                            "${stats.total} total tasks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Counts per status
                        STATUS_COLUMNS.forEach { status ->
                            val count = stats.countsByStatus[status] ?: 0
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(12.dp).clip(CircleShape)
                                        .background(STATUS_COLORS[status] ?: Color.Gray),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    STATUS_LABELS[status] ?: status,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Oldest ready task
                        val oldestReady = stats.oldestReadyAgeSeconds
                        if (oldestReady != null && oldestReady > 0) {
                            val ageStr = formatAgeSeconds(oldestReady)
                            Text(
                                "Oldest ready task: $ageStr",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "No ready tasks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Task Detail Bottom Sheet ─────────────────────────────
    selectedTask?.let { task ->
        if (detailSheet) {
            TaskDetailSheet(
                task = task,
                viewModel = viewModel,
                commentText = commentText,
                onCommentTextChange = { commentText = it },
                onDismiss = { detailSheet = false; viewModel.clearSelectedTask() },
            )
        }
    }
}

// ── Task Detail Bottom Sheet ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskDetailSheet(
    task: org.hermes.community.companion.data.TaskShowResponse,
    viewModel: MainViewModel,
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingTitle by remember { mutableStateOf(false) }
    var editingBody by remember { mutableStateOf(false) }
    var editTitleText by remember { mutableStateOf(task.title) }
    var editBodyText by remember { mutableStateOf(task.body ?: "") }
    var showBlockDialog by remember { mutableStateOf(false) }
    var blockReason by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAssignDropdown by remember { mutableStateOf(false) }
    var showAddDependencyDialog by remember { mutableStateOf(false) }
    var dependencyTaskId by remember { mutableStateOf("") }
    val profiles by viewModel.profiles.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // File picker for task attachments
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val name = getFileName(context, it) ?: "file_${System.currentTimeMillis()}"
            readFileBytes(context, it)?.let { bytes ->
                val mime = guessMime(name)
                viewModel.uploadTaskAttachment(task.id, bytes, name, mime)
            }
        }
    }

    // Reset edit fields when task changes
    LaunchedEffect(task.id) {
        editTitleText = task.title
        editBodyText = task.body ?: ""
        editingTitle = false
        editingBody = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 1. Editable Title ──────────────────────────────
            item {
                if (editingTitle) {
                    OutlinedTextField(
                        value = editTitleText,
                        onValueChange = { editTitleText = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                viewModel.updateTaskTitle(task.id, editTitleText)
                                editingTitle = false
                                focusManager.clearFocus()
                            }
                        ),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = {
                                    viewModel.updateTaskTitle(task.id, editTitleText)
                                    editingTitle = false
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Filled.Check, "Save", tint = Color(0xFFA6E3A1))
                                }
                                IconButton(onClick = {
                                    editTitleText = task.title
                                    editingTitle = false
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Filled.Close, "Cancel", tint = Color(0xFFF38BA8))
                                }
                            }
                        },
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { editingTitle = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(task.title, style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Edit, "Edit title", modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Status + Assignee + Priority row ───────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Status badge
                    Surface(
                        color = STATUS_COLORS[task.status]?.copy(alpha = 0.2f)
                            ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(task.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }

                    // 5. Priority badge (click to cycle)
                    val pColor = PRIORITY_COLORS[task.priority] ?: Color(0xFF6C7086)
                    val pLabel = PRIORITY_LABELS[task.priority] ?: "${task.priority}"
                    Surface(
                        color = pColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable {
                            val next = if (task.priority >= 5) 1 else task.priority + 1
                            viewModel.updateTaskPriority(task.id, next)
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(pColor))
                            Text(pLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 4. Assignee dropdown chip
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { showAssignDropdown = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Filled.Person, null, modifier = Modifier.size(14.dp))
                                Text(task.assignee ?: "Unassigned",
                                    style = MaterialTheme.typography.labelSmall)
                                Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(14.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showAssignDropdown,
                            onDismissRequest = { showAssignDropdown = false },
                        ) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile) },
                                    onClick = {
                                        viewModel.assignTask(task.id, profile)
                                        showAssignDropdown = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Unassigned") },
                                onClick = {
                                    viewModel.assignTask(task.id, "")
                                    showAssignDropdown = false
                                },
                            )
                        }
                    }
                }
            }

            // ── Metadata Chips ──────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    task.age?.createdAgeSeconds?.let { ageSec ->
                        AssistChip(
                            onClick = {},
                            label = { Text(formatAgeSeconds(ageSec)) },
                            leadingIcon = { Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                    task.progress?.let { prog ->
                        if (prog.total > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${prog.done}/${prog.total} done") },
                                leadingIcon = { Icon(Icons.Filled.TaskAlt, null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                    task.warnings?.forEach { warning ->
                        AssistChip(
                            onClick = {},
                            label = { Text(warning.take(20)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            leadingIcon = { Icon(Icons.Filled.Warning, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            // ── 2. Editable Body ───────────────────────────────
            item {
                if (editingBody) {
                    OutlinedTextField(
                        value = editBodyText,
                        onValueChange = { editBodyText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        maxLines = 10,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            editBodyText = task.body ?: ""
                            editingBody = false
                        }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.updateTaskBody(task.id, editBodyText)
                            editingBody = false
                        }) { Text("Save") }
                    }
                } else {
                    if (!task.body.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(task.body, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { editingBody = true }) {
                                Text("Edit", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        TextButton(onClick = { editingBody = true }) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add description", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ── 3. Status Action Row ───────────────────────────
            item {
                Text("Actions", style = MaterialTheme.typography.titleSmall)
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatusChip("→ Triage", task.status != "triage") { viewModel.updateTaskStatus(task.id, "triage") }
                    StatusChip("→ To Do", task.status != "todo") { viewModel.updateTaskStatus(task.id, "todo") }
                    StatusChip("→ Scheduled", task.status != "scheduled") { viewModel.updateTaskStatus(task.id, "scheduled") }
                    StatusChip("→ Ready", task.status != "ready") { viewModel.updateTaskStatus(task.id, "ready") }
                    StatusChip("→ Running", task.status != "running") { viewModel.updateTaskStatus(task.id, "running") }
                    StatusChip("Block", task.status != "blocked") { showBlockDialog = true }
                    StatusChip("Unblock", task.status == "blocked") { viewModel.updateTaskStatus(task.id, "ready") }
                    StatusChip("→ Review", task.status != "review") { viewModel.updateTaskStatus(task.id, "review") }
                    StatusChip("Complete", task.status != "done") { viewModel.updateTaskStatus(task.id, "done"); onDismiss() }
                    StatusChip("Archive", task.status != "archived") { viewModel.updateTaskStatus(task.id, "archived"); onDismiss() }
                    StatusChip("Delete", true, isDestructive = true) { showDeleteConfirm = true }
                }
            }

            // ── 10. Result / Latest Summary ────────────────────
            if (!task.latestSummary.isNullOrBlank()) {
                item {
                    Text("Latest Summary", style = MaterialTheme.typography.titleSmall)
                }
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(task.latestSummary, modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── 7. Attachments Section ─────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Attachments (${task.attachments.size})", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Filled.Upload, "Upload")
                    }
                }
            }
            if (task.attachments.isEmpty()) {
                item {
                    Text("No attachments", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(task.attachments) { att ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Attachment, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(att.filename, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sizeStr = when {
                                att.size > 1024 * 1024 -> "${att.size / (1024 * 1024)} MB"
                                att.size > 1024 -> "${att.size / 1024} KB"
                                else -> "${att.size} B"
                            }
                            Text("${sizeStr} • ${att.uploadedBy ?: "unknown"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── 8. Dependencies / Links Section ────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Dependencies", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { showAddDependencyDialog = true }) {
                        Icon(Icons.Filled.Add, "Add dependency")
                    }
                }
            }
            val parents = task.parents
            val children = task.children
            if (parents.isEmpty() && children.isEmpty()) {
                item {
                    Text("No dependencies", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (parents.isNotEmpty()) {
                item {
                    Text("Parents", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(parents) { parent ->
                    Surface(
                        color = STATUS_COLORS[parent.status]?.copy(alpha = 0.15f)
                            ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.loadTask(parent.id)
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(parent.title, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = STATUS_COLORS[parent.status]?.copy(alpha = 0.3f)
                                    ?: MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(parent.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            if (children.isNotEmpty()) {
                item {
                    Text("Children", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(children) { child ->
                    Surface(
                        color = STATUS_COLORS[child.status]?.copy(alpha = 0.15f)
                            ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.loadTask(child.id)
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(child.title, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = STATUS_COLORS[child.status]?.copy(alpha = 0.3f)
                                    ?: MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(child.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ── 9. Activity / Runs History ─────────────────────
            item {
                Text("Activity", style = MaterialTheme.typography.titleSmall)
            }
            if (task.runs.isEmpty() && task.events.isEmpty()) {
                item {
                    Text("No activity yet", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Show runs
            items(task.runs) { run ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(run.profile ?: "unknown", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = when (run.outcome) {
                                    "completed" -> Color(0xFFA6E3A1).copy(alpha = 0.3f)
                                    "crashed", "timed_out" -> Color(0xFFF38BA8).copy(alpha = 0.3f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(run.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            val elapsed = if (run.endedAt > 0 && run.startedAt > 0) {
                                val secs = (run.endedAt - run.startedAt) / 1000
                                if (secs >= 60) "${secs / 60}m ${secs % 60}s" else "${secs}s"
                            } else ""
                            if (elapsed.isNotEmpty()) {
                                Text(elapsed, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!run.summary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(run.summary, style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (!run.error.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(run.error, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            // Show events
            items(task.events) { event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val relTime = formatRelativeTime(event.createdAt)
                    Text(relTime, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val eventLabel = when (event.kind) {
                        "created" -> "Task created"
                        "claimed" -> "Claimed by ${event.profile ?: "worker"}"
                        "status_changed" -> "Status changed${event.profile ?: ""}"
                        "completed" -> "Completed"
                        "blocked" -> "Blocked"
                        "unblocked" -> "Unblocked"
                        else -> event.kind
                    }
                    Text(eventLabel, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── 6. Comments with author + timestamp ───────────
            item {
                Text("Comments (${task.comments.size})", style = MaterialTheme.typography.titleSmall)
            }
            if (task.comments.isEmpty()) {
                item {
                    Text("No comments yet", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(task.comments) { c ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.author, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(formatRelativeTime(c.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(c.body, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── 11. Add comment ───────────────────────────────
            item {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = onCommentTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add comment...") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            viewModel.commentOnTask(task.id, commentText)
                            onCommentTextChange("")
                        }, enabled = commentText.isNotBlank()) {
                            Icon(Icons.Filled.Send, "Send")
                        }
                    },
                )
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ── Block Dialog ──────────────────────────────────────────
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false; blockReason = "" },
            title = { Text("Block Task") },
            text = {
                OutlinedTextField(
                    value = blockReason,
                    onValueChange = { blockReason = it },
                    label = { Text("Reason") },
                    placeholder = { Text("Why is this blocked?") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTaskStatus(task.id, "blocked")
                    if (blockReason.isNotBlank()) {
                        viewModel.commentOnTask(task.id, "Blocked: $blockReason")
                    }
                    showBlockDialog = false
                    blockReason = ""
                }) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false; blockReason = "" }) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Delete Confirmation Dialog ────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Task") },
            text = { Text("Permanently delete this task? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(task.id)
                    showDeleteConfirm = false
                    onDismiss()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // ── Add Dependency Dialog ─────────────────────────────────
    if (showAddDependencyDialog) {
        AlertDialog(
            onDismissRequest = { showAddDependencyDialog = false; dependencyTaskId = "" },
            title = { Text("Add Dependency") },
            text = {
                OutlinedTextField(
                    value = dependencyTaskId,
                    onValueChange = { dependencyTaskId = it },
                    label = { Text("Parent Task ID") },
                    placeholder = { Text("t_...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dependencyTaskId.isNotBlank()) {
                        viewModel.addDependency(dependencyTaskId.trim(), task.id)
                    }
                    showAddDependencyDialog = false
                    dependencyTaskId = ""
                }, enabled = dependencyTaskId.isNotBlank()) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDependencyDialog = false
                    dependencyTaskId = ""
                }) { Text("Cancel") }
            },
        )
    }
}

// ── Status Action Chip ────────────────────────────────────────

@Composable
private fun StatusChip(label: String, enabled: Boolean, isDestructive: Boolean = false, onClick: () -> Unit) {
    if (isDestructive) {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Icon(Icons.Filled.Delete, null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        FilterChip(
            selected = false,
            onClick = onClick,
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            enabled = enabled,
        )
    }
}

// ── Relative time formatter ───────────────────────────────────

private fun formatRelativeTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    if (diff < 0) return "just now"
    val secs = TimeUnit.MILLISECONDS.toSeconds(diff)
    if (secs < 60) return "${secs}s ago"
    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
    if (mins < 60) return "${mins}m ago"
    val hrs = TimeUnit.MILLISECONDS.toHours(diff)
    if (hrs < 24) return "${hrs}h ago"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return "${days}d ago"
}

private fun formatAgeSeconds(secs: Long): String {
    if (secs < 60) return "${secs}s"
    val mins = secs / 60
    if (mins < 60) return "${mins}m"
    val hrs = mins / 60
    if (hrs < 24) return "${hrs}h"
    val days = hrs / 24
    return "${days}d"
}

// ── Board Drawer ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardDrawer(
    boards: List<org.hermes.community.companion.data.KanbanBoard>,
    currentSlug: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBoardClick: (String) -> Unit,
    onBoardLongPress: (org.hermes.community.companion.data.KanbanBoard) -> Unit,
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

// ── T13: Enhanced Kanban Column with inline create + card enhancements ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KanbanColumn(
    status: String,
    label: String,
    color: Color,
    tasks: List<org.hermes.community.companion.data.KanbanTask>,
    onTaskClick: (String) -> Unit,
    onTaskLongPress: (org.hermes.community.companion.data.KanbanTask) -> Unit,
    selectMode: Boolean = false,
    selectedTaskIds: Set<String> = emptySet(),
    onCheckChanged: (String, Boolean) -> Unit = { _, _ -> },
    onCreateTask: () -> Unit = {},
) {
    Column(
        modifier = Modifier.width(180.dp).padding(4.dp),
    ) {
        // Column header with + FAB
        Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = color.copy(alpha = 0.4f), shape = CircleShape) {
                        Text("${tasks.size}", modifier = Modifier.padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Inline create button
                    IconButton(onClick = onCreateTask, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Add, "Add task", modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    selectMode = selectMode,
                    isSelected = selectedTaskIds.contains(task.id),
                    onClick = { onTaskClick(task.id) },
                    onLongPress = { onTaskLongPress(task) },
                    onCheckChanged = { checked -> onCheckChanged(task.id, checked) },
                )
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

// ── T13: Enhanced Task Card ───────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: org.hermes.community.companion.data.KanbanTask,
    selectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Row: checkbox (select mode) + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onCheckChanged,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Priority dot
                val pColor = PRIORITY_COLORS[task.priority] ?: Color(0xFF6C7086)
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(pColor))

                // Assignee
                if (task.assignee != null) {
                    Text(
                        task.assignee,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Comment count badge
                if (task.commentCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "💬 ${task.commentCount}",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                // Dependency count badge
                val depCount = task.linkCounts?.let { it.parents + it.children } ?: task.linkCount
                if (depCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "🔗 $depCount",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            // Progress pill for parent tasks
            val progress = task.progress
            if (progress != null && progress.total > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LinearProgressIndicator(
                        progress = progress.done.toFloat() / progress.total.toFloat(),
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = if (progress.done == progress.total) Color(0xFFA6E3A1) else Color(0xFF89B4FA),
                    )
                    Text(
                        "${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Age + warnings row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Age
                val ageSeconds = task.age?.createdAgeSeconds
                if (ageSeconds != null && ageSeconds > 0) {
                    Text(
                        formatAgeSeconds(ageSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Warning indicator
                if (!task.warnings.isNullOrEmpty()) {
                    Icon(
                        Icons.Filled.Warning,
                        "Warnings",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFFAB387),
                    )
                }
            }
        }
    }
}

/** Read a file's bytes from a content URI. */
private fun readFileBytes(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        android.util.Log.e("KanbanScreen", "Failed to read file", e)
        null
    }
}

/** Extract a display name from a content URI. */
private fun getFileName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
        }
    } catch (e: Exception) {
        null
    }
}

/** Guess MIME type from filename extension. */
private fun guessMime(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        else -> "application/octet-stream"
    }
}
