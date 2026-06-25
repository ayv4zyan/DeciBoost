pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DeciBoost"

include(
    ":app",
    ":core:audio:policy",
    ":core:audio:android",
    ":core:domain",
    ":core:data",
    ":feature:boost",
    ":feature:settings",
    ":benchmark",
    ":testing:fakes",
    ":testing:audio-harness",
)