package com.mrdartsidetm.wasm

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val db = remember { ChatDatabase.getDatabase(applicationContext) }
                val prefs = remember { UserPreferencesRepository(applicationContext) }
                
                // Manual ViewModel Factory to inject dependencies
                val viewModel: ChatViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ChatViewModel(db.chatDao(), prefs) as T
                        }
                    }
                )

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let {
                            contentResolver.openInputStream(it)?.use { stream ->
                                val text = stream.bufferedReader().readText()
                                viewModel.importChatFile(text)
                            }
                        }
                    }
                )

                ChatScreen(
                    viewModel = viewModel,
                    onImportClick = {
                        filePickerLauncher.launch(arrayOf("text/plain"))
                    }
                )
            }
        }
    }
}
