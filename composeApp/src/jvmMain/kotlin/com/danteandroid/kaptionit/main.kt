package com.danteandroid.kaptionit

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.danteandroid.kaptionit.native.BundledNativeTools
import com.danteandroid.kaptionit.screen.App
import com.danteandroid.kaptionit.utils.JvmResourceStrings
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.app_title
import kaptionit.composeapp.generated.resources.kaptionit_app_icon
import org.jetbrains.compose.resources.painterResource
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import java.util.Locale
import java.util.prefs.Preferences
import javax.imageio.ImageIO

private const val WhisperitIconClasspathResource =
    "composeResources/kaptionit.composeapp.generated.resources/drawable/kaptionit_app_icon.png"

private fun applyNativeAppIconFromClasspath() {
    if (GraphicsEnvironment.isHeadless()) return
    val cl = Thread.currentThread().contextClassLoader ?: return
    val image = cl.getResourceAsStream(WhisperitIconClasspathResource)?.use {
        ImageIO.read(it)
    } ?: return
    runCatching {
        if (Taskbar.isTaskbarSupported()) {
            val tb = Taskbar.getTaskbar()
            if (tb.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                tb.setIconImage(image)
            }
        }
    }
    runCatching {
        val appClass = Class.forName("com.apple.eawt.Application")
        val app = appClass.getMethod("getApplication").invoke(null)
        appClass.getMethod("setDockIconImage", java.awt.Image::class.java).invoke(app, image)
    }
}

fun main() {
    Locale.setDefault(Locale.forLanguageTag("zh"))
    BundledNativeTools.ensureComposeResourcesDirFromDiscovery()
    applyNativeAppIconFromClasspath()
    application {
        val prefs = Preferences.userRoot().node("com.danteandroid.kaptionit/window")
        val savedWidth = prefs.getDouble("widthDp", 1024.0).dp
        val savedHeight = prefs.getDouble("heightDp", 940.0).dp
        val hasSavedPosition = prefs.getBoolean("hasPosition", false)
        val savedX = prefs.getDouble("xDp", 0.0).dp
        val savedY = prefs.getDouble("yDp", 0.0).dp
        val savedPlacement = prefs.get("placement", WindowPlacement.Floating.name)

        val windowState = rememberWindowState(
            width = savedWidth,
            height = savedHeight,
            position = if (hasSavedPosition) {
                WindowPosition.Absolute(savedX, savedY)
            } else {
                WindowPosition.PlatformDefault
            },
        ).apply {
            placement = if (savedPlacement == WindowPlacement.Maximized.name) {
                WindowPlacement.Maximized
            } else {
                WindowPlacement.Floating
            }
        }

        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
                .collect { (size, position, placement) ->
                    prefs.put("placement", placement.name)
                    if (size.width.value > 0f && size.height.value > 0f) {
                        prefs.putDouble("widthDp", size.width.value.toDouble())
                        prefs.putDouble("heightDp", size.height.value.toDouble())
                    }
                    if (position is WindowPosition.Absolute) {
                        prefs.putBoolean("hasPosition", true)
                        prefs.putDouble("xDp", position.x.value.toDouble())
                        prefs.putDouble("yDp", position.y.value.toDouble())
                    }
                }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = JvmResourceStrings.text(Res.string.app_title),
            state = windowState,
            icon = painterResource(Res.drawable.kaptionit_app_icon),
        ) {
            App()
        }
    }
}