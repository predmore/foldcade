plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("foldcade.jvm-api33")
}

dependencies {
    compileOnly(project(":api"))
    implementation(project(":romm"))
    implementation(project(":artwork"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(project(":api"))
    testImplementation(project(":host"))
    testImplementation(project(":plugins:eden"))
    testImplementation(project(":romm"))
    testImplementation(project(":net"))
    testImplementation(libs.coroutines.core)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
