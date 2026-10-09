plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Fast loop from the v1 scope. Android modules expose testDebugUnitTest;
// :api is a JVM module and exposes test. One command runs both.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Unit tests only. Does not assemble, install, or boot an emulator."
    dependsOn(":api:test", ":language:testDebugUnitTest", ":app:testDebugUnitTest")
}
