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

// Configuration for the language server JAR (bundled as resource, not classpath dependency)
val languageServerJar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    // Bundle the language server JAR as a resource (not on classpath)
    // This requires running: ./mvnw install -pl language-server/taxi-lang-server-standalone
    // Note: We need the "jar-with-dependencies" classifier for the fat JAR with Main-Class manifest
    languageServerJar("org.taxilang:taxi-lang-server-standalone:${version}:jar-with-dependencies") {
        // Exclude transitive dependencies - we only want the JAR
        isTransitive = false
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

    // Task to verify and copy the language server JAR
    val copyLanguageServerJar by registering(Copy::class) {
        from(languageServerJar)
        into("${project.buildDir}/resources/main/languageServer")
        rename { "taxi-language-server.jar" }

        doFirst {
            val jarFiles = languageServerJar.resolvedConfiguration.resolvedArtifacts
            if (jarFiles.isEmpty()) {
                throw GradleException(
                    """
                    |
                    |Taxi Language Server JAR not found!
                    |Looking for: taxi-lang-server-standalone-${version}-jar-with-dependencies.jar
                    |
                    |Please build the language server first:
                    |  cd ../..
                    |  ./mvnw install -pl language-server/taxi-lang-server-standalone
                    |
                    |The JAR should be installed to:
                    |  ~/.m2/repository/org/taxilang/taxi-lang-server-standalone/${version}/
                    |
                    """.trimMargin()
                )
            }
            logger.lifecycle("✓ Copying Taxi Language Server JAR: ${jarFiles.first().file.name}")
        }
    }

    // Ensure all tasks that need resources wait for the language server JAR
    processResources {
        dependsOn(copyLanguageServerJar)
    }

    // Make jar task depend on copying the language server
    jar {
        dependsOn(copyLanguageServerJar)
    }

    // Make IntelliJ-specific tasks depend on the language server JAR
    instrumentedJar {
        dependsOn(copyLanguageServerJar)
    }

    prepareSandbox {
        dependsOn(copyLanguageServerJar)
    }
}
