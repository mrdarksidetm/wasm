package com.mrdartsidetm.wasm

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrdartsidetm.wasm.data.ChatDatabase
import com.mrdartsidetm.wasm.data.UserPreferencesRepository
import com.mrdartsidetm.wasm.ui.ChatScreen
import com.mrdartsidetm.wasm.ui.ChatViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val db = remember { ChatDatabase.getDatabase(applicationContext) }
                val prefs = remember { UserPreferencesRepository(applicationContext) }
                val mediaDir = remember { File(applicationContext.filesDir, "media") }
                
                // Manual ViewModel Factory to inject dependencies
                val viewModel: ChatViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ChatViewModel(db.chatDao(), prefs, mediaDir) as T
                        }
                    }
                )

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let {
                            val isZip = isZipUri(applicationContext, it)
                            if (isZip) {
                                contentResolver.openInputStream(it)?.use { stream ->
                                    viewModel.importZipFile(stream)
                                }
                            } else {
                                contentResolver.openInputStream(it)?.use { stream ->
                                    val text = stream.bufferedReader().readText()
                                    viewModel.importChatFile(text)
                                }
                            }
                        }
                    }
                )

                ChatScreen(
                    viewModel = viewModel,
                    onImportClick = {
                        // Support both ZIP archives and plain text files
                        filePickerLauncher.launch(
                            arrayOf(
                                "text/plain",
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

    private fun isZipUri(context: Context, uri: Uri): Boolean {
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
        val mimeType = context.contentResolver.getType(uri)
        return mimeType == "application/zip" || 
               mimeType == "application/x-zip-compressed" || 
               mimeType == "application/octet-stream" ||
               name?.endsWith(".zip", ignoreCase = true) == true
    }
}
