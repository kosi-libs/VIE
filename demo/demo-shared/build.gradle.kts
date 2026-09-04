import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)

    jvm()

    android {
        namespace = "org.kodein.arch.vie.demo"
        compileSdk = 37
        minSdk = 26
    }

    iosArm64()
    iosSimulatorArm64()

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
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin"))
        }

        commonMain.dependencies {
            implementation(projects.vie)
//            implementation(projects.timemachine.vieTimemachineClient)

            implementation(libs.androidx.navigation3.runtime)

            implementation(libs.androidx.jb.lifecycle.viewmodel.compose)
            implementation(libs.androidx.jb.lifecycle.viewmodel.navigation3)

            implementation(libs.androidx.jb.navigation3.ui)

            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIcons)
            implementation(libs.compose.runtime)

            implementation(libs.inara.runtime)

            implementation(libs.kotlinx.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.inara.processor)
}

// Every compilation - and every per-target KSP task, which also reads commonMain as part of its
// merged source set even though it has no processor registered - consumes the KSP-generated
// commonMain sources, so all of them must wait for KSP to run.
tasks.matching { it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    if (name.startsWith("ksp") || this is KotlinCompilationTask<*>) {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
