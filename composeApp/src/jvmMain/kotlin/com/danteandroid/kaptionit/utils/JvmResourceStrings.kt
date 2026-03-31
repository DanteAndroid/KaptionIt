package com.danteandroid.kaptionit.utils

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * 在非 Composable（如 ViewModel）中按当前 JVM 区域设置加载字符串资源。
 * 桌面端 [org.jetbrains.compose.resources.getSystemResourceEnvironment] 与 [java.util.Locale.getDefault] 一致，
 * 应用内切换语言时需同步调用 [com.danteandroid.kaptionit.AppLocale.apply]。
 */
object JvmResourceStrings {
    fun text(res: StringResource): String = runBlocking {
        getString(getSystemResourceEnvironment(), res)
    }

    fun text(res: StringResource, vararg formatArgs: Any): String = runBlocking {
        getString(getSystemResourceEnvironment(), res, *formatArgs)
    }
}