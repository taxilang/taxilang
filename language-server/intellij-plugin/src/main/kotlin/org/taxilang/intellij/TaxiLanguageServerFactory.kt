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
         * This expects the JAR to be bundled in the plugin's lib directory
         */
        private fun findLanguageServerJar(): String {
            val pluginClassLoader = this::class.java.classLoader

            // Try to find the JAR in the classpath
            val jarName = "taxi-lang-server-standalone"

            // Get all JAR URLs from the classloader
            pluginClassLoader.getResource("META-INF/plugin.xml")?.let { pluginXmlUrl ->
                val pluginPath = pluginXmlUrl.path.substringBefore("!/")
                val pluginDir = File(pluginPath).parentFile.parentFile // Go up from classes to plugin root

                // Look for the server JAR in the lib directory
                val libDir = File(pluginDir, "lib")
                if (libDir.exists()) {
                    libDir.listFiles { file ->
                        file.name.contains(jarName) && file.extension == "jar"
                    }?.firstOrNull()?.let { serverJar ->
                        return serverJar.absolutePath
                    }
                }
            }

            // Alternative: Look in the plugin's installation directory
            // This works when the plugin is installed
            try {
                val urls = (pluginClassLoader as? java.net.URLClassLoader)?.urLs
                urls?.forEach { url ->
                    if (url.path.contains(jarName) && url.path.endsWith(".jar")) {
                        val jarPath = url.path.removePrefix("file:")
                        return jarPath
                    }
                }
            } catch (e: Exception) {
                logger.debug("Could not search URLClassLoader", e)
            }

            throw IllegalStateException(
                "Could not find Taxi language server JAR. " +
                "Please ensure taxi-lang-server-standalone JAR is bundled with the plugin."
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
