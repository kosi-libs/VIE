plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
    // TimeMachine is still in development: these modules keep the published-module naming and
    // will be published once they are ready.
//    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())

    jvm()

    android {
        namespace = "org.kodein.arch.vie.tm.client"
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
            implementation(projects.timemachine.vieTimemachineShared)
            implementation(projects.vieMvi)

            implementation(libs.androidx.jb.lifecycle.viewmodel.compose)

            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIcons)
            implementation(libs.compose.runtime)

            implementation(libs.kotlinx.coroutines)
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

// TimeMachine is still in development: these modules keep the published-module naming and
// will be published once they are ready.
//mavenPublishing {
//    pom {
//        name = "vie-timemachine-client"
//        description = "VIE TimeMachine client"
//    }
//}
