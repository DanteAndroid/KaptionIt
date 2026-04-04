package com.danteandroid.transbee

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Component
import java.awt.Window

private interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(
        hwnd: HWND,
        dwAttribute: Int,
        pvAttribute: Pointer,
        cbAttribute: Int,
    ): Int
}

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

private fun awtWindowHwnd(w: Window): Long? {
    val peer = runCatching {
        val f = Component::class.java.getDeclaredField("peer")
        f.isAccessible = true
        f.get(w)
    }.getOrNull() ?: return null
    var c: Class<*> = peer.javaClass
    while (c != Any::class.java) {
        for (m in c.declaredMethods + c.methods) {
            if (m.name != "getHWnd" || m.parameterCount != 0) continue
            m.isAccessible = true
            val v = runCatching { m.invoke(peer) }.getOrNull() ?: continue
            val asLong = when (v) {
                is Long -> v
                is Int -> v.toLong()
                is Number -> v.toLong()
                else -> null
            } ?: continue
            return asLong
        }
        c = c.superclass ?: break
    }
    return null
}

fun applyWindowsImmersiveDarkTitleBarIfNeeded(window: Window) {
    if (System.getProperty("os.name")?.lowercase()?.contains("windows") != true) return
    runCatching {
        val dwm = Native.load("dwmapi", Dwmapi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        val hwndLong = awtWindowHwnd(window) ?: return
        val hwnd = HWND(Pointer.createConstant(hwndLong))
        val attr = Memory(4)
        attr.setInt(0, 1)
        dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, attr, 4)
    }
}
