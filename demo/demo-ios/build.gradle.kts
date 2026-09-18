plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "DemoIos"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(projects.demo.demoShared)

            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
