package com.example.vanish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.vanish.ui.AppState
import com.example.vanish.ui.VanishApp
import com.example.vanish.ui.theme.VanishTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state = remember { AppState() }
            VanishTheme(dynamicColor = state.dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VanishApp(state = state)
                }
            }
        }
    }
}
