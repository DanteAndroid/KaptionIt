import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import transbee.tasks.GenerateBundledNativeDistributionPathTask
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

private val whisperCppTag = "v1.8.4"
private val whisperWinZipUrl =
    "https://github.com/ggml-org/whisper.cpp/releases/download/$whisperCppTag/whisper-bin-x64.zip"

private fun osName(): String = System.getProperty("os.name").orEmpty().lowercase()

private fun isMacOs(): Boolean = osName().contains("mac")

private fun isWindowsOs(): Boolean = osName().contains("windows")

private fun isUnixLikeDesktop(): Boolean =
    isMacOs() || (osName().contains("linux") && !osName().contains("android"))

private fun findCmakeExecutable(): File {
    val candidates = listOf(
        "/opt/homebrew/bin/cmake",
        "/usr/local/bin/cmake",
        "/usr/bin/cmake",
    )
    for (p in candidates) {
        val f = File(p)
        if (f.isFile && f.canExecute()) return f
    }
    val path = System.getenv("PATH") ?: ""
    for (dir in path.split(File.pathSeparator)) {
        if (dir.isEmpty()) continue
        val f = File(dir, "cmake")
        if (f.isFile && f.canExecute()) return f
    }
    throw GradleException(
        "未找到 cmake，无法自动编译 whisper-cli。请先安装 CMake（macOS：brew install cmake），" +
            "并确保 Xcode 命令行工具可用（xcode-select --install），然后重新执行构建。",
    )
}

private fun runExternalCommand(vararg command: String) {
    val exit = ProcessBuilder(*command)
        .redirectErrorStream(true)
        .inheritIO()
        .start()
        .waitFor()
    if (exit != 0) {
        throw GradleException("命令失败 (exit=$exit): ${command.joinToString(" ")}")
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/transbee/kotlin"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.slf4j.api)
                implementation(libs.slf4j.simple)
                implementation("net.java.dev.jna:jna-platform:5.14.0")
                implementation("commons-codec:commons-codec:1.17.1")
            }
        }
    }
}

/** 版本唯一来源：仓库根目录 VERSION（semver x.y.z） */
val appVersion: String = run {
    val vf = rootProject.layout.projectDirectory.file("VERSION").asFile
    if (!vf.isFile) {
        error("缺少根目录 VERSION 文件（单行版本号，如 1.2.0）")
    }
    vf.readText().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        ?: error("根目录 VERSION 为空")
}
val computedPackageVersion = appVersion

/** 与 Configuration Cache 兼容：仅在配置阶段计算，勿在任务执行时读 project。 */
val enableVerboseLogForBuild: Boolean = run {
    val prop = project.findProperty("transbee.verboseLog")?.toString()
    when {
        prop.equals("true", ignoreCase = true) -> true
        prop.equals("false", ignoreCase = true) -> false
        else -> {
            val names = gradle.startParameter.taskNames.map { it.lowercase() }
            val releaseLike = names.any { tn ->
                tn.contains("release") && (
                    tn.contains("package") || tn.contains("distributable") ||
                        tn.contains("notarize")
                )
            }
            !releaseLike
        }
    }
}

val generateBundledNativeDistributionPath =
    tasks.register<GenerateBundledNativeDistributionPathTask>("generateBundledNativeDistributionPath") {
        group = "build"
        nativeDistributionPath.set(
            layout.projectDirectory.dir("native-distribution/common").asFile.canonicalFile.absolutePath,
        )
        outputFile.set(
            layout.buildDirectory.file(
                "generated/transbee/kotlin/com/danteandroid/transbee/bundled/BundledNativeDistributionPath.kt",
            ),
        )
    }

