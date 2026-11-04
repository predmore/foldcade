plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.foldcade.language"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.zxing.core)
}

// Exercise the same archive that the app packages, including its reference validation.
val afterglowZip = project(":app").layout.buildDirectory.file("generated/themeAssets/themes/afterglow.zip")
tasks.withType<Test>().configureEach {
    dependsOn(":app:packageAfterglowTheme")
    inputs.file(afterglowZip).withPathSensitivity(PathSensitivity.NONE)
    systemProperty("foldcade.afterglowZip", afterglowZip.get().asFile.absolutePath)
    // ./gradlew updateThemeContract rewrites themes/contract.json and themes/theme.schema.json.
    // -PwriteThemeContract=true does the same while the unit tests run.
    val requested = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
    val writeThemeContract = providers.gradleProperty("writeThemeContract").getOrElse("false")
    val updatingContract = "updateThemeContract" in requested
    systemProperty("foldcade.writeThemeContract", if (updatingContract) "true" else writeThemeContract)
    // The updater runs only ThemeContractTest. A suite named on the same command line still runs whole.
    val suite = setOf("testDebugUnitTest", "test", "check")
    if (name == "testDebugUnitTest" && updatingContract && requested.none { it in suite }) {
        filter.includeTestsMatching("*ThemeContractTest*")
    }
    inputs.files(rootProject.file("themes/contract.json"), rootProject.file("themes/theme.schema.json")).optional()
    inputs.dir(rootProject.file("samples/themes")).withPathSensitivity(PathSensitivity.RELATIVE)
}
