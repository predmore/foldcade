pluginManagement {
    includeBuild("build-logic")
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
    ":plugins:romm",
    ":samples:out-of-tree",
    ":romm",
)

// :api, :host, :romm, and every :plugins:* module apply foldcade.jvm-api33.
// New plugin modules inherit the API 33 Animal Sniffer check the same way.
gradle.lifecycle.afterProject {
    val appJvmModule = path == ":api" || path == ":host" || path == ":romm" || path.startsWith(":plugins:")
    if (appJvmModule && !pluginManager.hasPlugin("foldcade.jvm-api33")) {
        error("$path must apply id(\"foldcade.jvm-api33\")")
    }
}
