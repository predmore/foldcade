import org.gradle.api.file.RegularFileProperty
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.Jar
import org.gradle.process.CommandLineArgumentProvider

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
    implementation(project(":api"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(project(":plugins:sample"))
}

abstract class OutOfTreeJarArgument : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    override fun asArguments(): Iterable<String> =
        listOf("-Dfoldcade.outOfTreeJar=${jar.get().asFile.absolutePath}")
}

tasks.test {
    useJUnit()
    val outOfTreeJar = project(":samples:out-of-tree").tasks.named<Jar>("jar")
    dependsOn(outOfTreeJar)
    val argument = objects.newInstance(OutOfTreeJarArgument::class.java)
    argument.jar.set(outOfTreeJar.flatMap { it.archiveFile })
    jvmArgumentProviders.add(argument)
}
