package com.mrdartsidetm.wasm

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrdartsidetm.wasm.data.ChatDatabase
import com.mrdartsidetm.wasm.data.UserPreferencesRepository
import com.mrdartsidetm.wasm.ui.ChatScreen
import com.mrdartsidetm.wasm.ui.ChatViewModel
import com.mrdartsidetm.wasm.ui.instagram.InstagramScreen
import com.mrdartsidetm.wasm.ui.instagram.InstagramViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val db = remember { ChatDatabase.getDatabase(applicationContext) }
                val prefs = remember { UserPreferencesRepository(applicationContext) }
                val whatsappMediaDir = remember { File(applicationContext.filesDir, "media") }
                val instagramBaseDir = remember { File(applicationContext.filesDir, "instagram_data") }

                // WhatsApp ViewModel
                val whatsappViewModel: ChatViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ChatViewModel(db.chatDao(), prefs, whatsappMediaDir) as T
                        }
                    }
                )

                // Instagram ViewModel
                val instagramViewModel: InstagramViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return InstagramViewModel(db.instagramDao(), instagramBaseDir) as T
                        }
                    }
                )

                var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: WhatsApp, 1: Instagram

                // File picker for WhatsApp (.txt or .zip)
                val whatsappPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let { selectedUri ->
                            val isZip = isZipUri(applicationContext, selectedUri)
                            if (isZip) {
                                whatsappViewModel.importZipUri(contentResolver, selectedUri)
                            } else {
                                whatsappViewModel.importChatUri(contentResolver, selectedUri)
                            }
                        }
                    }
                )

                // File picker for Instagram (.zip export)
                val instagramPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let { selectedUri ->
                            val fileName = getFileName(applicationContext, selectedUri)
                            instagramViewModel.importZipUri(contentResolver, selectedUri, fileName)
                        }
                    }
                )

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Chat, contentDescription = "WhatsApp") },
                                label = { Text("WhatsApp") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Instagram") },
                                label = { Text("Instagram") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> ChatScreen(
                                viewModel = whatsappViewModel,
                                onImportClick = {
                                    whatsappPickerLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            )
                            1 -> InstagramScreen(
                                viewModel = instagramViewModel,
                                onImportClick = {
                                    instagramPickerLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isZipUri(context: Context, uri: Uri): Boolean {
        val name = getFileName(context, uri)
        val mimeType = context.contentResolver.getType(uri)
        return mimeType == "application/zip" ||
                mimeType == "application/x-zip-compressed" ||
                mimeType == "application/octet-stream" ||
                name?.endsWith(".zip", ignoreCase = true) == true
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }
}
