plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())

    jvm()

    android {
        namespace = "org.kodein.arch.vie"
        compileSdk = findProperty("android.compileSdk")!!.toString().toInt()
        minSdk = findProperty("android.minSdk")!!.toString().toInt()
    }

    iosArm64()
    iosSimulatorArm64()

    js {
        browser()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            // VIEViewModel extends androidx.lifecycle.ViewModel, so the type is part of this
            // module's public signatures.
            api(libs.androidx.jb.lifecycle.viewmodel.compose)
            // VIEView and SignalEffect are @Composable, so the runtime is part of this module's
            // public signatures.
            api(libs.compose.runtime)
            // modelFlow is a StateFlow and collectToModel/collectToSignal extend Flow, so
            // coroutines is part of this module's public signatures.
            api(libs.kotlinx.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xwarning-level=ERROR_SUPPRESSION:disabled")
    }
}

// The Compose plugin fails the build unless a web target that could run Compose UI tests declares
// binaries.executable(), to bundle the Skiko runtime (https://youtrack.jetbrains.com/issue/CMP-4906).
// This module has no Compose UI test, so there is nothing to bundle, and a published library has
// no business producing a webpack executable. The tasks are registered late by the Compose plugin,
// hence matching rather than named().
tasks.matching { it.name.startsWith("checkComposeUiTestConfiguration") }.configureEach { enabled = false }

mavenPublishing {
    pom {
        name = "vie-mvi"
        description = "Vue Intention État: an MVI architecture for Compose Multiplatform"
    }
}
