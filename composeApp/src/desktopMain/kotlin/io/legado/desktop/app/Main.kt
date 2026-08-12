package io.legado.desktop.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Legado Desktop",
    ) {
        App()
    }
}
