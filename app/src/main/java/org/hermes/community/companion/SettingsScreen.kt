package org.hermes.community.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import org.hermes.community.companion.data.ApiClient
import org.hermes.community.companion.data.CompanionHealth
import org.hermes.community.companion.data.SessionManager
import org.hermes.community.companion.data.StorageMode
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: MainViewModel, onResetSetup: () -> Unit = {}) {
    val baseUrl by viewModel.baseUrl.collectAsState()
    val username by viewModel.username.collectAsState()
    val boardSlug by viewModel.boardSlug.collectAsState()
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf(baseUrl) }
    var userInput by remember { mutableStateOf(username) }
    var boardInput by remember { mutableStateOf(boardSlug) }
    var passInput by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }

    // Sync from viewModel when values change
    LaunchedEffect(baseUrl) { urlInput = baseUrl }
    LaunchedEffect(username) { userInput = username }
    LaunchedEffect(boardSlug) { boardInput = boardSlug }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Storage mode security banner
        val context = LocalContext.current
        val sessionManager = remember { SessionManager(context) }
        val storageMode = sessionManager.getStorageMode()
        when (storageMode) {
            is StorageMode.Encrypted -> { /* silent — no banner needed */ }
            is StorageMode.Plaintext -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ Credentials storage unavailable", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Encrypted storage is unavailable on this device. Credentials cannot be saved. Please ensure your device has a working Android Keystore.")
                        Text("Reason: ${storageMode.reason}")
                    }
                }
            }
            is StorageMode.Unavailable -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ Credentials storage unavailable", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Cannot store credentials: ${storageMode.reason}")
                    }
                }
            }
        }

        Text("Connection", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

        // Server URL
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://your-server.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        if (baseUrl.isBlank()) {
            Text(
                "Not configured — enter your server URL above",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Username
        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("Username") },
            placeholder = { Text("Enter username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (username.isBlank()) {
            Text(
                "Not configured — enter your username above",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Password
        OutlinedTextField(
            value = passInput,
            onValueChange = { passInput = it },
            label = { Text("Password") },
            placeholder = { Text("Enter password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (passVisible) "Hide" else "Show",
                    )
                }
            },
        )

        // Test connection button
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    testResult = "Testing..."
                    testOk = false
                    if (passInput.isBlank()) {
                        testResult = "Please enter a password"
                        testOk = false
                        return@launch
                    }
                    try {
                        val c = ApiClient(urlInput, userInput, passInput)
                        val raw = c.get("/health")
                        val health = Json { ignoreUnknownKeys = true }.decodeFromString<CompanionHealth>(raw)
                        testResult = "Connected ✓ (hermes_api=${if (health.hermesReachable) "up" else "down"})"
                        testOk = health.hermesReachable
                    } catch (e: Exception) {
                        testResult = "Failed: ${e.message}"
                    }
                }
            }) {
                Text("Test Connection")
            }

            // Save button
            Button(onClick = {
                viewModel.saveSettings(urlInput, userInput, passInput)
                viewModel.setBoard(boardInput)
                passInput = ""
                testResult = "Saved ✓"
                testOk = true
            }) {
                Text("Save")
            }
        }

        // Test result
        testResult?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (testOk) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }

        Divider()

        Text("Kanban", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)

        // Board
        OutlinedTextField(
            value = boardInput,
            onValueChange = { boardInput = it },
            label = { Text("Board slug") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text(
            "Change the active kanban board. Use the Kanban tab to browse available boards.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Divider()

        Text("Security", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)

        // 2FA section
        var show2faSetupDialog by remember { mutableStateOf(false) }
        var show2faDisableDialog by remember { mutableStateOf(false) }

        OutlinedButton(
            onClick = { show2faSetupDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Email 2FA")
        }

        OutlinedButton(
            onClick = { show2faDisableDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disable 2FA")
        }

        // 2FA Setup dialog
        if (show2faSetupDialog) {
            var setupCode by remember { mutableStateOf("") }
            var setupError by remember { mutableStateOf<String?>(null) }
            var setupLoading by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { show2faSetupDialog = false },
                title = { Text("Enable Email 2FA") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This will enable email-based two-factor authentication. You'll receive a code via email each time you log in.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = setupCode,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) setupCode = it },
                            label = { Text("Enter current 6-digit code to confirm") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = setupError != null,
                            supportingText = if (setupError != null) {
                                { Text(setupError!!, color = MaterialTheme.colorScheme.error) }
                            } else null
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                setupLoading = true
                                setupError = null
                                try {
                                    val c = ApiClient(urlInput, userInput, passInput)
                                    c.setup2fa()
                                    show2faSetupDialog = false
                                    testResult = "2FA enabled"
                                    testOk = true
                                } catch (e: Exception) {
                                    setupError = e.message
                                } finally {
                                    setupLoading = false
                                }
                            }
                        },
                        enabled = setupCode.length == 6 && !setupLoading
                    ) { Text("Enable") }
                },
                dismissButton = {
                    TextButton(onClick = { show2faSetupDialog = false }) { Text("Cancel") }
                }
            )
        }

        // 2FA Disable dialog
        if (show2faDisableDialog) {
            var disableCode by remember { mutableStateOf("") }
            var disableError by remember { mutableStateOf<String?>(null) }
            var disableLoading by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { show2faDisableDialog = false },
                title = { Text("Disable Email 2FA") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter your current 2FA code to disable email authentication.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = disableCode,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) disableCode = it },
                            label = { Text("6-digit code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = disableError != null,
                            supportingText = if (disableError != null) {
                                { Text(disableError!!, color = MaterialTheme.colorScheme.error) }
                            } else null
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                disableLoading = true
                                disableError = null
                                try {
                                    val c = ApiClient(urlInput, userInput, passInput)
                                    c.disable2fa(disableCode)
                                    show2faDisableDialog = false
                                    testResult = "2FA disabled"
                                    testOk = true
                                } catch (e: Exception) {
                                    disableError = e.message
                                } finally {
                                    disableLoading = false
                                }
                            }
                        },
                        enabled = disableCode.length == 6 && !disableLoading
                    ) { Text("Disable") }
                },
                dismissButton = {
                    TextButton(onClick = { show2faDisableDialog = false }) { Text("Cancel") }
                }
            )
        }

        Text(
            "Enable email-based two-factor authentication for additional security.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Divider()

        Text("Setup", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)

        OutlinedButton(
            onClick = onResetSetup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Setup Wizard")
        }

        Text(
            "Re-run the initial setup wizard to change server, credentials, or board.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
