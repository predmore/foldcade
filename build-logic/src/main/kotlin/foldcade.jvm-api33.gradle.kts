import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("ru.vyarus.animalsniffer")
}

// Android minSdk is 33. These JVM modules are not linted by the Android plugin.
// Animal Sniffer fails the build when compiled classes use an API above 33.
// testDebugUnitTest depends on each module's test task, so CI fails with the unit tests.
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    val gummyBears = extensions.getByType(VersionCatalogsExtension::class.java)
        .named("libs")
        .findVersion("gummyBears")
        .get()
        .requiredVersion
    dependencies.add(
        "signature",
        "com.toasttab.android:gummy-bears-api-33:$gummyBears@signature",
    )
    tasks.named("animalsnifferMain") {
        group = "verification"
    }
    tasks.named("test") {
        dependsOn("animalsnifferMain")
    }
}
