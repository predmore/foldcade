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

rootProject.name = "foldcade"
include(
    ":api",
    ":language",
    ":app",
    ":host",
    ":plugins:local-folder",
    ":plugins:sample",
    ":samples:out-of-tree",
    ":romm",
)
