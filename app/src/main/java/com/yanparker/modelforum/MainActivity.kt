package com.yanparker.modelforum

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yanparker.modelforum.ui.nav.AppNavHost
import com.yanparker.modelforum.ui.theme.ModelForumTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showCrashIfAny()
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ModelForumTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavHost()
                }
            }
        }
    }

    private fun showCrashIfAny() {
        val file = File(filesDir, "crash_last.txt")
        if (!file.exists()) return
        val text = file.readText()
        file.delete()
        AlertDialog.Builder(this)
            .setTitle("Приложение аварийно завершилось")
            .setMessage("Ниже причина (отправьте её разработчику):\n\n$text")
            .setPositiveButton("Скопировать") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
            }
            .setNegativeButton("ОК", null)
            .setCancelable(false)
            .show()
    }
}