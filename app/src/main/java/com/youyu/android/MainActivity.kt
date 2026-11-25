package com.youyu.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.youyu.android.data.EncryptedLedgerRepository
import com.youyu.android.ui.LedgerViewModel
import com.youyu.android.ui.YouyuApp
import java.io.File

class MainActivity : ComponentActivity() {
    private val model: LedgerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LedgerViewModel(EncryptedLedgerRepository(File(noBackupFilesDir, "vault"))) as T
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { YouyuApp(model) }
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) model.lockForBackground()
    }
}
