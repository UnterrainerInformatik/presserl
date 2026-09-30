package info.unterrainer.presserl.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import info.unterrainer.presserl.admin.ui.connect.AndroidAppHost

class MainActivity : ComponentActivity() {

    private lateinit var host: AndroidAppHost

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        host = AndroidAppHost(this, debug = BuildConfig.DEBUG)
        host.start(intent)
        setContent { host.Content() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        host.handleIntent(intent)
    }
}
