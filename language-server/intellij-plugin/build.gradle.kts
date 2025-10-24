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
    // Reference the Taxi language server JAR from Maven local repository
    // This requires running: ./mvnw install -pl language-server/taxi-lang-server-standalone
    implementation("org.taxilang:taxi-lang-server-standalone:${version}") {
        // Exclude LSP4J to avoid conflicts with LSP4IJ's bundled version
        exclude(group = "org.eclipse.lsp4j")
    }
}

// Configure Gradle IntelliJ Plugin
intellij {
    pluginName.set(providers.gradleProperty("pluginName").get())
    version.set(providers.gradleProperty("platformVersion").get())
    type.set(providers.gradleProperty("platformType").get())

    // Plugin Dependencies
    plugins.set(listOf("com.redhat.devtools.lsp4ij:0.6.0"))
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

    // Task to verify that the language server JAR is available
    register("verifyLanguageServer") {
        doLast {
            val jarFound = configurations.runtimeClasspath.get().any {
                it.name.contains("taxi-lang-server-standalone")
            }
            if (!jarFound) {
                throw GradleException(
                    """
                    |
                    |Taxi Language Server JAR not found!
                    |Please build the language server first:
                    |  cd ../..
                    |  ./mvnw install -pl language-server/taxi-lang-server-standalone
                    |
                    """.trimMargin()
                )
            }
            println("✓ Taxi Language Server JAR found in dependencies")
        }
    }

    // Make build depend on verification
    build {
        dependsOn("verifyLanguageServer")
    }
}
