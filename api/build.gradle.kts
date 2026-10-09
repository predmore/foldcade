// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("foldcade.jvm-api33")
}

description = "Foldcade plugin API. Apache License, Version 2.0."

tasks.jar {
    manifest {
        attributes(
            "Specification-Title" to "Foldcade Plugin API",
            "Specification-Vendor" to "Foldcade",
            "Bundle-License" to "https://www.apache.org/licenses/LICENSE-2.0.txt",
            "SPDX-License-Identifier" to "Apache-2.0",
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
