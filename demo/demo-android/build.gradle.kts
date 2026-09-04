plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
}

android {
    namespace = "org.kodein.arch.vie.demo.android"
    compileSdk = findProperty("android.compileSdk")!!.toString().toInt()

    defaultConfig {
        applicationId = "org.kodein.arch.vie.demo.android"
        minSdk = findProperty("android.minSdk")!!.toString().toInt()
        targetSdk = findProperty("android.targetSdk")!!.toString().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(projects.demo.demoShared)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
}
