package com.danteandroid.kaptionit.whisper

import java.io.File

object WhisperModelPaths {
    fun modelsDirectory(): File {
        val dir = File(System.getProperty("user.home"), ".kaptionit/models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
