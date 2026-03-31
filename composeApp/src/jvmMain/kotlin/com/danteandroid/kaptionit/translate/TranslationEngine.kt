package com.danteandroid.kaptionit.translate

import kotlinx.serialization.Serializable

@Serializable
enum class TranslationEngine {
    APPLE,
    GOOGLE,
    DEEPL,
    OPENAI,
}
