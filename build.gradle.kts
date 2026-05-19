plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = providers.gradleProperty("pluginGroup").orElse("com.kamini").get()
version = providers.gradleProperty("pluginVersion").orElse("1.0.0").get()

// ── Java 17 toolchain (canonical approach for Kotlin + IntelliJ plugins) ──
kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    // OkHttp + Gson are not bundled with the IntelliJ platform — ship them
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // kotlinx-coroutines-core and kotlinx-coroutines-swing are already bundled
    // with the IntelliJ platform — declaring them explicitly causes conflicts.

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

// The Kotlin stdlib is auto-added by the Kotlin Gradle plugin.
// Tell IntelliJ plugin verifier not to treat it as a conflict.
configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

intellij {
    version.set("2023.3")
    type.set("IC")

    plugins.set(
        listOf(
            "com.intellij.java",
            "org.jetbrains.kotlin"
        )
    )

    downloadSources.set(true)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("")
    }

    buildSearchableOptions {
        enabled = false
    }

    // ── Marketplace publishing ──────────────────────────────────────────────
    // Token is read from ~/.gradle/gradle.properties (intellijPublishToken=...)
    // or from the INTELLIJ_PUBLISH_TOKEN environment variable.
    // Never hardcode the token here or commit it to source control.
    publishPlugin {
        token.set(
            providers.gradleProperty("intellijPublishToken")
                .orElse(providers.environmentVariable("INTELLIJ_PUBLISH_TOKEN"))
                .orElse("")
        )
    }
}