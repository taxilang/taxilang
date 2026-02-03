package lang.taxi.lsp

import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import lang.taxi.CompilerConfig
import lang.taxi.lsp.notebook.TaxiNotebookService
import lang.taxi.lsp.workspace.TranspilingWorkspaceSourceService
import lang.taxi.toggles.FeatureToggle
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
        val compilerService = TaxiCompilerService(compilerConfig)

        val taxiLanguageServer = TaxiLanguageServer(
           compilerConfig = compilerConfig,
           compilerService = compilerService,
           lifecycleHandler = ProcessLifecycleHandler,

           // This enables support for transpiling sources from things like Avro, etc.
           // To disable, revert to the default (FileBasedWorkspaceSourceService.Companion.Factory())
           workspaceSourceServiceFactory = TranspilingWorkspaceSourceService.Companion.Factory()
           )

        // Create the notebook service for handling TaxiQL notebook operations
        val notebookService = TaxiNotebookService(compilerService)

        // Create a composite server that implements both LanguageServer and NotebookService
        // This allows the JSONRPC launcher to route requests to the appropriate service
        val compositeServer = TaxiLanguageServerWithNotebooks(taxiLanguageServer, notebookService)

        // Create JSON RPC launcher with the composite server
//        val launcher = LSPLauncher.createServerLauncher(compositeServer, input, outputStream)


       // Add message tracer for debugging
//       val traceWriter = java.io.PrintWriter(java.io.FileWriter("/tmp/taxi-lsp-jsonrpc.log", true))

       val launcher = LSPLauncher.Builder<LanguageClient>()
          .setLocalService(compositeServer)
          .setRemoteInterface(LanguageClient::class.java)
          .setInput(input)
          .setOutput(outputStream)
          .configureGson { builder ->
             GsonCustomizer.configureGson(builder)
          }
//          .traceMessages(traceWriter)
          .create()

        // Get the client that request to launch the LS.
        val client = launcher.remoteProxy

        // Set the client to language server (through the composite wrapper)
        compositeServer.connect(client)

        // Debug logging to file (won't interfere with stdio)
        try {
            java.io.File("/tmp/taxi-lsp-debug.log").appendText("Launcher: About to start listening...\n")
        } catch (e: Exception) { /* ignore */ }

        // Start the listener for JsonRPC
        val startListening: Future<*> = launcher.startListening()

        // Debug logging
        try {
            java.io.File("/tmp/taxi-lsp-debug.log").appendText("Launcher: startListening() returned, now waiting...\n")
        } catch (e: Exception) { /* ignore */ }

        // Get the computed result from LS.
        startListening.get()
    }
}
