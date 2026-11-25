plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
            // export, the helpers could not name VIEViewModel or VIEHost.
            export(projects.vieMvi)
            export(projects.demo.demoShared)
        }
    }

    sourceSets {
        iosMain.dependencies {
            // A SwiftUI app: no Compose in this framework, on purpose.
            api(projects.vieMvi)
            api(projects.demo.demoShared)
        }
    }
}
