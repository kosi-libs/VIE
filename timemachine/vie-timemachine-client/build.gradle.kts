plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.serialization)
    // Not published yet: see mavenPublishing at the end of this file.
    // alias(libs.plugins.mavenPublish)
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

            // Core holds the WebSockets plugin and the Js engine, which the web targets use:
            // ktor-client-websockets and ktor-client-js are empty shims.
            //
            // The other platforms each add an engine below, and every platform names its own
            // (timeMachineEngine): ktor would otherwise discover the host app's, whatever its WebSocket
            // support. An engine dependency also registers itself for the host app's own HttpClient(),
            // so each platform takes the one least likely to change that app's default.
            implementation(libs.ktor.client.core)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.vieTest)
        }
        jvmMain.dependencies {
            // CIO's priority (0.0) is below every other engine's.
            implementation(libs.ktor.client.cio)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
        }
        androidMain.dependencies {
            // CIO, as on the jvm.
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            // Darwin, which an iOS app most likely ships already: every native engine registers at
            // the same priority, so any other one would tie with the app's.
            implementation(libs.ktor.client.darwin)
        }
    }

    compilerOptions {
        optIn.addAll(
            "kotlinx.serialization.ExperimentalSerializationApi",
            "kotlin.concurrent.atomics.ExperimentalAtomicApi",
        )
    }
}

// TimeMachine is still in development: this module keeps its published name, and will be
// published, with the plugin commented out above, once it is ready.
// mavenPublishing {
//     pom {
//         name = "vie-timemachine-client"
//         description = "VIE TimeMachine client"
//     }
// }
