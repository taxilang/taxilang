import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Add the language server as a direct dependency (will run in-process)
    // Exclude LSP4J to use LSP4IJ's bundled version and avoid conflicts
    implementation("org.taxilang:taxi-lang-service:${version}") {
        exclude(group = "org.eclipse.lsp4j")
    }

    // We also need the standalone module for its factories and lifecycle handlers
    implementation("org.taxilang:taxi-lang-server-standalone:${version}") {
        exclude(group = "org.eclipse.lsp4j")
    }
}

// Configure Gradle IntelliJ Plugin
intellij {
    pluginName.set(providers.gradleProperty("pluginName").get())
    version.set(providers.gradleProperty("platformVersion").get())
    type.set(providers.gradleProperty("platformType").get())

    // Plugin Dependencies
    plugins.set(listOf("com.redhat.devtools.lsp4ij:0.18.0"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set(providers.gradleProperty("pluginSinceBuild").get())
        untilBuild.set(providers.gradleProperty("pluginUntilBuild").get())
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