val generateBuildConfig = tasks.register("generateBuildConfig") {
    val outDir =
        layout.buildDirectory.dir("generated/transbee/kotlin/com/danteandroid/transbee/bundled")
    inputs.property("transbee.enableVerboseLog", enableVerboseLogForBuild)
    inputs.property("transbee.appVersion", computedPackageVersion)
    inputs.file(rootProject.layout.projectDirectory.file("VERSION"))
    outputs.dir(outDir)
    val versionText = computedPackageVersion
    val enableVerboseLog = enableVerboseLogForBuild
    doLast {
        val f = outDir.get().file("BuildConfig.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("""
            package com.danteandroid.transbee.bundled
            
            object BuildConfig {
                const val APP_VERSION = "$versionText"
                const val ENABLE_VERBOSE_LOG = $enableVerboseLog
            }
        """.trimIndent())
    }
}


tasks.named("compileKotlinJvm") {
    dependsOn(generateBundledNativeDistributionPath, generateBuildConfig)
}

val buildAppleTranslateMac = tasks.register<Exec>("buildAppleTranslateMac") {
    group = "build"
    description = "在 macOS 上编译 AppleTranslate（swift build -c release）"
    notCompatibleWithConfigurationCache("调用 swift build")
    val appleTranslateDir = rootProject.layout.projectDirectory.dir("native/AppleTranslate").asFile
    workingDir = appleTranslateDir
    commandLine("swift", "build", "-c", "release")
    onlyIf {
        isMacOs() && File(appleTranslateDir, "Package.swift").isFile
    }
}

val syncAppleTranslateBundle = tasks.register("syncAppleTranslateBundle") {
    group = "build"
    description = "将 AppleTranslate 复制到 composeApp/native-distribution/common（仅 macOS）"
    dependsOn(buildAppleTranslateMac)
    notCompatibleWithConfigurationCache("复制 AppleTranslate 可执行文件")
    val rootDir = rootProject.layout.projectDirectory.asFile
    val outDir = layout.projectDirectory.dir("native-distribution/common").asFile
    onlyIf { isMacOs() }
    doLast {
        val candidates = listOf(
            File(rootDir, "native/AppleTranslate/.build/arm64-apple-macosx/release/AppleTranslate"),
            File(rootDir, "native/AppleTranslate/.build/x86_64-apple-macosx/release/AppleTranslate"),
            File(rootDir, "native/AppleTranslate/.build/release/AppleTranslate"),
        )
        val fromFile = candidates.firstOrNull { it.isFile }
            ?: throw GradleException(
                "未找到 AppleTranslate 可执行文件（已尝试 swift build）。请确认 native/AppleTranslate 可正常编译，" +
                    "且已安装 Xcode 命令行工具（xcode-select --install）。",
            )
        outDir.mkdirs()
        val dest = File(outDir, "AppleTranslate")
        Files.copy(fromFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        dest.setExecutable(true, false)
    }
}

val repoRootPath = rootProject.layout.projectDirectory.asFile.absolutePath
val composeNativeDistPath = layout.projectDirectory.dir("native-distribution").asFile.absolutePath

/** 仅当目标不存在时下载；已存在则跳过，无需单独 fetch 任务。 */
private fun ensureBundledFfmpegMac(repoRoot: String) {
    val dest = File(repoRoot, "native/bundled/ffmpeg")
    if (dest.isFile) return
    if (!isMacOs()) return
    val zipFile = File(layout.buildDirectory.dir("tmp/bundled").get().asFile, "ffmpeg.zip")
    zipFile.parentFile.mkdirs()
    dest.parentFile.mkdirs()
    URI.create("https://evermeet.cx/ffmpeg/getrelease/zip").toURL().openStream().use { inp ->
        Files.copy(inp, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    ZipFile(zipFile).use { zip ->
        val entry = zip.getEntry("ffmpeg")
            ?: throw GradleException("FFmpeg 压缩包内缺少 ffmpeg 文件")
        zip.getInputStream(entry).use { stream ->
            Files.copy(stream, dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    zipFile.delete()
    dest.setExecutable(true, false)
    println("已下载 FFmpeg → ${dest.absolutePath}")
}

private fun ensureBundledFfmpegWin(repoRoot: String) {
    val exe = File(repoRoot, "native/bundled/ffmpeg.exe")
    if (exe.isFile) return
    if (!isWindowsOs()) return
    val zipFile = File(layout.buildDirectory.dir("tmp/bundled").get().asFile, "ffmpeg-win.zip")
    zipFile.parentFile.mkdirs()
    exe.parentFile.mkdirs()
    URI.create("https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip").toURL().openStream().use { inp ->
        Files.copy(inp, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    ZipFile(zipFile).use { zip ->
        var found = false
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (e.name.endsWith("bin/ffmpeg.exe")) {
                zip.getInputStream(e).use { stream ->
                    Files.copy(stream, exe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                found = true
                break
            }
        }
        if (!found) {
            throw GradleException("FFmpeg Windows 压缩包内缺少 bin/ffmpeg.exe 文件")
        }
    }
    zipFile.delete()
    exe.setExecutable(true, false)
    println("已下载 Windows FFmpeg → ${exe.absolutePath}")
}

private fun ensureBundledWhisperWin(repoRoot: String) {
    val exe = File(repoRoot, "native/bundled/whisper-win/whisper-cli.exe")
    if (exe.isFile) return
    if (!isWindowsOs()) return
    val outDir = File(repoRoot, "native/bundled/whisper-win")
    outDir.mkdirs()
    val zipFile = File(layout.buildDirectory.dir("tmp/bundled").get().asFile, "whisper-win.zip")
    zipFile.parentFile.mkdirs()
    URI.create(whisperWinZipUrl).toURL().openStream().use { inp ->
        Files.copy(inp, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    ZipFile(zipFile).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            val rel = e.name.removePrefix("Release/").removePrefix("release/")
            if (rel.isEmpty() || rel.endsWith("/")) continue
            val fileDest = File(outDir, rel)
            fileDest.parentFile?.mkdirs()
            zip.getInputStream(e).use { stream ->
                Files.copy(stream, fileDest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
    zipFile.delete()
    if (!exe.isFile) {
        throw GradleException("Whisper Windows 压缩包内缺少 whisper-cli.exe")
    }
    println("已解压 Whisper Windows 二进制 → ${outDir.absolutePath}")
}

val syncBundledFfmpeg = tasks.register("syncBundledFfmpeg") {
    group = "build"
    notCompatibleWithConfigurationCache("ensures FFmpeg then copies into native-distribution")
    doLast {
        val outDir = File(composeNativeDistPath, "common")
        outDir.mkdirs()
        if (isWindowsOs()) {
            ensureBundledFfmpegWin(repoRootPath)
            val src = File(repoRootPath, "native/bundled/ffmpeg.exe")
            if (!src.isFile) return@doLast
            val out = File(outDir, "ffmpeg.exe")
            src.copyTo(out, overwrite = true)
            out.setExecutable(true, false)
        } else {
            ensureBundledFfmpegMac(repoRootPath)
            val src = File(repoRootPath, "native/bundled/ffmpeg")
            if (!src.isFile) return@doLast
            val out = File(outDir, "ffmpeg")
            src.copyTo(out, overwrite = true)
            out.setExecutable(true, false)
        }
    }
}

val buildBundledWhisperCliUnix = tasks.register("buildBundledWhisperCliUnix") {
    group = "build"
    notCompatibleWithConfigurationCache("builds whisper-cli from whisper.cpp via CMake")
    onlyIf {
        isUnixLikeDesktop() &&
            !File(repoRootPath, "native/bundled/whisper-cli").isFile
    }
    doLast {
        val cmake = findCmakeExecutable().absolutePath
        val srcDir = File(repoRootPath, "native/bundled/.whisper-src")
        val buildDir = File(repoRootPath, "native/bundled/.whisper-build")
        val destBin = File(repoRootPath, "native/bundled/whisper-cli")
        if (!srcDir.isDirectory) {
            runExternalCommand(
                "git",
                "clone",
                "--depth",
                "1",
                "--branch",
                whisperCppTag,
                "https://github.com/ggml-org/whisper.cpp.git",
                srcDir.absolutePath,
            )
        }
        buildDir.mkdirs()
        runExternalCommand(
            cmake,
            "-S",
            srcDir.absolutePath,
            "-B",
            buildDir.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
        )
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        runExternalCommand(
            cmake,
            "--build",
            buildDir.absolutePath,
            "--target",
            "whisper-cli",
            "--parallel",
            cores.toString(),
        )
        val built = File(buildDir, "bin/whisper-cli")
        if (!built.isFile) {
            throw GradleException("CMake 未生成 bin/whisper-cli，请确认已安装 Xcode 命令行工具（macOS）或 CMake 与 gcc（Linux）。")
        }
        Files.copy(built.toPath(), destBin.toPath(), StandardCopyOption.REPLACE_EXISTING)
        destBin.setExecutable(true, false)
        println("已编译 whisper-cli → ${destBin.absolutePath}")
    }
}

val syncBundledWhisperCli = tasks.register("syncBundledWhisperCli") {
    group = "build"
    dependsOn(buildBundledWhisperCliUnix)
    notCompatibleWithConfigurationCache("ensures whisper binaries then copies into native-distribution")
    doLast {
        ensureBundledWhisperWin(repoRootPath)
        val outRoot = File(composeNativeDistPath, "common")
        outRoot.mkdirs()
        when {
            isWindowsOs() -> {
                val srcDir = File(repoRootPath, "native/bundled/whisper-win")
                if (!srcDir.isDirectory) return@doLast
                val outDir = File(outRoot, "whisper-bin")
                outDir.mkdirs()
                srcDir.copyRecursively(outDir, overwrite = true)
            }
            else -> {
                val src = File(repoRootPath, "native/bundled/whisper-cli")
                if (!src.isFile) return@doLast
                val out = File(outRoot, "whisper-cli")
                Files.copy(src.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                out.setExecutable(true, false)
                println("已同步 whisper-cli → ${out.absolutePath}")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.danteandroid.transbee.MainKt"
        jvmArgs(
            "-Djava.net.useSystemProxies=true",
            "--add-opens",
            "java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens",
            "java.desktop/sun.awt.windows=ALL-UNNAMED",
        )

        nativeDistributions {
            modules("java.net.http")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Transbee"
            packageVersion = computedPackageVersion
            appResourcesRootDir.set(project.layout.projectDirectory.dir("native-distribution"))
            macOS {
                bundleID = "com.danteandroid.transbee"
                dockName = "Transbee" // 专门用于 Dock 栏显示的名字
                iconFile.set(project.file("icons/app_icon.icns"))
                entitlementsFile.set(project.file("entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
            <key>CFBundleLocalizations</key>
            <array>
                <string>zh</string>
                <string>zh_CN</string>
                <string>en</string>
            </array>
            <key>CFBundleDevelopmentRegion</key>
            <string>zh_CN</string>
        """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("icons/app_icon.ico"))
                shortcut = true
                menu = true
                // Win10/11：当前用户安装，通常无需管理员权限；与系统 DPI 缩放由 JVM/Compose 处理
                perUserInstall = true
            }
            linux {
                iconFile.set(project.file("icons/app_icon_linux.png"))
            }
        }
    }
}

