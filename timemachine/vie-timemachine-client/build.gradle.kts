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
            // ktor-client-js are empty shims. Each platform names its engine (timeMachineEngine) rather
            // than letting ktor discover one, which would pick the host app's own engine whatever its
            // WebSocket support. An engine dependency also registers itself for the host app's own
            // HttpClient(), so each platform gets the one least likely to change that app's default:
            // - jvm and android: CIO, whose priority (0.0) is below every other engine's;
            // - iOS: Darwin, which an iOS app most likely ships already. Every native engine registers
            //   at the same priority, so any other one would tie with the app's;
            // - web: the Js engine, from core.
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
            implementation(libs.ktor.client.cio)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
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

// TimeMachine is still in development: these modules keep the published-module naming and
// will be published once they are ready.
//mavenPublishing {
//    pom {
//        name = "vie-timemachine-client"
//        description = "VIE TimeMachine client"
//    }
//}
