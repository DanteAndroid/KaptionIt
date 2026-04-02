package com.danteandroid.transbee.whisper

import java.io.File

object WhisperModelPaths {
    fun modelsDirectory(): File {
        val dir = File(System.getProperty("user.home"), ".transbee/models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
