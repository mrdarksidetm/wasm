package com.mrdartsidetm.wasm

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrdartsidetm.wasm.data.ChatDatabase
import com.mrdartsidetm.wasm.data.UserPreferencesRepository
import com.mrdartsidetm.wasm.ui.ChatScreen
import com.mrdartsidetm.wasm.ui.ChatViewModel
import com.mrdartsidetm.wasm.ui.HomeScreen
import com.mrdartsidetm.wasm.ui.PlatformChooserScreen
import com.mrdartsidetm.wasm.ui.SettingsScreen
import com.mrdartsidetm.wasm.ui.instagram.InstagramScreen
import com.mrdartsidetm.wasm.ui.instagram.InstagramViewModel
import com.mrdartsidetm.wasm.ui.theme.WasmTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WasmTheme {
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

                // Selected Tab: 0 = Home, 1 = Messages, 2 = Settings
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }

                // Inside Messages: null = Platform Chooser, "whatsapp" = WhatsApp, "instagram" = Instagram
                var activeMessagesPlatform by rememberSaveable { mutableStateOf<String?>(null) }

                val waConversations by whatsappViewModel.conversations.collectAsStateWithLifecycle()
                val igConversations by instagramViewModel.conversations.collectAsStateWithLifecycle()
                val igAccount by instagramViewModel.account.collectAsStateWithLifecycle()
                val waSelectedConversationId by whatsappViewModel.selectedConversationId.collectAsStateWithLifecycle()
                val igSelectedConversationId by instagramViewModel.selectedConversationId.collectAsStateWithLifecycle()
                val igIsViewingDetails by instagramViewModel.isViewingConversationDetails.collectAsStateWithLifecycle()
                val igExpandedMediaList by instagramViewModel.activeExpandedMediaList.collectAsStateWithLifecycle()

                // File picker for WhatsApp (.txt or .zip)
                val whatsappPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let { selectedUri ->
                            val fileName = getFileName(applicationContext, selectedUri)
                            val isZip = isZipUri(applicationContext, selectedUri)
                            if (isZip) {
                                whatsappViewModel.importZipUri(contentResolver, selectedUri, fileName)
                            } else {
                                whatsappViewModel.importChatUri(contentResolver, selectedUri, fileName)
                            }
                            selectedTab = 1
                            activeMessagesPlatform = "whatsapp"
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
                            selectedTab = 1
                            activeMessagesPlatform = "instagram"
                        }
                    }
                )

                // System BackPress Handler: App ONLY exits when on the Home page (selectedTab == 0)
                when (selectedTab) {
                    2 -> {
                        // On Settings -> pressing back returns to Home
                        BackHandler {
                            selectedTab = 0
                        }
                    }
                    1 -> {
                        // On Messages tab
                        when (activeMessagesPlatform) {
                            "whatsapp" -> {
                                if (waSelectedConversationId != null) {
                                    // In individual WhatsApp chat -> close chat and return to WhatsApp chat list
                                    BackHandler {
                                        whatsappViewModel.closeConversation()
                                    }
                                } else {
                                    // On WhatsApp chat list -> return to Platform Chooser
                                    BackHandler {
                                        activeMessagesPlatform = null
                                    }
                                }
                            }
                            "instagram" -> {
                                if (igExpandedMediaList != null) {
                                    // In expanded media viewer -> close viewer
                                    BackHandler {
                                        instagramViewModel.closeExpandedMedia()
                                    }
                                } else if (igIsViewingDetails) {
                                    // In conversation details -> close details
                                    BackHandler {
                                        instagramViewModel.closeConversationDetails()
                                    }
                                } else if (igSelectedConversationId != null) {
                                    // In DM thread -> close thread
                                    BackHandler {
                                        instagramViewModel.closeConversation()
                                    }
                                } else if (igAccount != null && igAccount!!.isDived) {
                                    // In conversation list -> return to Instagram landing page
                                    BackHandler {
                                        instagramViewModel.backToLanding()
                                    }
                                } else {
                                    // On Instagram landing/empty state -> return to Platform Chooser
                                    BackHandler {
                                        activeMessagesPlatform = null
                                    }
                                }
                            }
                            null -> {
                                // On Platform Chooser -> return to Home page
                                BackHandler {
                                    selectedTab = 0
                                }
                            }
                        }
                    }
                    0 -> {
                        // On Home page: No BackHandler enabled -> system exits the app!
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                        contentDescription = "Home"
                                    )
                                },
                                label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = {
                                    if (selectedTab == 1 && activeMessagesPlatform != null) {
                                        // Tapping Messages while already in a platform returns to Platform Chooser
                                        activeMessagesPlatform = null
                                    } else {
                                        selectedTab = 1
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 1) Icons.Filled.Forum else Icons.Outlined.Forum,
                                        contentDescription = "Messages"
                                    )
                                },
                                label = { Text("Messages") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = { Text("Settings") }
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
                            0 -> HomeScreen(
                                whatsappViewModel = whatsappViewModel,
                                instagramViewModel = instagramViewModel,
                                onNavigateToMessages = {
                                    selectedTab = 1
                                    activeMessagesPlatform = null
                                },
                                onOpenWhatsApp = {
                                    selectedTab = 1
                                    activeMessagesPlatform = "whatsapp"
                                },
                                onOpenInstagram = {
                                    selectedTab = 1
                                    activeMessagesPlatform = "instagram"
                                },
                                onImportWhatsApp = {
                                    whatsappPickerLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                onImportInstagram = {
                                    instagramPickerLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            )
                            1 -> {
                                when (activeMessagesPlatform) {
                                    null -> PlatformChooserScreen(
                                        whatsappChatCount = waConversations.size,
                                        instagramConversationCount = igConversations.size,
                                        onSelectWhatsApp = { activeMessagesPlatform = "whatsapp" },
                                        onSelectInstagram = { activeMessagesPlatform = "instagram" },
                                        onBackToHome = { selectedTab = 0 }
                                    )
                                    "whatsapp" -> ChatScreen(
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
                                        },
                                        onBackToPlatformChooser = { activeMessagesPlatform = null }
                                    )
                                    "instagram" -> InstagramScreen(
                                        viewModel = instagramViewModel,
                                        onImportClick = {
                                            instagramPickerLauncher.launch(
                                                arrayOf(
                                                    "application/zip",
                                                    "application/x-zip-compressed",
                                                    "application/octet-stream"
                                                )
                                            )
                                        },
                                        onBackToPlatformChooser = { activeMessagesPlatform = null }
                                    )
                                }
                            }
                            2 -> SettingsScreen(
                                whatsappViewModel = whatsappViewModel,
                                instagramViewModel = instagramViewModel,
                                onBackToHome = { selectedTab = 0 }
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

