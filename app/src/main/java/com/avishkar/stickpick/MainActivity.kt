package com.avishkar.stickpick

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.avishkar.stickpick.data.local.PackStorage
import com.avishkar.stickpick.ui.navigation.AppNavGraph
import com.avishkar.stickpick.ui.theme.StickPickTheme
import com.avishkar.stickpick.viewmodel.MainViewModel
import com.avishkar.stickpick.whatsapp.PackArchiveHelper
import com.avishkar.stickpick.whatsapp.WhatsAppIntentHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var lastHandledUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsState()

            StickPickTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(navController = navController, vm = vm)
                }
            }
        }
        handleIncomingPackIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingPackIntent(intent)
    }

    private fun handleIncomingPackIntent(intent: Intent?) {
        val uri = intent?.data ?: (intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)) ?: return
        if (uri == lastHandledUri) return
        lastHandledUri = uri

        lifecycleScope.launch {
            val packStorage = PackStorage(this@MainActivity)
            val imported = PackArchiveHelper.importWastickers(this@MainActivity, uri, packStorage)
            if (imported != null) {
                Toast.makeText(this@MainActivity, "Imported \"${imported.name}\"! Adding to WhatsApp...", Toast.LENGTH_SHORT).show()
                WhatsAppIntentHelper.addStickerPackToWhatsApp(this@MainActivity, imported.identifier, imported.name)
            }
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200) {
            if (resultCode == Activity.RESULT_CANCELED) {
                val error = data?.getStringExtra("validation_error")
                if (error != null) {
                    Toast.makeText(this, "WhatsApp: $error", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Sticker pack not added", Toast.LENGTH_SHORT).show()
                }
            } else if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Sticker pack added!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
