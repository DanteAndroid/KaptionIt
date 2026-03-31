package com.danteandroid.kaptionit.translate

object TargetLanguageMapper {

    fun toGoogleTargetCode(userInput: String): String {
        val t = userInput.trim()
        if (t.isEmpty()) return "zh-CN"
        if (t.matches(Regex("[a-z]{2}(-[A-Za-z]{2,4})?"))) return t
        return when (t) {
            "简体中文", "中文", "简体" -> "zh-CN"
            "繁体中文", "繁体", "正体中文" -> "zh-TW"
            "English", "英语", "英文" -> "en"
            "日本語", "日语", "日文" -> "ja"
            "한국어", "韩语", "韩文" -> "ko"
            "Français", "法语" -> "fr"
            "Deutsch", "德语" -> "de"
            "Español", "西班牙语" -> "es"
            "Русский", "俄语" -> "ru"
            else -> t
        }
    }

    fun toDeepLTargetCode(userInput: String): String {
        val t = userInput.trim().uppercase()
        if (t.isEmpty()) return "ZH"
        if (t.matches(Regex("[A-Z]{2}(-[A-Z]{2,4})?"))) return t
        return when (userInput.trim()) {
            "简体中文", "中文", "简体" -> "ZH"
            "繁体中文", "繁体", "正体中文" -> "ZH"
            "English", "英语", "英文" -> "EN"
            "日本語", "日语", "日文" -> "JA"
            "한국어", "韩语", "韩文" -> "KO"
            "Français", "法语" -> "FR"
            "Deutsch", "德语" -> "DE"
            "Español", "西班牙语" -> "ES"
            "Русский", "俄语" -> "RU"
            else -> if (userInput.trim().length == 2) userInput.trim().uppercase() else "ZH"
        }
    }

    fun toAppleLocale(userInput: String, forTarget: Boolean): String {
        val t = userInput.trim()
        if (t.isEmpty()) return if (forTarget) "zh-Hans" else "en"
        if (t.matches(Regex("[a-z]{2}(-[A-Za-z]{2,4})?"))) return t
        return when (t) {
            "简体中文", "中文", "简体" -> "zh-Hans"
            "繁体中文", "繁体", "正体中文" -> "zh-Hant"
            "English", "英语", "英文" -> "en"
            "日本語", "日语", "日文" -> "ja"
            "한국어", "韩语", "韩文" -> "ko"
            "Français", "法语" -> "fr"
            "Deutsch", "德语" -> "de"
            "Español", "西班牙语" -> "es"
            "Русский", "俄语" -> "ru"
            else -> t
        }
    }

    fun whisperLanguageToAppleSource(iso639: String?): String {
        val raw = iso639?.trim()?.lowercase().orEmpty()
        if (raw.isEmpty()) return "en"
        return when (raw) {
            "zh", "cmn", "yue" -> "zh-Hans"
            "en" -> "en"
            "ja" -> "ja"
            "ko" -> "ko"
            "fr" -> "fr"
            "de" -> "de"
            "es" -> "es"
            "ru" -> "ru"
            else -> if (raw.length == 2) raw else "en"
        }
    }

    fun subtitleTargetSuffix(userInput: String): String {
        val code = toGoogleTargetCode(userInput).lowercase()
        return when {
            code.startsWith("zh") -> "_zh"
            code.startsWith("en") -> "_en"
            code.startsWith("ja") -> "_ja"
            code.startsWith("ko") -> "_ko"
            code.startsWith("fr") -> "_fr"
            code.startsWith("de") -> "_de"
            code.startsWith("es") -> "_es"
            code.startsWith("ru") -> "_ru"
            else -> "_" + code.replace('-', '_')
        }
    }
}
