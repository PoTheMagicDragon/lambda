/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.*

val modId: String by project
val mavenGroup: String by project
val modVersion: String by project
val minecraftVersion: String by project
val yarnMappings: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project
val kotlinFabricVersion: String by project
val pngEncoderVersion: String by project
val discordIPCVersion: String by project
val classGraphVersion: String by project
val kotlinVersion: String by project
val ktorVersion: String by project
val jacksonVersion: String by project
val mockkVersion: String by project
val spairVersion: String by project
val lwjglVersion: String by project
val sodiumVersion: String by project
val litematicaVersion: String by project
val maLiLibVersion: String by project
val composeVersion: String by project
val skikoVersion: String by project

// Skiko publishes its natives as one artifact per platform, and none of them are pulled in
// transitively by Compose. Dev runs only need the host's, so resolve that here.
// TODO: production jars need every target bundled, or downloaded at runtime.
val skikoTarget = run {
    val osName = System.getProperty("os.name").lowercase()
    val archName = System.getProperty("os.arch").lowercase()

    val os = when {
        osName.startsWith("windows") -> "windows"
        osName.startsWith("mac") || osName.contains("darwin") -> "macos"
        osName.contains("linux") -> "linux"
        else -> throw GradleException("No skiko natives for host OS '$osName'")
    }
    val arch = when (archName) {
        "x86_64", "amd64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> throw GradleException("No skiko natives for host architecture '$archName'")
    }

    "$os-$arch"
}

val libs = file("libs")
val targets = listOf("fabric.mod.json")
val replacements = file("gradle.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }
}.map { (k, v) -> k.toString() to v.toString() }.toMap()

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.dokka") version "2.1.0"
    id("fabric-loom") version "1.16-SNAPSHOT"
    id("com.gradleup.shadow") version "9.3.0"
    id("maven-publish")
}

group = mavenGroup
version = modVersion

base.archivesName = modId

// We need to force it using lwjgl 3.3.3 because of 3.3.4 poor support for Wayland protocol
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.lwjgl") {
            useVersion(lwjglVersion)
        }
        // The native artifact and the classes Compose pulls in transitively must agree,
        // or skiko fails to find its library at runtime
        if (requested.group == "org.jetbrains.skiko") {
            useVersion(skikoVersion)
        }
    }
}

repositories {
    mavenLocal() // Allow the use of local repositories
    maven("https://maven.lambda-client.org/releases")
    maven("https://maven.2b2t.vc/releases") // Baritone
    maven("https://jitpack.io") // KDiscordIPC
    maven("https://api.modrinth.com/maven")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") // Compose Multiplatform
    google() // AndroidX dependencies (transitive from Compose)
    mavenCentral()

    // Allow the use of local libraries
    flatDir {
        dirs(libs)
    }
}

fabricApi {
    @Suppress("UnstableApiUsage")
    configureTests {
        modId = "${base.archivesName}-tests"
        eula = true
        createSourceSet = true

        enableGameTests = false
        enableClientGameTests = true
        clearRunDirectory = false
    }
}

loom {
    accessWidenerPath = file("src/main/resources/$modId.accesswidener")

    // Apply access wideners transitively (other mods)
    enableTransitiveAccessWideners = true

    runs {
        all {
            property("lambda.dev", "youtu.be/RYnFIRc0k6E")

            property("org.lwjgl.util.Debug", "true")
            property("org.lwjgl.util.DebugLoader", "true")
            //property("org.lwjgl.util.DebugAllocator", "true")
            //property("org.lwjgl.util.DebugAllocator.fast", "true")
            property("org.lwjgl.util.DebugStack", "true")
            property("org.lwjgl.util.DebugFunctions", "true")
            property("mixin.debug.export", "true")

            // Skia refuses to initialise on macOS without both of these
            if (skikoTarget.startsWith("macos")) {
                property("skiko.renderApi", "OPENGL")
                property("skiko.macos.opengl.enabled", "true")
            }

            vmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:+CreateCoredumpOnCrash")
            programArgs("--username", "Steve", "--uuid", "8667ba71b85a4004af54457a9734eed7", "--accessToken", "****")
        }
    }
}

val includeLib: Configuration by configurations.creating
val includeMod: Configuration by configurations.creating
val shadowLib: Configuration by configurations.creating { isCanBeConsumed = false }
val shadowMod: Configuration by configurations.creating { isCanBeConsumed = false }

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        include(it)
    }

    includeMod.dependencies.forEach {
        modImplementation(it)
        include(it)
    }

    shadowLib.dependencies.forEach {
        implementation(it)
    }

    shadowMod.dependencies.forEach {
        modImplementation(it)
    }
}

