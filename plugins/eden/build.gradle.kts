plugins {
    alias(libs.plugins.kotlin.jvm)
    id("foldcade.jvm-api33")
}

dependencies {
    implementation(project(":api"))
    testImplementation(libs.junit)
    testImplementation(project(":plugins:emulators"))
}

tasks.test {
    useJUnit()
}
