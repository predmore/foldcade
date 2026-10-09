plugins {
    alias(libs.plugins.kotlin.jvm)
    id("foldcade.jvm-api33")
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
