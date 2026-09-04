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

rootProject.name = "Kosi-VIE"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":vie",
    ":vie-test",
    ":timemachine:vie-timemachine-shared",
    ":timemachine:vie-timemachine-client",
    ":timemachine:vie-timemachine-server",

    ":demo:demo-shared",

    ":demo:demo-android",
    ":demo:demo-desktop",
    ":demo:demo-ios",
)
