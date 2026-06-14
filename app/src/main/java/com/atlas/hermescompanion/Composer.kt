package com.atlas.hermescompanion

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Composer(
    onSend: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }

    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier.padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { /* clear not implemented in VM yet */ }) {
                Icon(Icons.Filled.DeleteOutline, "Clear")
            }
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                singleLine = false,
                maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    if (input.isNotBlank() && enabled) {
                        onSend(input.trim())
                        input = ""
                    }
                },
                enabled = input.isNotBlank() && enabled,
            ) {
                Icon(Icons.Filled.Send, "Send")
            }
        }
    }
}
