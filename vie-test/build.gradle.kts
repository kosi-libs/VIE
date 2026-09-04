plugins {
    alias(libs.plugins.kotlin.multiplatform)
//    alias(libs.plugins.kotlin.plugin.compose)
//    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(17)

    jvm()

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
            implementation(projects.vie)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.androidx.jb.lifecycle.viewmodel.compose)
        }
    }
}

mavenPublishing {
    pom {
        name = "vie-test"
        description = "VIE test utils"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }
    }
}
