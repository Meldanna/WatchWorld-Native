package com.watchworld.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.watchworld.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WatchWorld Native",
        state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
    ) {
        App()
    }
}
