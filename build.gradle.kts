plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Fast loop from the v1 scope. Android modules expose testDebugUnitTest.
// JVM modules expose test. One command runs all of them. No emulator.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Unit tests only. Does not assemble, install, or boot an emulator."
    dependsOn(
        ":api:test",
        ":romm:test",
        ":host:test",
        ":plugins:azahar:test",
        ":plugins:gamenative:test",
        ":plugins:local-folder:test",
        ":plugins:melonds:test",
        ":plugins:sample:test",
        ":plugins:romm:test",
        ":samples:out-of-tree:test",
        ":language:testDebugUnitTest",
        ":app:testDebugUnitTest",
    )
}
