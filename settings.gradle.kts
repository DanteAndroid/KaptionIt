rootProject.name = "Transbee"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")

/** 安装包任务执行前递增根目录 [VERSION]（semver x.y.z 的 z+1）。版本仅此一处维护。 */
private fun isReleasePackagingTask(taskName: String): Boolean {
    val n = taskName.lowercase().replace(":", "")
    return n.endsWith("packagedmg") ||
        n.endsWith("packagemsi") ||
        n.endsWith("packagedeb") ||
        n.endsWith("packagedistributionforcurrentos")
}

private fun readCurrentVersion(rootDir: java.io.File): String? {
    val versionFile = java.io.File(rootDir, "VERSION")
    if (!versionFile.isFile) return null
    return versionFile.readText().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
}

private fun bumpPatchSemver(current: String): String {
    val m = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(current.trim())
        ?: error("Invalid app version '$current' (expect major.minor.patch, e.g. 1.2.3)")
    val (ma, mi, pa) = m.destructured
    return "$ma.$mi.${pa.toInt() + 1}"
}

if (
    gradle.startParameter.taskNames.any(::isReleasePackagingTask) &&
    !gradle.startParameter.isDryRun
) {
    val rootDir = settings.rootDir
    val current = readCurrentVersion(rootDir) ?: "1.0.0"
    val next = bumpPatchSemver(current)
    java.io.File(rootDir, "VERSION").writeText("$next\n")
    println("[Transbee] Release packaging: version $current → $next (VERSION)")
}