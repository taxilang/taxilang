package org.taxilang.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Factory for creating Taxi language server instances
 */
class TaxiLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        return TaxiLanguageServerConnectionProvider(project)
    }

    override fun createLanguageClient(project: Project): LanguageClientImpl {
        return TaxiLanguageClient(project)
    }

    /**
     * Connection provider that launches the Taxi language server
     */
    private class TaxiLanguageServerConnectionProvider(
        private val project: Project
    ) : StreamConnectionProvider {

        private val logger = Logger.getInstance(TaxiLanguageServerConnectionProvider::class.java)
        private var process: Process? = null

        override fun start() {
            try {
                val javaExecutable = findJavaExecutable()
                val serverJar = findLanguageServerJar()

                logger.info("Starting Taxi Language Server")
                logger.info("Java: $javaExecutable")
                logger.info("Server JAR: $serverJar")

                val commands = mutableListOf(
                    javaExecutable,
                    "-jar",
                    serverJar,
                    "typeChecker=ENABLED"  // Default to enabled type checking
                )

                val processBuilder = ProcessBuilder(commands)
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)

                process = processBuilder.start()

                logger.info("Taxi Language Server started successfully")
            } catch (e: Exception) {
                logger.error("Failed to start Taxi Language Server", e)
                throw e
            }
        }

        override fun getInputStream(): InputStream? {
            return process?.inputStream
        }

        override fun getOutputStream(): OutputStream? {
            return process?.outputStream
        }

        override fun getErrorStream(): InputStream? {
            return process?.errorStream
        }

        override fun stop() {
            process?.let {
                logger.info("Stopping Taxi Language Server")
                it.destroy()

                // Wait for graceful shutdown
                try {
                    it.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    logger.warn("Timeout waiting for language server to stop")
                }

                // Force kill if still alive
                if (it.isAlive) {
                    logger.warn("Force killing Taxi Language Server")
                    it.destroyForcibly()
                }
            }
            process = null
        }

        /**
         * Find the Java executable to use for launching the language server
         */
        private fun findJavaExecutable(): String {
            // Try JAVA_HOME first
            System.getenv("JAVA_HOME")?.let { javaHome ->
                val javaExe = if (System.getProperty("os.name").lowercase().contains("windows")) {
                    File(javaHome, "bin/java.exe")
                } else {
                    File(javaHome, "bin/java")
                }

                if (javaExe.exists() && javaExe.canExecute()) {
                    return javaExe.absolutePath
                }
            }

            // Fallback to 'java' on PATH
            return "java"
        }

        /**
         * Find the Taxi language server JAR
         * The JAR is bundled in the plugin's resources
         */
        private fun findLanguageServerJar(): String {
            val classLoader = this::class.java.classLoader

            // Try to find the JAR as a bundled resource
            val resourcePath = "languageServer/taxi-language-server.jar"
            val resourceUrl = classLoader.getResource(resourcePath)

            if (resourceUrl != null) {
                logger.info("Found language server JAR at: $resourceUrl")

                // If it's a JAR URL (jar:file:/path/to/plugin.jar!/languageServer/taxi-language-server.jar)
                // we need to extract it to a temp location
                if (resourceUrl.protocol == "jar") {
                    val tempDir = File(System.getProperty("java.io.tmpdir"), "taxi-language-server")
                    tempDir.mkdirs()
                    val tempJar = File(tempDir, "taxi-language-server.jar")

                    // Only extract if not already present or outdated
                    if (!tempJar.exists() || tempJar.length() == 0L) {
                        logger.info("Extracting language server JAR to: ${tempJar.absolutePath}")
                        classLoader.getResourceAsStream(resourcePath)?.use { input ->
                            tempJar.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    return tempJar.absolutePath
                } else {
                    // Direct file access (e.g., in development mode)
                    val jarPath = resourceUrl.path.removePrefix("file:")
                    return jarPath
                }
            }

            // Fallback: Look in the plugin directory (for development/sandbox mode)
            classLoader.getResource("META-INF/plugin.xml")?.let { pluginXmlUrl ->
                val pluginPath = pluginXmlUrl.path.substringBefore("!/")
                val pluginDir = File(pluginPath).parentFile

                // Look in resources directory
                val resourcesDir = File(pluginDir, "languageServer")
                if (resourcesDir.exists()) {
                    val serverJar = File(resourcesDir, "taxi-language-server.jar")
                    if (serverJar.exists()) {
                        logger.info("Found language server JAR in plugin directory: ${serverJar.absolutePath}")
                        return serverJar.absolutePath
                    }
                }
            }

            throw IllegalStateException(
                "Could not find Taxi language server JAR. " +
                        "Expected location: $resourcePath in plugin resources. " +
                        "Please ensure the language server was built and bundled with the plugin."
            )
        }
    }

    /**
     * Custom language client for Taxi
     */
    private class TaxiLanguageClient(project: Project) : LanguageClientImpl(project) {
        // Currently using default implementation
        // Can be extended to handle custom LSP notifications/requests
    }
}
