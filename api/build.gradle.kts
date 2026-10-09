import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("foldcade.jvm-api33")
}

kotlin {
    compilerOptions {
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
}

tasks.test {
    useJUnit()
}
