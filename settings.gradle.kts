pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    // Downloads the JDK the toolchain asks for when it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    ":core",
    ":in-memory",
    ":lettuce",
    ":compatibility",
)

project(":core").name = "redif-core"
project(":in-memory").name = "redif-in-memory"
project(":lettuce").name = "redif-lettuce"
project(":compatibility").name = "redif-compatibility"

rootProject.name = "redif"
