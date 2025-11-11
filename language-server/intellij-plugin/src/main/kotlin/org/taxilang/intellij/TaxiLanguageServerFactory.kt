package org.taxilang.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import lang.taxi.CompilerConfig
import lang.taxi.lsp.TaxiLanguageServer
import lang.taxi.lsp.workspace.TranspilingWorkspaceSourceService
import lang.taxi.toggles.FeatureToggle
import org.eclipse.lsp4j.jsonrpc.Launcher
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future

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
     * Connection provider that runs the Taxi language server in-process
     */
    private class TaxiLanguageServerConnectionProvider(
        private val project: Project
    ) : StreamConnectionProvider {

        private val logger = Logger.getInstance(TaxiLanguageServerConnectionProvider::class.java)

        // Input from client to server
        private val clientToServerInput = PipedInputStream()
        private val clientToServerOutput = PipedOutputStream(clientToServerInput)

        // Output from server to client
        private val serverToClientInput = PipedInputStream()
        private val serverToClientOutput = PipedOutputStream(serverToClientInput)

        private var languageServer: TaxiLanguageServer? = null
        private var launcherFuture: Future<*>? = null

        override fun start() {
            try {
                logger.info("Starting Taxi Language Server in-process")

                // Configure the language server
                val compilerConfig = CompilerConfig(
                    typeCheckerEnabled = FeatureToggle.ENABLED
                )

                // Create the Taxi language server instance
                languageServer = TaxiLanguageServer(
                    compilerConfig = compilerConfig,
                    lifecycleHandler = IntelliJLifecycleHandler,
                    // Enable support for transpiling sources from Avro, OpenAPI, etc.
                    workspaceSourceServiceFactory = TranspilingWorkspaceSourceService.Companion.Factory()
                )

                // Create the LSP4J launcher to handle communication
                val launcher = Launcher.createLauncher(
                    languageServer,
                    org.eclipse.lsp4j.services.LanguageClient::class.java,
                    clientToServerInput,
                    serverToClientOutput,
                    Executors.newCachedThreadPool { runnable ->
                        Thread(runnable, "Taxi LSP Server Thread")
                    },
                    { it } // Message consumer (pass-through)
                )

                // Get the remote proxy (client)
                val client = launcher.remoteProxy

                // Connect the client to the language server
                languageServer?.connect(client)

                // Start listening for JSON-RPC messages
                launcherFuture = launcher.startListening()

                logger.info("Taxi Language Server started successfully in-process")
            } catch (e: Exception) {
                logger.error("Failed to start Taxi Language Server", e)
                throw e
            }
        }

        override fun getInputStream(): InputStream {
            // This is what the client reads (server's output)
            return serverToClientInput
        }

        override fun getOutputStream(): OutputStream {
            // This is what the client writes to (server's input)
            return clientToServerOutput
        }
//
//        override fun getErrorStream(): InputStream? {
//            // No separate error stream for in-process server
//            return null
//        }

        override fun stop() {
            try {
                logger.info("Stopping Taxi Language Server")

                // Shutdown the language server
                languageServer?.shutdown()?.get()
                languageServer?.exit()

                // Close streams
                clientToServerOutput.close()
                clientToServerInput.close()
                serverToClientOutput.close()
                serverToClientInput.close()

                logger.info("Taxi Language Server stopped successfully")
            } catch (e: Exception) {
                logger.warn("Error stopping Taxi Language Server", e)
            } finally {
                languageServer = null
                launcherFuture = null
            }
        }
    }

    /**
     * Lifecycle handler for IntelliJ environment
     */
    private object IntelliJLifecycleHandler : lang.taxi.lsp.LanguageServerLifecycleHandler {
        private val logger = Logger.getInstance("TaxiLanguageServer")

//        override fun onShutdown() {
//            logger.info("Taxi Language Server shutdown requested")
//        }
//
//        override fun onExit() {
//            logger.info("Taxi Language Server exiting")
//            // Don't call System.exit() in IntelliJ - just log
//        }
    }

    /**
     * Custom language client for Taxi
     */
    private class TaxiLanguageClient(project: Project) : LanguageClientImpl(project) {
        // Currently using default implementation
        // Can be extended to handle custom LSP notifications/requests
    }
}
