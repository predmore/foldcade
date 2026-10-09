plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.foldcade"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "app.foldcade"
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
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
    implementation(project(":api"))
    implementation(project(":language"))
    implementation(project(":romm"))
    implementation(project(":plugins:romm"))
    implementation(libs.androidx.core)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
}