afterEvaluate {
    tasks.named("prepareAppResources") {
        dependsOn(
            syncAppleTranslateBundle,
            syncBundledFfmpeg,
            syncBundledWhisperCli,
        )
    }
    val nativeDistPath = layout.projectDirectory.dir("native-distribution/common").asFile.absolutePath
    val dockIconPath =
        layout.projectDirectory.file("src/jvmMain/composeResources/drawable/app_icon.png").asFile.absolutePath
    fun JavaExec.configureComposeRunResources() {
        systemProperty("compose.application.resources.dir", nativeDistPath)
        if (isMacOs()) {
            jvmArgs("-Xdock:icon=$dockIconPath", "-Xdock:name=Transbee")
            systemProperty("apple.awt.application.name", "Transbee")
        }
    }
    tasks.named<JavaExec>("run") {
        configureComposeRunResources()
    }
    listOf(
        "hotRunJvm",
        "hotRunJvmAsync",
        "jvmRun",
        "hotDevJvm",
        "hotDevJvmAsync",
        "runDistributable",
    ).forEach { tn ->
        tasks.findByName(tn)?.let { t ->
            if (t is JavaExec) {
                t.configureComposeRunResources()
            }
        }
    }

    // Compose 默认注册两套桌面产物：default → build/compose/binaries/main/；
    // release（含 ProGuard）→ main-release/。本项目只使用 default，禁用 release 相关任务。
    listOf(
        "createReleaseDistributable",
        "flattenReleaseJars",
        "notarizeReleaseDmg",
        "packageReleaseDeb",
        "packageReleaseDistributionForCurrentOS",
        "packageReleaseDmg",
        "packageReleaseMsi",
        "packageReleaseUberJarForCurrentOS",
        "proguardReleaseJars",
        "runRelease",
        "runReleaseDistributable",
    ).forEach { tn ->
        tasks.findByName(tn)?.enabled = false
    }
}
