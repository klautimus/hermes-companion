package org.hermes.community.companion

import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.hermes.community.companion.data.ApiClient
import org.hermes.community.companion.data.checkServerHealth
import org.hermes.community.companion.data.SessionManager
import org.hermes.community.companion.data.StorageMode
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WizardConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val token: String? = null,  // NEW: setup token from QR code
    val board: String = "default"
)

@Composable
fun SetupWizardScreen(
    viewModel: MainViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }

    var currentScreen by remember { mutableIntStateOf(0) }
    var config by remember { mutableStateOf(WizardConfig()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var boards by remember { mutableStateOf<List<String>>(emptyList()) }
    var createBoardName by remember { mutableStateOf("") }
    var showCreateBoardDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    // Handle deep link data
    val deepLinkData = viewModel.deepLinkConfig.value
    LaunchedEffect(deepLinkData) {
        if (deepLinkData != null) {
            config = WizardConfig(
                serverUrl = deepLinkData.serverUrl,
                username = deepLinkData.username,
                password = deepLinkData.password,
                token = deepLinkData.token,
                board = deepLinkData.board
            )
            if (deepLinkData.serverUrl.isNotBlank() &&
                deepLinkData.username.isNotBlank() &&
                deepLinkData.password.isNotBlank() &&
                deepLinkData.board.isNotBlank()) {
                currentScreen = 2
            }
            viewModel.clearDeepLinkConfig()
        }
    }

    // Auto-redeem setup token when present (from QR scan or deep link)
    LaunchedEffect(config.token) {
        val token = config.token
        if (token != null && config.serverUrl.isNotBlank()) {
            isLoading = true
            val result = viewModel.redeemSetupToken(config.serverUrl, token)
            if (result.isSuccess) {
                sessionManager.setSetupComplete()
                onSetupComplete()
            } else {
                // Show error — token redemption failed, fall back to manual entry
                testResult = "Token redeem failed: ${result.exceptionOrNull()?.message}"
                testOk = false
            }
            isLoading = false
        }
    }

    if (showQrScanner) {
        QrScannerScreen(
            onQrCodeScanned = { uriString ->
                showQrScanner = false
                parseQrUri(uriString)?.let { parsed ->
                    config = WizardConfig(
                        serverUrl = parsed.serverUrl,
                        username = parsed.username,
                        password = parsed.password,
                        token = parsed.token,
                        board = parsed.board
                    )
                    // If QR provides all fields, skip to board selection
                    if (parsed.serverUrl.isNotBlank() &&
                        parsed.username.isNotBlank() &&
                        parsed.password.isNotBlank()) {
                        if (parsed.board.isNotBlank()) {
                            currentScreen = 2
                        } else {
                            currentScreen = 1
                        }
                    }
                }
            },
            onDismiss = { showQrScanner = false }
        )
        return
    }

    // Insecure storage consent dialog — must be acknowledged before setup completes
    val acknowledgedInsecureStorage = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Reset acknowledgment when wizard starts/re-enters
        acknowledgedInsecureStorage.value = false
    }
    val storageMode = remember { sessionManager.getStorageMode() }
    if (storageMode is StorageMode.Plaintext && !acknowledgedInsecureStorage.value) {
        AlertDialog(
            onDismissRequest = { /* do nothing — must explicitly choose */ },
            title = { Text("⚠️ Insecure storage detected") },
            text = {
                Text("""
                    Your device's Android Keystore is unavailable. Credentials will be stored in plaintext.

                    This is a security risk. Anyone with access to your device can read your password.

                    Recommended actions:
                    1. Use a real device instead of an emulator
                    2. Or accept the risk and continue (NOT RECOMMENDED)

                    Reason: ${storageMode.reason}
                """.trimIndent())
            },
            confirmButton = {
                TextButton(onClick = { acknowledgedInsecureStorage.value = true }) {
                    Text("I understand, continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { /* exit setup — user can restart */ }) {
                    Text("Cancel")
                }
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = { (currentScreen + 1) / 4f },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            when (currentScreen) {
                0 -> "Step 1: Server Connection"
                1 -> "Step 2: Credentials"
                2 -> "Step 3: Board Selection"
                3 -> "Step 3: Create Account"
                else -> ""
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        when (currentScreen) {
            0 -> ServerConnectionScreen(
                config = config,
                onConfigChange = { config = it },
                onTestResult = { result, ok -> testResult = result; testOk = ok },
                isLoading = isLoading,
                onLoadingChange = { isLoading = it },
                onQrScan = { showQrScanner = true }
            )
            1 -> CredentialsScreen(
                config = config,
                onConfigChange = { config = it },
                onCreateAccount = { currentScreen = 3 }
            )
            2 -> BoardSelectionScreen(
                config = config,
                onConfigChange = { config = it },
                boards = boards,
                viewModel = viewModel,
                createBoardName = createBoardName,
                onCreateBoardNameChange = { createBoardName = it },
                showCreateBoardDialog = showCreateBoardDialog,
                onShowCreateBoardDialog = { showCreateBoardDialog = it }
            )
            3 -> CreateAccountScreen(
                serverUrl = config.serverUrl,
                onAccountCreated = { username, password ->
                    config = config.copy(username = username, password = password)
                    currentScreen = 2
                },
                onBack = { currentScreen = 1 }
            )
        }

        // Test result display (for screen 0)
        if (currentScreen == 0 && testResult != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (testOk) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    testResult!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentScreen > 0) {
                OutlinedButton(onClick = { currentScreen-- }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            when (currentScreen) {
                0 -> {
                    Button(
                        onClick = { currentScreen++ },
                        enabled = testOk && !isLoading
                    ) {
                        Text("Next")
                    }
                }
                1 -> {
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val c = ApiClient(config.serverUrl, config.username, config.password)
                                    val raw = c.get("/api/kanban/boards")
                                    val json = Json { ignoreUnknownKeys = true }
                                    val boardsList = json.parseToJsonElement(raw).jsonArray
                                    boards = boardsList.map {
                                        it.jsonObject["slug"]?.jsonPrimitive?.content ?: ""
                                    }
                                    currentScreen++
                                } catch (e: Exception) {
                                    currentScreen++
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = config.username.isNotBlank() && config.password.isNotBlank() && !isLoading
                    ) {
                        Text("Next")
                    }
                }
                2 -> {
                    Button(
                        onClick = {
                            scope.launch {
                                sessionManager.setBaseUrl(config.serverUrl)
                                sessionManager.setUsername(config.username)
                                sessionManager.setPassword(config.password)
                                sessionManager.setBoard(config.board)
                                sessionManager.setSetupComplete()
                                onSetupComplete()
                            }
                        },
                        enabled = config.board.isNotBlank()
                    ) {
                        Text("Finish")
                    }
                }
            }
        }
    }
}

/**
 * Parse a hermescompanion://configure URI into a WizardConfig.
 * Format: hermescompanion://configure?url=https://...&user=kevin&pass=xxx&token=yyy&board=default
 * Paired with daemon setup_wizard.py:generate_qr_code — keep in sync
 */
private fun parseQrUri(uriString: String): WizardConfig? {
    return try {
        val uri = Uri.parse(uriString) ?: return null
        if (uri.scheme != "hermescompanion") return null
        WizardConfig(
            serverUrl = uri.getQueryParameter("url") ?: "",
            username = uri.getQueryParameter("user") ?: "",
            password = uri.getQueryParameter("pass") ?: "",
            token = uri.getQueryParameter("token"),
            board = uri.getQueryParameter("board") ?: "default",
        )
    } catch (e: Exception) {
        null
    }
}

@Composable
fun CreateAccountScreen(
    serverUrl: String,
    onAccountCreated: (username: String, password: String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Create Your Account", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This server has no users yet. Create the first admin account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username (min 3 chars)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min 8 chars)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    try {
                        if (password != confirmPassword) {
                            error = "Passwords do not match"
                            return@launch
                        }
                        org.hermes.community.companion.data.registerUser(serverUrl, username, password)
                        onAccountCreated(username, password)
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = username.length >= 3 && password.length >= 8 &&
                      password == confirmPassword && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Create Account")
            }
        }

        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun ServerConnectionScreen(
    config: WizardConfig,
    onConfigChange: (WizardConfig) -> Unit,
    onTestResult: (String?, Boolean) -> Unit,
    isLoading: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    onQrScan: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = config.serverUrl,
            onValueChange = { onConfigChange(config.copy(serverUrl = it)) },
            label = { Text("Server URL") },
            placeholder = { Text("https://your-server.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            isError = config.serverUrl.isNotBlank() && !Patterns.WEB_URL.matcher(config.serverUrl).matches()
        )

        if (config.serverUrl.isNotBlank() && !Patterns.WEB_URL.matcher(config.serverUrl).matches()) {
            Text(
                "Please enter a valid URL (https://...)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Scan QR Code button
        OutlinedButton(
            onClick = onQrScan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan QR Code")
        }

        // Test connection button
        Button(
            onClick = {
                scope.launch {
                    onLoadingChange(true)
                    onTestResult("Testing...", false)
                    try {
                        val health = checkServerHealth(config.serverUrl)
                        onTestResult(
                            "Connected ✓ (hermes_api=${if (health.hermesReachable) "up" else "down"})",
                            health.hermesReachable
                        )
                    } catch (e: Exception) {
                        onTestResult("Failed: ${e.message}", false)
                    } finally {
                        onLoadingChange(false)
                    }
                }
            },
            enabled = config.serverUrl.isNotBlank() &&
                      Patterns.WEB_URL.matcher(config.serverUrl).matches() &&
                      !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Test Connection")
        }
    }
}

@Composable
private fun CredentialsScreen(
    config: WizardConfig,
    onConfigChange: (WizardConfig) -> Unit,
    onCreateAccount: (() -> Unit)? = null
) {
    var passVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = config.username,
            onValueChange = { onConfigChange(config.copy(username = it)) },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = config.password,
            onValueChange = { onConfigChange(config.copy(password = it)) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (passVisible) "Hide" else "Show"
                    )
                }
            }
        )

        if (onCreateAccount != null) {
            HorizontalDivider()
            Text(
                "No account yet?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Account on This Server")
            }
        }
    }
}

@Composable
private fun BoardSelectionScreen(
    config: WizardConfig,
    onConfigChange: (WizardConfig) -> Unit,
    boards: List<String>,
    viewModel: MainViewModel,
    createBoardName: String,
    onCreateBoardNameChange: (String) -> Unit,
    showCreateBoardDialog: Boolean,
    onShowCreateBoardDialog: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (boards.isEmpty()) {
            OutlinedTextField(
                value = config.board,
                onValueChange = { onConfigChange(config.copy(board = it)) },
                label = { Text("Board slug") },
                placeholder = { Text("default") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "Enter a board slug manually, or create a new board below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("Select a board:", style = MaterialTheme.typography.bodyMedium)
            boards.forEach { board ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = config.board == board,
                        onClick = { onConfigChange(config.copy(board = board)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(board)
                }
            }
        }

        HorizontalDivider()

        // Create new board
        OutlinedButton(
            onClick = { onShowCreateBoardDialog(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create new board")
        }
    }

    if (showCreateBoardDialog) {
        AlertDialog(
            onDismissRequest = { onShowCreateBoardDialog(false) },
            title = { Text("Create New Board") },
            text = {
                OutlinedTextField(
                    value = createBoardName,
                    onValueChange = onCreateBoardNameChange,
                    label = { Text("Board name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val slug = createBoardName.lowercase().replace(" ", "-")
                            viewModel.createBoard(slug, createBoardName)
                            onConfigChange(config.copy(board = slug))
                            onShowCreateBoardDialog(false)
                            onCreateBoardNameChange("")
                        }
                    },
                    enabled = createBoardName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowCreateBoardDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
