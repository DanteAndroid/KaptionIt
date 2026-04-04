package com.danteandroid.transbee

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Component
import java.awt.Window
import javax.swing.SwingUtilities
import javax.swing.Timer

private interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(
        hwnd: HWND,
        dwAttribute: Int,
        pvAttribute: Pointer,
        cbAttribute: Int,
    ): Int
}

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
private const val DWMWA_BORDER_COLOR = 34
private const val DWMWA_CAPTION_COLOR = 35
private const val DWMWA_SYSTEMBACKDROP_TYPE = 38
private const val DWMSBT_NONE = 1

private const val AppDarkBackgroundColorref = 0x001E0C08

private fun isWindows(): Boolean =
    System.getProperty("os.name")?.lowercase()?.contains("windows") == true

private fun componentPeer(c: Component): Any? = runCatching {
    val f = Component::class.java.getDeclaredField("peer")
    f.isAccessible = true
    f.get(c)
}.getOrNull()

private fun awtWindowHwnd(w: Window): Long? {
    val peer = componentPeer(w) ?: return null
    var clazz: Class<*> = peer.javaClass
    while (true) {
        for (name in listOf("getHWnd", "getHwnd")) {
            val m = runCatching { clazz.getDeclaredMethod(name) }.getOrNull() ?: continue
            if (m.parameterCount != 0) continue
            m.isAccessible = true
            val v = runCatching { m.invoke(peer) }.getOrNull() ?: continue
            val n = when (v) {
                is Pointer -> Pointer.nativeValue(v)
                is Number -> v.toLong()
                else -> null
            } ?: continue
            if (n != 0L) return n
        }
        clazz = clazz.superclass ?: break
    }
    return null
}

private fun hwndForDwm(peerHwnd: Long): HWND {
    val base = HWND(Pointer.createConstant(peerHwnd))
    val root = User32.INSTANCE.GetAncestor(base, WinUser.GA_ROOT) ?: return base
    return if (Pointer.nativeValue(root.pointer) != 0L) root else base
}

private fun applyDwmDarkChrome(hwnd: HWND, dwm: Dwmapi) {
    val attr = Memory(4)
    attr.setInt(0, 1)
    dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, attr, 4)
    runCatching {
        attr.setInt(0, DWMSBT_NONE)
        dwm.DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, attr, 4)
    }
    runCatching {
        attr.setInt(0, AppDarkBackgroundColorref)
        dwm.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, attr, 4)
        dwm.DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, attr, 4)
    }
}

private fun tryApplyWindowsDarkChrome(window: Window): Boolean {
    val peerHwnd = awtWindowHwnd(window) ?: return false
    runCatching {
        val dwm = Native.load("dwmapi", Dwmapi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        applyDwmDarkChrome(hwndForDwm(peerHwnd), dwm)
    }
    return true
}

fun scheduleApplyWindowsTitleBarDarkMode(window: Window) {
    if (!isWindows()) return
    SwingUtilities.invokeLater {
        if (tryApplyWindowsDarkChrome(window)) return@invokeLater
        var n = 0
        val timer = Timer(50) { e ->
            n++
            if (tryApplyWindowsDarkChrome(window) || n >= 40) (e.source as Timer).stop()
        }
        timer.isRepeats = true
        timer.start()
    }
}
