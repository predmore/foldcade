plugins {
    alias(libs.plugins.kotlin.jvm)
    id("foldcade.jvm-api33")
}

dependencies {
    compileOnly(project(":api"))
    implementation(project(":romm"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(project(":api"))
    testImplementation(project(":host"))
    testImplementation(project(":romm"))
    testImplementation(libs.coroutines.core)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
