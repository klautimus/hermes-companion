package org.hermes.community.companion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hermes.community.companion.data.DeepLinkConfig
import org.hermes.community.companion.data.SessionManager
import org.hermes.community.companion.data.SessionMigration

class MainActivity : ComponentActivity() {
    private var deepLinkConfig: DeepLinkConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Migrate credentials from plaintext DataStore to EncryptedSharedPreferences
        // This must complete before any SessionManager access, but we launch it
        // concurrently since SessionManager.init is lazy
        lifecycleScope.launch {
            SessionMigration.migrateIfNeeded(applicationContext)
        }

        // Capture deep link from intent
        deepLinkConfig = parseDeepLinkIntent(intent)

        setContent {
            HermesCompanionTheme {
                val vm: MainViewModel = viewModel<MainViewModel>()
                val sessionManager = remember { SessionManager(this@MainActivity) }

                // Check if setup is complete
                val isSetupComplete by sessionManager.setupComplete.collectAsState(initial = false)

                // Allow re-running wizard from settings
                var showWizard by remember { mutableStateOf(!isSetupComplete) }

                // Update showWizard when setupComplete changes
                LaunchedEffect(isSetupComplete) {
                    if (!isSetupComplete) showWizard = true
                }

                // Feed deep link into ViewModel
                LaunchedEffect(deepLinkConfig) {
                    deepLinkConfig?.let { vm.setDeepLinkConfig(it) }
                }

                if (showWizard) {
                    SetupWizardScreen(
                        viewModel = vm,
                        onSetupComplete = {
                            showWizard = false
                        }
                    )
                } else {
                    MainAppContent(
                        viewModel = vm,
                        onShowWizard = { showWizard = true }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle deep link when app is already running
        parseDeepLinkIntent(intent)?.let { deepLinkConfig = it }
    }

    internal fun parseDeepLinkIntent(intent: Intent): DeepLinkConfig? {
        val data = intent.data ?: return null
        if (data.scheme != "hermescompanion") return null
        if (data.host != "configure") return null
        // Paired with daemon setup_wizard.py:generate_qr_code — keep in sync

        return DeepLinkConfig(
            serverUrl = data.getQueryParameter("url") ?: "",
            username = data.getQueryParameter("user") ?: "",
            password = data.getQueryParameter("pass") ?: "",
            token = data.getQueryParameter("token"),
            board = data.getQueryParameter("board") ?: "",
        )
    }
}

@Composable
private fun MainAppContent(viewModel: MainViewModel, onShowWizard: () -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = "Kanban") },
                    label = { Text("Kanban") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> ChatScreen(modifier = Modifier.fillMaxSize().padding(padding), viewModel = viewModel)
            1 -> KanbanScreen(modifier = Modifier.fillMaxSize().padding(padding), viewModel = viewModel)
            2 -> SettingsScreen(modifier = Modifier.fillMaxSize().padding(padding), viewModel = viewModel, onResetSetup = onShowWizard)
        }
    }
}
