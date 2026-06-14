package com.atlas.hermescompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HermesCompanionTheme {
                val vm: MainViewModel = viewModel<MainViewModel>()
                var selectedTab by remember { mutableIntStateOf(0) }

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
                ) { _ ->
                    when (selectedTab) {
                        0 -> ChatScreen(modifier = Modifier.fillMaxSize(), viewModel = vm)
                        1 -> KanbanScreen(modifier = Modifier.fillMaxSize(), viewModel = vm)
                        2 -> SettingsScreen(modifier = Modifier.fillMaxSize(), viewModel = vm)
                    }
                }
            }
        }
    }
}
