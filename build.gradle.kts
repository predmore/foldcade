plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Fast loop from the v1 scope. Android modules expose testDebugUnitTest.
// JVM modules expose test. One command runs all of them. No emulator.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Unit tests only. Does not assemble, install, or boot an emulator."
    dependsOn(
        ":api:test",
        ":net:test",
        ":artwork:test",
        ":romm:test",
        ":gamestream:test",
        ":host:test",
        ":plugins:aps3e:test",
        ":plugins:armsx2:test",
        ":plugins:azahar:test",
        ":plugins:droiddeck:test",
        ":plugins:duckstation:test",
        ":plugins:dolphin:test",
        ":plugins:ppsspp:test",
        ":plugins:flycast:test",
        ":plugins:explus:test",
        ":plugins:eden:test",
        ":plugins:emulators:test",
        ":plugins:gamenative:test",
        ":plugins:local-folder:test",
        ":plugins:melonds:test",
        ":plugins:retroarch:test",
        ":plugins:moonlight:test",
        ":plugins:sample:test",
        ":plugins:romm:test",
        ":samples:out-of-tree:test",
        ":language:testDebugUnitTest",
        ":app:testDebugUnitTest",
    )
}

tasks.register("lintDebug") {
    group = "verification"
    description = "Android lint for :app and :language. Fails on errors."
    dependsOn(":app:lintDebug", ":language:lintDebug")
}

tasks.register("updateThemeContract") {
    group = "verification"
    description = "Rewrite themes/contract.json and themes/theme.schema.json from the engine."
    dependsOn(":language:testDebugUnitTest")
}
