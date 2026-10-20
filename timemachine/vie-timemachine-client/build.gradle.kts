plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.serialization)
    // TimeMachine is still in development: these modules keep the published-module naming and
    // will be published once they are ready.
//    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())

    jvm()

    android {
        namespace = "org.kodein.arch.vie.timemachine.client"
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
            api(projects.timemachine.vieTimemachineShared)

            // TimeMachine.middleware() returns a VIEMiddleware, so vie-mvi is part of this module's
            // public signatures.
            api(projects.vieMvi)

            // The WebSockets plugin and the Js engine both live in core: ktor-client-websockets and
            // ktor-client-js are empty shims.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.vieTest)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xwarning-level=ERROR_SUPPRESSION:disabled")
        optIn.addAll(
            "kotlin.uuid.ExperimentalUuidApi",
            "kotlin.time.ExperimentalTime",
            "kotlinx.serialization.ExperimentalSerializationApi",
            "kotlin.concurrent.atomics.ExperimentalAtomicApi",
        )
    }
}

// TimeMachine is still in development: these modules keep the published-module naming and
// will be published once they are ready.
//mavenPublishing {
//    pom {
//        name = "vie-timemachine-client"
//        description = "VIE TimeMachine client"
//    }
//}
