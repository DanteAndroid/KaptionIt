package com.danteandroid.transbee.utils

import com.danteandroid.transbee.bundled.BuildConfig
import org.slf4j.LoggerFactory

object TransbeeLog {
    private val log by lazy { LoggerFactory.getLogger("transbee") }

    fun verbose(message: () -> String) {
        if (!BuildConfig.ENABLE_VERBOSE_LOG) return
        log.info(message())
    }

    fun llmHttp(tag: String, body: () -> String) {
        if (!BuildConfig.ENABLE_VERBOSE_LOG) return
        log.info("[$tag]\n${body()}")
    }
}
