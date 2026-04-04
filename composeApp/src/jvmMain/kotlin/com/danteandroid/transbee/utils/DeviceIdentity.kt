package com.danteandroid.transbee.utils

import java.security.SecureRandom
import java.util.prefs.Preferences

object DeviceIdentity {

    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val prefs: Preferences =
        Preferences.userRoot().node("com.danteandroid.transbee/device")

    /** 持久化本机标识，最多 6 位（数字与大小写字母），尽量稳定。 */
    fun getStableDeviceId(): String {
        var raw = prefs.get("stable_id", null)?.trim().orEmpty()
        if (raw.length > 6) {
            raw = raw.take(6)
        }
        if (raw.length == 6 && raw.all { it in ALPHABET }) {
            return raw
        }
        val rnd = SecureRandom()
        val id = buildString(6) {
            repeat(6) {
                append(ALPHABET[rnd.nextInt(ALPHABET.length)])
            }
        }
        prefs.put("stable_id", id)
        return id
    }
}
