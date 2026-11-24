plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())

    jvm()

    android {
        namespace = "org.kodein.arch.vie.demo.compose"
        compileSdk = findProperty("android.compileSdk")!!.toString().toInt()
        minSdk = findProperty("android.minSdk")!!.toString().toInt()
    }

    iosArm64()
    iosSimulatorArm64()

    js {
        browser {
            testTask {
                // There's a weird bug in Compose JS that fails test on JS.
                // See https://youtrack.jetbrains.com/issue/CMP-4906
                enabled = false
            }
        }
        binaries.executable()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // The screens render the contracts and the ViewModels of demo-shared, through VIEView.
            implementation(projects.demo.demoShared)
            implementation(projects.vieCompose)
            implementation(projects.vieMvi)

            implementation(libs.androidx.jb.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.androidx.navigation3.runtime)

            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIcons)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.vieTest)

            implementation(libs.compose.ui.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            runtimeOnly(libs.compose.ui.tooling)
        }
        androidMain.dependencies {
            runtimeOnly(libs.compose.ui.tooling)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