dependencies {
    // Read this if you'd like to understand the gradle dependency hell
    // https://medium.com/@nagendra.raja/understanding-configurations-and-dependencies-in-gradle-ad0827619501

    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$minecraftVersion+$yarnMappings:v2")

    // Fabric
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion+$minecraftVersion")
    // Explicit dependency on fabric-renderer-indigo for mixin access to internal classes
    modCompileOnly("net.fabricmc.fabric-api:fabric-renderer-indigo:4.1.4+1af5c5a75f")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinFabricVersion.$kotlinVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("io.github.classgraph:classgraph:${classGraphVersion}")
    includeLib("com.github.emyfops:KDiscordIPC:$discordIPCVersion")
    includeLib("com.pngencoder:pngencoder:$pngEncoderVersion")

    includeLib("com.lambda:lambda-imgui-java-binding:$spairVersion")
    includeLib("com.lambda:lambda-imgui-java-lwjgl3:$spairVersion")
    includeLib("com.lambda:lambda-imgui-java-natives-windows:$spairVersion")
    includeLib("com.lambda:lambda-imgui-java-natives-linux:$spairVersion")
    includeLib("com.lambda:lambda-imgui-java-natives-macos:$spairVersion")

    // Ktor
    includeLib("io.ktor:ktor-client-core:$ktorVersion")
    shadowLib("io.ktor:ktor-client-cio:$ktorVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }
    includeLib("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    includeLib("io.ktor:ktor-serialization-gson:$ktorVersion")
    includeLib("com.fasterxml.jackson.core:jackson-annotations:2.21")
    includeLib("tools.jackson.core:jackson-core:$jacksonVersion")
    includeLib("tools.jackson.core:jackson-databind:$jacksonVersion")
    includeLib("tools.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // Add mods
    modImplementation("com.github.rfresh2:baritone-fabric:$minecraftVersion-SNAPSHOT")
    modCompileOnly("maven.modrinth:sodium:$sodiumVersion")
    modCompileOnly("maven.modrinth:malilib:$maLiLibVersion")
    modCompileOnly("maven.modrinth:litematica:$litematicaVersion")

    shadowLib("org.jetbrains.compose.runtime:runtime-desktop:$composeVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.jetbrains.skiko")
    }
    shadowLib("org.jetbrains.compose.ui:ui-desktop:$composeVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.jetbrains.skiko")
    }
    shadowLib("org.jetbrains.compose.foundation:foundation-desktop:$composeVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.jetbrains.skiko")
    }
    shadowLib("org.jetbrains.compose.material3:material3-desktop:$composeVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.jetbrains.skiko")
    }

    shadowLib("org.jetbrains.skiko:skiko-awt-runtime-$skikoTarget:$skikoVersion")

    implementation(compose.components.resources)

	// DevLogin
	modRuntimeOnly("com.ptsmods:devlogin:3.5")

    // Test implementations
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:${mockkVersion}")

    // Finish the configuration
    setupConfigurations()
}

compose.resources {
    customDirectory(
        "main",
        provider { layout.projectDirectory.dir("src/main/resources/assets/lambda/compose/") }
    )
    packageOfResClass = "com.lambda.newui"
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    }

    shadowJar {
        archiveClassifier = "dev-shadow"
        archiveVersion = "$modVersion+$minecraftVersion"
        configurations = listOf(shadowLib, shadowMod)
    }

    remapJar {
        dependsOn(shadowJar)

        inputFile = shadowJar.get().archiveFile
        archiveVersion = "$modVersion+$minecraftVersion"
    }

    processResources {
        filesMatching(targets) { expand(replacements) }

        // Forces the task to always run
        outputs.upToDateWhen { false }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xconsistent-data-class-copy-visibility", "-Xannotation-default-target=param-property")
    }

    jvmToolchain(21)
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

publishing {
    val publishType = project.findProperty("mavenType").toString()
    val isSnapshots = publishType == "snapshots"
    val mavenUrl = if (isSnapshots) "https://maven.lambda-client.org/snapshots" else "https://maven.lambda-client.org/releases"
    val mavenVersion =
        if (isSnapshots) "$modVersion+$minecraftVersion-SNAPSHOT"
        else "$modVersion+$minecraftVersion"

	publications {
        create<MavenPublication>("maven") {
            groupId = mavenGroup
            artifactId = modId
            version = mavenVersion

            from(components["java"])
        }
    }

    repositories {
        maven(mavenUrl) {
            name = "lambda-maven"

            credentials {
                username = project.findProperty("mavenUsername").toString()
                password = project.findProperty("mavenPassword").toString()
            }


            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}