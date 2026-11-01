package com.shiyi.memory

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.shiyi.memory.ui.*

class MainActivity : FragmentActivity() {
    private val screenReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context,
                intent: android.content.Intent,
            ) {
                model.lock()
            }
        }
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            screenReceiver,
            android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MemoryApp(model) }
        if (savedInstanceState == null) model.acceptShare(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.acceptShare(intent)
    }

    override fun onResume() {
        super.onResume()
        model.foreground()
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) model.background()
    }
}
