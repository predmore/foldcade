plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.animalsniffer)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    "signature"("com.toasttab.android:gummy-bears-api-33:${libs.versions.gummyBears.get()}@signature")
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
}

// Android minSdk is 33. This JVM module is not linted by the Android plugin.
// Animal Sniffer fails the build when compiled classes use an API above 33.
// testDebugUnitTest depends on :romm:test, so CI fails with the unit tests.
tasks.named("animalsnifferMain") {
    group = "verification"
}

tasks.test {
    useJUnit()
    dependsOn("animalsnifferMain")
}
