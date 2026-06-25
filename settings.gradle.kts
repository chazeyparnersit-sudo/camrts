pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Necesario para RootEncoder (se distribuye por JitPack)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "StreamCam"
include(":app")
