package com.shiyi.memory

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.shiyi.memory.ui.*

class MainActivity : FragmentActivity() {
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MemoryApp(model) }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) model.unlocked.value = false
    }
}
