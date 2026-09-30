import java.util.Properties

rootProject.name = "presserl-admin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// The Android target is built only where an Android SDK exists; the image and CI build the web app only.
val androidSdkPresent = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").any { !System.getenv(it).isNullOrBlank() } ||
    settingsDir.resolve("local.properties").takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } }.getProperty("sdk.dir") }
        .isNullOrBlank().not()
gradle.extra["presserl.androidSdkPresent"] = androidSdkPresent

include(":composeApp")
if (androidSdkPresent) include(":androidApp")
