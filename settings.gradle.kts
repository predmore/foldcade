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
    ":plugins:aps3e",
    ":plugins:armsx2",
    ":plugins:azahar",
    ":plugins:droiddeck",
    ":plugins:duckstation",
    ":plugins:eden",
    ":plugins:emulators",
    ":plugins:gamenative",
    ":plugins:local-folder",
    ":plugins:melonds",
    ":plugins:retroarch",
    ":plugins:moonlight",
    ":plugins:sample",
    ":plugins:romm",
    ":samples:out-of-tree",
    ":romm",
    ":net",
    ":gamestream",
    ":artwork",
)

// :api, :host, :net, :artwork, :romm, :gamestream, and every :plugins:* module apply foldcade.jvm-api33.
// New plugin modules inherit the API 33 Animal Sniffer check the same way.
gradle.lifecycle.afterProject {
    val appJvmModule = path == ":api" || path == ":host" || path == ":net" || path == ":artwork" || path == ":romm" || path == ":gamestream" || path.startsWith(":plugins:")
    if (appJvmModule && !pluginManager.hasPlugin("foldcade.jvm-api33")) {
        error("$path must apply id(\"foldcade.jvm-api33\")")
    }
}
