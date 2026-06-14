package com.atlas.hermescompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun Composer(
    onSendText: (String) -> Unit,
    onSendAttachment: (String, ByteArray, String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = {},
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Pair<ByteArray, Uri>?>(null) }

    // Gallery / photo picker
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { compressImage(context, it)?.let { bytes -> pendingImage = bytes to it } }
    }

    // Camera capture
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraUri?.let { uri ->
            compressImage(context, uri)?.let { bytes -> pendingImage = bytes to uri }
        }
    }

    // File picker (any type)
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { readFileBytes(context, it)?.let { bytes ->
            pendingImage = bytes to it
        }}
    }

    var showPickerMenu by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier.padding(8.dp),
    ) {
        Column {
            // Pending image preview
            pendingImage?.let { (bytes, _) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = bytes,
                        contentDescription = "Image preview",
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Text("Image attached", modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { pendingImage = null }) {
                        Icon(Icons.Filled.Close, "Remove attachment")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Attachment button
                Box {
                    IconButton(onClick = { showPickerMenu = true }) {
                        Icon(Icons.Filled.AttachFile, "Attach")
                    }
                    DropdownMenu(
                        expanded = showPickerMenu,
                        onDismissRequest = { showPickerMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Camera") },
                            onClick = {
                                showPickerMenu = false
                                val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file
                                )
                                cameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Gallery") },
                            onClick = {
                                showPickerMenu = false
                                imagePicker.launch("image/*")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("File") },
                            onClick = {
                                showPickerMenu = false
                                filePicker.launch("*/*")
                            },
                        )
                    }
                }

                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    singleLine = false,
                    maxLines = 4,
                )
                // Clear button — visible only when there's text or a pending image
                if (input.isNotBlank() || pendingImage != null) {
                    IconButton(
                        onClick = {
                            input = ""
                            pendingImage = null
                            onClear()
                        },
                    ) {
                        Icon(Icons.Filled.Close, "Clear")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val text = input.trim()
                        val image = pendingImage
                        if (text.isNotBlank() || image != null) {
                            if (enabled) {
                                if (image != null) {
                                    onSendAttachment(text, image.first, "image/jpeg")
                                } else {
                                    onSendText(text)
                                }
                                input = ""
                                pendingImage = null
                            }
                        }
                    },
                    enabled = (input.isNotBlank() || pendingImage != null) && enabled,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}

/** Compress image to max 1024px longest edge, JPEG quality 80. */
private fun compressImage(context: Context, uri: Uri, maxDim: Int = 1024, quality: Int = 80): ByteArray? {
    return try {
        // Decode bounds first
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        // Calculate sample size
        options.inSampleSize = 1
        val maxEdge = maxOf(options.outWidth, options.outHeight)
        while (maxEdge / options.inSampleSize > maxDim * 2) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false

        // Decode
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        // Resize if still larger than maxDim
        val w = bitmap.width
        val h = bitmap.height
        val resized = if (kotlin.math.max(w, h) > maxDim) {
            val ratio = maxDim.toFloat() / kotlin.math.max(w, h)
            Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
        } else bitmap

        // Compress to JPEG
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val result = out.toByteArray()
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
        result
    } catch (e: Exception) {
        android.util.Log.e("Composer", "Image compression failed", e)
        null
    }
}

/** Read a file's bytes from a content URI. */
private fun readFileBytes(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        android.util.Log.e("Composer", "Failed to read file", e)
        null
    }
}
