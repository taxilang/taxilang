package lang.taxi.lsp

import lang.taxi.CompilerConfig
import lang.taxi.toggles.FeatureToggle
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Future
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger


/**
 * Launcher for hello language server.
 */
object Launcher {
    @JvmStatic
    fun main(args: Array<String>) {
        // As we are using system std io channels
        // we need to reset and turn off the logging globally
        // So our client->server communication doesn't get interrupted.
        LogManager.getLogManager().reset()
        val globalLogger: Logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)
        globalLogger.level = Level.OFF

        // start the language server
        val configArgs = args.map {
            val keyValue = it.split("=")
            keyValue[0] to FeatureToggle.valueOf(keyValue[1])
        }.toMap()
        val config = CompilerConfig(
                typeCheckerEnabled = configArgs["typeChecker"] ?: FeatureToggle.DISABLED
        )
        startServer(System.`in`, System.out, config)
    }

    /**
     * Start the language server.
     * @param in System Standard input stream
     * @param outputStream System standard output stream
     * @throws ExecutionException Unable to start the server
     * @throws InterruptedException Unable to start the server
     */
    private fun startServer(input: InputStream, outputStream: OutputStream, compilerConfig: CompilerConfig) {
        // Initialize the HelloLanguageServer
        val taxiLanguageServer = TaxiLanguageServer(
           compilerConfig = compilerConfig,
           lifecycleHandler = ProcessLifecycleHandler)
        // Create JSON RPC launcher for HelloLanguageServer instance.
        val launcher = LSPLauncher.createServerLauncher(taxiLanguageServer, input, outputStream)

        // Get the client that request to launch the LS.
        val client = launcher.remoteProxy

        // Set the client to language server
        taxiLanguageServer.connect(client)

        // Start the listener for JsonRPC
        val startListening: Future<*> = launcher.startListening()

        // Get the computed result from LS.
        startListening.get()
    }
}
