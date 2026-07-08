package com.spilol2.vanish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.spilol2.vanish.engine.OnnxInpainter
import com.spilol2.vanish.engine.Segmenter
import com.spilol2.vanish.ui.AppState
import com.spilol2.vanish.ui.VanishApp
import com.spilol2.vanish.ui.theme.VanishTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state = remember { AppState() }
            val context = LocalContext.current
            val inpainter = remember { OnnxInpainter(context) }
            val segmenter = remember { Segmenter(context) }
            VanishTheme(dynamicColor = state.dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VanishApp(state = state, inpainter = inpainter, segmenter = segmenter)
                }
            }
        }
    }
}
