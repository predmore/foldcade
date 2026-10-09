import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.reporting.ReportingExtension
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer

plugins {
    id("ru.vyarus.animalsniffer")
}

// Animal Sniffer 2.0.1 maps reportsDir through ReportingExtension.file(String),
// and reads it when resolving each report. Gradle 10 removes that method.
// Set the output file here so that mapping never runs. The task name is
// animalsniffer + target, and the directory stays build/reports/animalsniffer.
// Iterate existing reports. configureEach would store this script on the task,
// which the configuration cache cannot keep.
val animalSnifferReports = extensions.getByType(ReportingExtension::class.java).baseDirectory
tasks.withType<AnimalSniffer>().configureEach {
    val snifferTarget = name.removePrefix("animalsniffer").replaceFirstChar { it.lowercase() }
    reports.forEach { report ->
        report.outputLocation.set(
            animalSnifferReports.file("animalsniffer/$snifferTarget.${report.name}").get(),
        )
    }
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
