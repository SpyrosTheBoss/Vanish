package com.spilol2.vanish.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spilol2.vanish.engine.Inpainter
import com.spilol2.vanish.engine.Segmenter
import com.spilol2.vanish.engine.StubInpainter
import com.spilol2.vanish.ui.screens.EditorScreen
import com.spilol2.vanish.ui.screens.HomeScreen
import com.spilol2.vanish.ui.screens.ResultScreen
import com.spilol2.vanish.ui.screens.SettingsScreen

@Composable
fun VanishApp(
    state: AppState,
    segmenter: Segmenter,
    modifier: Modifier = Modifier,
    inpainter: Inpainter = remember { StubInpainter() },
) {
    BackHandler(enabled = state.screen != Screen.Home) {
        state.screen = when (state.screen) {
            Screen.Editor -> Screen.Home
            Screen.Result -> Screen.Editor
            Screen.Settings -> Screen.Home
            Screen.Home -> Screen.Home
        }
    }

    AnimatedContent(
        targetState = state.screen,
        transitionSpec = {
            (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false))
        },
        label = "screen",
        modifier = modifier,
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(
                onPickPhoto = { bmp: Bitmap -> state.openEditor(bmp) },
                onOpenSettings = { state.screen = Screen.Settings },
            )
            Screen.Editor -> EditorScreen(
                state = state,
                inpainter = inpainter,
                segmenter = segmenter,
                onBack = { state.screen = Screen.Home },
            )
            Screen.Result -> ResultScreen(
                state = state,
                onBack = { state.screen = Screen.Editor },
                onDone = { state.screen = Screen.Home },
            )
            Screen.Settings -> SettingsScreen(
                state = state,
                onBack = { state.screen = Screen.Home },
            )
        }
    }
}
