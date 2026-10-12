plugins {
    alias(libs.plugins.kotlin.jvm)
    id("foldcade.jvm-api33")
}

dependencies {
    implementation(project(":api"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
}

tasks.test {
    useJUnit()
}
