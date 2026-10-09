plugins {
    alias(libs.plugins.kotlin.jvm)
    id("foldcade.jvm-api33")
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
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
}

tasks.test {
    useJUnit()
}
