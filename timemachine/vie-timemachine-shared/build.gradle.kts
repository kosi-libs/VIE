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
        namespace = "org.kodein.arch.vie.tm.protocol"
        compileSdk = findProperty("android.compileSdk")!!.toString().toInt()
        minSdk = findProperty("android.minSdk")!!.toString().toInt()
    }

    // Narrowed to the target set of the TimeMachine client, the only consumer of this protocol
    // besides the JVM server - a Compose module, which cannot go wider.
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
            // TimeMachineJson is a public Json instance and the protocol messages carry
            // JsonElement payloads.
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    compilerOptions {
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
    }
}

// TimeMachine is still in development: these modules keep the published-module naming and
// will be published once they are ready.
//mavenPublishing {
//    pom {
//        name = "vie-timemachine-shared"
//        description = "VIE TimeMachine shared components between client and server"
//    }
//}
