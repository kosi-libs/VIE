pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Kosi-MVIVM"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":mvivm",
    ":timemachine:mvivm-timemachine-shared",
    ":timemachine:mvivm-timemachine-client",
    ":timemachine:mvivm-timemachine-server",

    ":demo:demo-shared",

    ":demo:demo-android",
    ":demo:demo-desktop",
)
