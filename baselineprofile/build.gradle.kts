plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "app.foldcade.baselineprofile"
    compileSdk { version = release(37) }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 33
        targetSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.benchmark.macro)
    implementation(libs.uiautomator)
    implementation(libs.test.runner)
    implementation(libs.test.junit)
}
