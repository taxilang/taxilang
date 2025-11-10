import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.10.4"
}

// Read version from root pom.xml
fun getMavenVersion(): String {
    val pomFile = file("../../pom.xml")
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc: Document = builder.parse(pomFile)
    doc.documentElement.normalize()

    val versionNodes = doc.getElementsByTagName("version")
    for (i in 0 until versionNodes.length) {
        val node = versionNodes.item(i)
        // Get the first version tag (which should be the project version)
        if (node.parentNode.nodeName == "project") {
            return node.textContent.trim()
        }
    }
    throw GradleException("Could not find version in pom.xml")
}

val mavenVersion = getMavenVersion()

group = providers.gradleProperty("pluginGroup").get()
version = mavenVersion  // Use Maven version

repositories {
    mavenCentral()
    mavenLocal()

    // Required for IntelliJ Platform Gradle Plugin 2.x
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Add the language server as a direct dependency (will run in-process)
    // Exclude LSP4J to use LSP4IJ's bundled version and avoid conflicts
    // Use Maven version for dependencies
    implementation("org.taxilang:taxi-lang-service:$mavenVersion") {
        exclude(group = "org.eclipse.lsp4j")
    }

    // We also need the standalone module for its factories and lifecycle handlers
    implementation("org.taxilang:taxi-lang-server-standalone:$mavenVersion") {
        exclude(group = "org.eclipse.lsp4j")
    }

   implementation("org.antlr:antlr4-intellij-adaptor:0.1")

    // IntelliJ Platform dependencies (2.x style)
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin dependencies
        plugin("com.redhat.devtools.lsp4ij:0.18.0")

        // Required for running tests
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Plugin verifier for compatibility testing
        pluginVerifier()

        // Bundled plugins if needed (only add if not empty)
        val bundledPluginsList = providers.gradleProperty("platformBundledPlugins").orElse("").get()
        if (bundledPluginsList.isNotBlank()) {
            bundledPlugins(bundledPluginsList.split(',').map(String::trim))
        }
    }
}

// Configure IntelliJ Platform Plugin 2.x
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = provider { mavenVersion }  // Use Maven version

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
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

    // Task to print version info for debugging
    register("printVersion") {
        doLast {
            println("Maven version: $mavenVersion")
            println("Plugin version: ${project.version}")
        }
    }
}
