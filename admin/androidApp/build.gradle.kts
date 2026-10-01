import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// The Play upload key lives outside the repository (design D10); locally it comes from ai/secrets/,
// in CI from the ANDROID_UPLOAD_* environment variables. Without either only unsigned release builds are possible.
data class UploadKey(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

val uploadKey: UploadKey? = rootDir.resolve("../ai/secrets/android-upload.properties").takeIf { it.isFile }?.let { file ->
    val properties = Properties().apply { file.inputStream().use { load(it) } }
    UploadKey(
        storeFile = file.parentFile.resolve(properties.getProperty("storeFile")),
        storePassword = properties.getProperty("storePassword"),
        keyAlias = properties.getProperty("keyAlias"),
        keyPassword = properties.getProperty("keyPassword"),
    )
} ?: System.getenv("ANDROID_UPLOAD_STORE_FILE")?.takeIf { it.isNotBlank() }?.let { storeFile ->
    fun required(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("ANDROID_UPLOAD_STORE_FILE is set but $name is missing")
    UploadKey(
        storeFile = file(storeFile),
        storePassword = required("ANDROID_UPLOAD_STORE_PASSWORD"),
        keyAlias = required("ANDROID_UPLOAD_KEY_ALIAS"),
        keyPassword = required("ANDROID_UPLOAD_KEY_PASSWORD"),
    )
}

// The release version X.Y.Z comes from the pipeline's bump job (-Ppresserl.version=X.Y.Z); design D1.
val releaseVersion: String? = providers.gradleProperty("presserl.version").orNull?.takeIf { it.isNotBlank() }
val appVersionName: String = releaseVersion ?: "0.0.0-local"
val appVersionCode: Int = releaseVersion?.let(::versionCodeOf) ?: 1

fun versionCodeOf(version: String): Int {
    val parts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(version)?.groupValues?.drop(1)?.map { it.toLong() }
        ?: throw GradleException("presserl.version '$version' is not of the form X.Y.Z")
    val (major, minor, patch) = parts
    if (minor > 99 || patch > 99_999) {
        throw GradleException(
            "presserl.version '$version' is out of range: minor must be 0..99 and patch 0..99999 " +
                "(versionCode = X * 10000000 + Y * 100000 + Z)",
        )
    }
    val code = major * 10_000_000 + minor * 100_000 + patch
    if (code > 2_100_000_000) {
        throw GradleException("presserl.version '$version' gives versionCode $code, above Google Play's limit 2100000000")
    }
    return code.toInt()
}

tasks.register("printAndroidVersion") {
    val name = appVersionName
    val code = appVersionCode
    doLast { println("versionName=$name versionCode=$code") }
}

android {
    namespace = "info.unterrainer.presserl.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "info.unterrainer.presserl"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = uploadKey.storeFile
                storePassword = uploadKey.storePassword
                keyAlias = uploadKey.keyAlias
                keyPassword = uploadKey.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
}
