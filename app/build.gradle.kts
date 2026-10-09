import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseKeystore = providers.environmentVariable("FOLDCADE_RELEASE_KEYSTORE")
val releaseStorePassword = providers.environmentVariable("FOLDCADE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("FOLDCADE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("FOLDCADE_RELEASE_KEY_PASSWORD")
val allowUnsignedRelease = providers.gradleProperty("foldcadeAllowUnsigned").orNull == "true"

android {
    namespace = "app.foldcade"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "app.foldcade"
        minSdk = 33
        targetSdk = 33
        // Local fallback. CI passes -PfoldcadeVersionCode from git rev-list --count HEAD.
        // A stable tag is cut only when versionName changes.
        versionCode = 1
        versionName = "0.1.0"
        val requestedVersionCode = providers.gradleProperty("foldcadeVersionCode").orNull?.trim().orEmpty()
        if (requestedVersionCode.isNotEmpty()) {
            val parsed = requestedVersionCode.toIntOrNull()
            if (parsed == null || parsed <= 0) {
                throw GradleException(
                    "foldcadeVersionCode must be a positive integer, was '$requestedVersionCode'.",
                )
            }
            versionCode = parsed
        }
    }
    signingConfigs {
        create("release") {
            val path = releaseKeystore.orNull
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
            }
            storePassword = releaseStorePassword.orNull
            keyAlias = releaseKeyAlias.orNull
            keyPassword = releaseKeyPassword.orNull
        }
    }
    buildTypes {
        debug {
            // Side-by-side with the signed Obtainium install. Release stays app.foldcade.
            applicationIdSuffix = ".debug"
        }
        release {
            if (!allowUnsignedRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += "ogg"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Fail before packageRelease can write an unsigned APK. No debug key stands in.
val requireReleaseSigning = tasks.register("requireReleaseSigning") {
    group = "build"
    description = "Fails the release build when the release keystore is not configured."
    val keystorePath = releaseKeystore
    val storePasswordValue = releaseStorePassword
    val aliasValue = releaseKeyAlias
    val keyPasswordValue = releaseKeyPassword
    doLast {
        val problems = mutableListOf<String>()
        val path = keystorePath.orNull
        if (path.isNullOrBlank() || !File(path).isFile) {
            problems += "FOLDCADE_RELEASE_KEYSTORE"
        }
        if (storePasswordValue.orNull.isNullOrBlank()) problems += "FOLDCADE_RELEASE_STORE_PASSWORD"
        if (aliasValue.orNull.isNullOrBlank()) problems += "FOLDCADE_RELEASE_KEY_ALIAS"
        if (keyPasswordValue.orNull.isNullOrBlank()) problems += "FOLDCADE_RELEASE_KEY_PASSWORD"
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured (${problems.joinToString()}). " +
                    "Refusing to assemble an unsigned or differently signed release APK.",
            )
        }
    }
}

tasks.configureEach {
    val packagesRelease = name == "assembleRelease" ||
        name == "bundleRelease" ||
        name.startsWith("packageRelease") ||
        name.startsWith("signRelease")
    if (packagesRelease && !allowUnsignedRelease) {
        dependsOn(requireReleaseSigning)
    }
}

tasks.register("printFoldcadeIdentity") {
    group = "help"
    description = "Prints versionCode, versionName, and the debug and release application ids."
    val code = android.defaultConfig.versionCode
        ?: throw GradleException("versionCode is not set in defaultConfig")
    val name = android.defaultConfig.versionName?.trim().orEmpty().ifEmpty {
        throw GradleException("versionName is not set in defaultConfig")
    }
    val releaseId = android.defaultConfig.applicationId ?: "app.foldcade"
    val debugSuffix = android.buildTypes.getByName("debug").applicationIdSuffix ?: ""
    val out = layout.buildDirectory.file("foldcade-identity.txt")
    inputs.property("versionCode", code)
    inputs.property("versionName", name)
    inputs.property("releaseApplicationId", releaseId)
    inputs.property("debugSuffix", debugSuffix)
    outputs.file(out)
    doLast {
        val text = buildString {
            appendLine("versionCode=$code")
            appendLine("versionName=$name")
            appendLine("releaseApplicationId=$releaseId")
            appendLine("debugApplicationId=$releaseId$debugSuffix")
        }
        out.get().asFile.writeText(text)
        print(text)
    }
}

dependencies {
    implementation(project(":api"))
    implementation(project(":language"))
    implementation(project(":host"))
    implementation(project(":plugins:azahar"))
    implementation(project(":plugins:local-folder"))
    implementation(project(":plugins:melonds"))
    implementation(project(":romm"))
    implementation(project(":plugins:romm"))
    implementation(libs.androidx.core)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.media3.exoplayer)
    testImplementation(libs.junit)
}

// Debug and release both merge these assets, so assembleDebug and assembleRelease
// package the loop. CI renders inside the job. -PfoldcadeHomeMusicAssets is an
// optional local copy of an already-rendered music/ directory.
val prebuiltHomeMusic = providers.gradleProperty("foldcadeHomeMusicAssets")
val renderHomeMusic = tasks.register<RenderHomeMusicTask>("renderHomeMusic") {
    group = "build"
    description = "Render every track in music/tracks/manifest.json, or copy a CI render."
    script.set(rootProject.layout.projectDirectory.file("music/gradle-render.sh"))
    sources.from(
        rootProject.files(
            "music/process.py",
            "music/render.sh",
            "music/check_render.py",
            "music/requirements.txt",
            "music/gradle-render.sh",
            "music/tracks/manifest.json",
        ),
    )
    sources.from(rootProject.fileTree("music/tracks") { include("**/compose.py") })
    prebuiltPath.set(prebuiltHomeMusic.orElse(""))
    if (prebuiltHomeMusic.isPresent) {
        prebuiltFiles.setFrom(rootProject.fileTree(prebuiltHomeMusic.get()))
    } else {
        prebuiltFiles.setFrom()
    }
    assetsDir.set(layout.buildDirectory.dir("generated/homeMusicAssets"))
    previewDir.set(layout.buildDirectory.dir("home-music-preview"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(renderHomeMusic, RenderHomeMusicTask::assetsDir)
    }
}

abstract class RenderHomeMusicTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputFile
    abstract val script: RegularFileProperty

    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val prebuiltPath: Property<String>

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val prebuiltFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val assetsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val previewDir: DirectoryProperty

    @TaskAction
    fun render() {
        val dest = assetsDir.get().asFile
        val preview = previewDir.get().asFile
        dest.deleteRecursively()
        preview.mkdirs()
        val path = prebuiltPath.get()
        if (path.isNotEmpty()) {
            copyPrebuilt(File(path), dest)
            return
        }
        execOperations.exec {
            commandLine(
                "bash",
                script.get().asFile.absolutePath,
                dest.absolutePath,
                preview.absolutePath,
            )
        }.assertNormalExitValue()
    }

    private fun copyPrebuilt(source: File, dest: File) {
        if (!source.isDirectory) {
            throw GradleException("foldcadeHomeMusicAssets is not a directory: ${source.absolutePath}")
        }
        val manifest = File(source, "music/manifest.json")
        val ogg = source.walkTopDown().firstOrNull { it.isFile && it.extension == "ogg" }
        if (!manifest.isFile || ogg == null) {
            throw GradleException(
                "foldcadeHomeMusicAssets must contain music/manifest.json and an ogg: ${source.absolutePath}",
            )
        }
        source.copyRecursively(dest, overwrite = true)
    }
}
