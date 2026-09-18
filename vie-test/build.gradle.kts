plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())

    jvm()

    android {
        namespace = "org.kodein.arch.vie.test"
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
        browser {
            testTask {
                // This module pulls Compose in transitively, and the wasmJs test bundle cannot
                // resolve the Skiko runtime it then needs - binaries.executable() does not help.
                // See https://youtrack.jetbrains.com/issue/CMP-4906
                enabled = false
            }
        }
    }

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            // MockVIE implements VIE and runVMTest takes a VIEViewModel, so both types are part of
            // this module's public signatures.
            api(projects.vieMvi)
            // MockVIE exposes MutableStateFlow, MutableSharedFlow and ReceiveChannel.
            api(libs.kotlinx.coroutines)
            // runVMTest takes TestScope lambdas and returns a TestResult.
            api(libs.kotlinx.coroutines.test)

            implementation(libs.androidx.jb.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    pom {
        name = "vie-test"
        description = "VIE test utilities: a mock VIE and a ViewModel test harness"
    }
}
