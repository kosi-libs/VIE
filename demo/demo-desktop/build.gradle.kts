plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())

    jvm()

    js {
        browser()
        binaries.executable()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.demo.demoShared)
            implementation(projects.timemachine.vieTimemachineClient)

            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose {
    desktop {
        application {
            mainClass = "org.kodein.arch.vie.demo.MainKt"

            // `run` is this app's debug build: it starts the TimeMachine probe, unless -Pvie.timemachine=false.
            jvmArgs += "-Dvie.timemachine=${findProperty("vie.timemachine") ?: true}"
        }
    }
}
