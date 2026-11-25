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
            // The helpers in skills/vie/swift start with `import Shared`: the framework of this
            // demo is named so that they compile as they are.
            baseName = "Shared"
            isStatic = true

            // Only the declarations of an exported module reach the framework header: without the
            // export, the helpers could not name VIEViewModel or VIEHost, nor the SwiftUI list
            // TaskListVM.
            export(projects.demo.demoShared)
            export(projects.vieMvi)
        }
    }

    sourceSets {
        iosMain.dependencies {
            // Exported by the framework, which requires an api dependency.
            api(projects.demo.demoShared)
            api(projects.vieMvi)

            // The task editor, a Compose screen that SwiftUI presents.
            implementation(projects.demo.demoCompose)

            implementation(libs.androidx.lifecycle.viewmodel.compose)

            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
