import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                // Development server on :8081; the backend (quarkus dev) runs on :8080
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).copy(
                    port = 8081,
                )
            }
            // Unit tests run in headless Chrome (Karma); Node cannot load the Compose runtime
            testTask { useKarma { useChromeHeadless() } }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.richeditor.compose)
            implementation(libs.qrose.encoder.matrix)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }
    }
}

// Android target only where an SDK exists (see settings.gradle.kts); the image builds the web app alone.
if (gradle.extra["presserl.androidSdkPresent"] == true) {
    apply(plugin = "com.android.kotlin.multiplatform.library")
    kotlin {
        extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
            namespace = "info.unterrainer.presserl.admin"
            compileSdk = libs.versions.android.compileSdk.get().toInt()
            minSdk = libs.versions.android.minSdk.get().toInt()
            androidResources { enable = true }
            withHostTest {}
            withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
        }
        sourceSets.getByName("androidMain").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.play.services.code.scanner)
        }
        sourceSets.getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
        }
        sourceSets.getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

compose.resources {
    packageOfResClass = "info.unterrainer.presserl.admin.resources"
}

// The Wasm runtime fetches a fallback font from fonts.gstatic.com for every character its loaded
// fonts lack, which the admin CSP blocks. UI symbols are drawn instead (ui/Icons.kt); this keeps
// such characters out of UI string literals and string resources.
val checkUiGlyphs by tasks.registering {
    group = "verification"
    description = "Rejects characters in UI texts that the Wasm runtime cannot render without downloading a font."
    val sources = fileTree("src/commonMain") { include("kotlin/**/*.kt", "composeResources/values*/strings.xml") }
    val root = projectDir
    inputs.files(sources)
    doLast {
        // Basic Latin, Latin-1 Supplement and Latin Extended-A, plus the punctuation verified to render
        // with the loaded fonts: – “ ” „ • …
        val allowed = setOf(0x2013, 0x201C, 0x201D, 0x201E, 0x2022, 0x2026)
        val comment = listOf("//", "/*", "*", "<!--")
        val violations = sources.files.sorted().flatMap { file ->
            file.readLines().withIndex()
                .filterNot { (_, line) -> comment.any { line.trimStart().startsWith(it) } }
                .flatMap { (index, line) ->
                    line.codePoints().toArray().filter { it > 0x17F && it !in allowed }.distinct().map {
                        "${file.relativeTo(root)}:${index + 1}: U+%04X '%s'".format(it, String(Character.toChars(it)))
                    }
                }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Characters outside the UI glyph allowlist (draw symbols with ui/Icons.kt instead):\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}

tasks.named("check") { dependsOn(checkUiGlyphs) }
