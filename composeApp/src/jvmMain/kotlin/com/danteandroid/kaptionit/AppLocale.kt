package com.danteandroid.kaptionit

import java.util.Locale

/**
 * 应用内界面语言；与 Compose Resources 的 `values` / `values-en` 通过 JVM [Locale] 对齐。
 */
enum class AppLanguage {
    ZH,
    EN,
}

object AppLocale {
    fun apply(lang: AppLanguage) {
        Locale.setDefault(Locale.forLanguageTag(if (lang == AppLanguage.ZH) "zh" else "en"))
    }
}
