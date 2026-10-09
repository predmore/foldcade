plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.coroutines.core)
    testImplementation(project(":api"))
    testImplementation(libs.coroutines.core)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
