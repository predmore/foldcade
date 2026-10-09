plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":api"))
    implementation(project(":host"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
