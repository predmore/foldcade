// SPDX-License-Identifier: MIT

import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("foldcade.jvm-api33")
}

description = "Foldcade plugin API. MIT License."

tasks.jar {
    manifest {
        attributes(
            "Specification-Title" to "Foldcade Plugin API",
            "Specification-Vendor" to "Foldcade",
            "Bundle-License" to "https://opensource.org/license/mit",
            "SPDX-License-Identifier" to "MIT",
        )
    }
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
    }
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
