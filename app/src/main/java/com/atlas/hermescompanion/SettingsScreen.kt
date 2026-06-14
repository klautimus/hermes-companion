package com.atlas.hermescompanion

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
import com.atlas.hermescompanion.data.ApiClient
import com.atlas.hermescompanion.data.CompanionHealth
import com.atlas.hermescompanion.data.SessionManager
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: MainViewModel) {
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

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connection", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

        // Server URL
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://android.kevlarscreations.com/android") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        // Username
        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Password
        OutlinedTextField(
            value = passInput,
            onValueChange = { passInput = it },
            label = { Text("Password") },
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
                    try {
                        val c = ApiClient(urlInput, userInput, passInput.ifBlank { SessionManager.DEFAULT_PASSWORD })
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
    }
}
