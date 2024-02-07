package lang.taxi.lsp

import com.google.common.base.Stopwatch
import lang.taxi.CompilationError
import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.CompilerConfig
import lang.taxi.CompilerTokenCache
import lang.taxi.linter.toLinterRules
import lang.taxi.lsp.completion.TypeCompletionBuilder
import lang.taxi.lsp.parser.TokenInjectingErrorStrategy
import lang.taxi.lsp.sourceService.WorkspaceSourceService
import lang.taxi.lsp.utils.Ranges
import lang.taxi.packages.MalformedTaxiConfFileException
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.types.SourceNames
import lang.taxi.utils.log
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.eclipse.aether.collection.DependencyCollectionException
import org.eclipse.aether.resolution.ArtifactResolutionException
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.jsonrpc.messages.Either
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.readText

class TaxiCompilerService(
   private val compilerConfig: CompilerConfig = CompilerConfig(),
) {
   private var taxiProjectConfig: TaxiPackageProject? = null
   private lateinit var workspaceSourceService: WorkspaceSourceService
   private val sources: MutableMap<URI, String> = mutableMapOf()
   private val charStreams: MutableMap<URI, CharStream> = mutableMapOf()

   private val lastSuccessfulCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
   private val lastCompilationResult: AtomicReference<CompilationResult> = AtomicReference();

   private val tokenCache: CompilerTokenCache = CompilerTokenCache(
      listOf(TokenInjectingErrorStrategy.parserCustomizer)
   )
   val typeCompletionBuilder = TypeCompletionBuilder()

   private val compileTriggerSink = Sinks.many().unicast().onBackpressureBuffer<CompilationTrigger>()
   private val taxiConfDiagnostics = Sinks.many().unicast().onBackpressureBuffer<DiagnosticMessagesWrapper>()
   val compilationResults: Flux<DiagnosticMessagesWrapper>

   private val compilationProgressSink = Sinks.many().unicast().onBackpressureBuffer<ProgressParams>()
   val compilationProgressEvents: Flux<ProgressParams> = compilationProgressSink.asFlux()

   fun lastSuccessfulCompilation(): CompilationResult? {
      return lastCompilationResult.get()
   }

   fun lastCompilation(): CompilationResult? {
      return lastCompilationResult.get()
   }

   init {
      val triggeredCompilationResults = compileTriggerSink.asFlux()
         .bufferTimeout(50, Duration.ofMillis(500))
         .map { _ ->
            try {
               compile()
            } catch (e: Exception) {
               val writer = StringWriter()
               val printWriter = PrintWriter(writer)
               e.printStackTrace(printWriter)
               val errorMessage =
                  "An exception was thrown when compiling.  This is a bug in the compiler, and should be reported. \n${e.message} \n$writer"
               log().warn(errorMessage)
               CompilationResult(
                  Compiler(emptyList<CharStream>()), document = null, errors = listOf(
                     CompilationError(
                        0,
                        0,
                        errorMessage
                     )
                  ), countOfSources = 0, duration = Duration.ZERO
               )
            }
         }

      val taxiConfMessages = taxiConfDiagnostics.asFlux()

      // Note: Because this is combineLatest, we need both to emit a message
      // before a result is emitted.
      // If that becomes problematic, consider emitting empty states on startup
      compilationResults = Flux.combineLatest(triggeredCompilationResults, taxiConfMessages) { a, b ->
         FileDiagnosticMessageCollection()
            .fold(a)
            .fold(b)
      }
   }

   fun triggerAsyncCompilation(trigger: CompilationTrigger): Sinks.EmitResult {
      return this.compileTriggerSink.tryEmitNext(trigger)
   }

   fun source(uri: URI): String {
      return this.sources[uri] ?: error("Could not find source with url ${uri.toASCIIString()}")
   }

   fun source(path: String): String {
      val uri = URI.create(SourceNames.normalize(path))
      return source(uri)
   }

   fun source(identifier: TextDocumentIdentifier): String {
      return source(identifier.uri)
   }

   private fun reloadSourcesWithoutCompiling() {
      val token = UUID.randomUUID().toString()
      fun cancelProgress(reason: String) {
         compilationProgressSink.tryEmitNext(
            ProgressParams().apply {
               value = Either.forLeft(WorkDoneProgressEnd().apply {
                  message = reason
               })
               setToken(token)
            }
         )
      }
      compilationProgressSink.tryEmitNext(
         ProgressParams().apply {
            value = Either.forLeft(WorkDoneProgressBegin().apply {
               title = "Updating project"
               cancellable = false
               message = "Resolving dependencies..."
            })
            setToken(token)
         }
      )
      this.sources.clear()
      this.charStreams.clear()
      try {
         this.taxiProjectConfig = this.workspaceSourceService.loadProject()
      } catch (e: MalformedTaxiConfFileException) {
         log().info("Cannot read taxi.conf file: ${e.path} - ${e.message}")
         cancelProgress("taxi.conf file is invalid")
         val message = FileDiagnosticMessage(e.path, e.message, e.lineNumber)
         taxiConfDiagnostics.emitNext(message, Sinks.EmitFailureHandler.FAIL_FAST)
         throw e
      }

      val loadedSources = try {
         val loadedSources = this.workspaceSourceService.loadSources()
         // At this point, the taxi.conf is valid, and all deps have been resolved
         this.taxiProjectConfig?.taxiConfFile?.let { removeTaxiConfErrors(it) }
         loadedSources
      } catch (e: DependencyCollectionException) {
         cancelProgress("Unable to collect requested dependencies: ${e.message}")
         handleTaxiConfException(e)
         this.workspaceSourceService.loadSources()
      } catch (e: ArtifactResolutionException) {
         reportUnresolvableDependencies(e)
         cancelProgress("Unable to resolve requested dependencies: ${e.message}")
         this.workspaceSourceService.loadSources()
      } catch (e: Exception) {
         handleTaxiConfException(e)
         cancelProgress("An error occurred loading the project: ${e.message}")
         this.workspaceSourceService.loadSources()
      }

      loadedSources
         .forEach { sourceCode ->
            // Prefer operating on the path - less chances to screw up
            // the normalization of the URI, which seems to be getting
            // messed up somewhere
            if (sourceCode.path != null) {
               updateSource(sourceCode.path!!.toUri(), sourceCode.content)
            } else {
               updateSource(sourceCode.normalizedSourceName, sourceCode.content)
            }
         }
      compilationProgressSink.tryEmitNext(
         ProgressParams().apply {
            value = Either.forLeft(WorkDoneProgressEnd().apply {
               message = "Project updated successfully"
            })
            setToken(token)
         }
      )

   }

   private fun reportUnresolvableDependencies(e: ArtifactResolutionException) {
      val taxiConfFile = taxiProjectConfig?.taxiConfFile!!
      val taxiConfig = taxiConfFile?.readText()
      val defaultLineNumber = 1;
      val messages = e.results
         .filter { it.exceptions.isNotEmpty() }
         .map { requestedArtifact ->
            val artifactId =
               requestedArtifact.request.artifact.groupId + "/" + requestedArtifact.request.artifact.artifactId
            val range = taxiConfig?.lineSequence()?.indexOfFirst { it.contains(artifactId) }?.let { lineNumber ->
               val line = taxiConfig.lines()[lineNumber]
               val startChar = line.indexOfFirst { !it.isWhitespace() }
               val endChar = line.length - 1
               Range(Position(lineNumber, startChar), Position(lineNumber, endChar))
            } ?: Ranges.fullLine(defaultLineNumber)
            val errorMessage = requestedArtifact.exceptions.joinToString("; ") {
               it.message
                  ?: "Could not resolve ${requestedArtifact.artifact} - An unknown error occurred (${it::class.java.simpleName}"
            }
            errorMessage to range
         }
      reportTaxiConfMessages(FileDiagnosticMessage(taxiConfFile, messages))
   }

   private fun handleTaxiConfException(e: Exception, lineNumber: Int = 1) {
      taxiProjectConfig?.let { project ->
         reportTaxiConfMessages(
            FileDiagnosticMessage(
               project.taxiConfFile!!,
               e.message ?: "An error occurred",
               lineNumber
            )
         )
      }
   }

   private fun removeTaxiConfErrors(path: Path) {
      reportTaxiConfMessages(FileDiagnosticMessage(path, emptyList()))
   }

   private fun reportTaxiConfMessages(message: FileDiagnosticMessage) {
      taxiConfDiagnostics.emitNext(message, Sinks.EmitFailureHandler.FAIL_FAST)
   }

   fun reloadSourcesAndTriggerCompilation(): Sinks.EmitResult {
      reloadSourcesWithoutCompiling()
      return this.triggerAsyncCompilation(CompilationTrigger(null))
   }

   /**
    * Compiles immediately
    * Note that the results are not emitted on the flux, meaning that they
    * are not pushed out to the listening LanguageServerClient
    */
   @Deprecated("Prefer reloadSourcesAndTriggerCompilation")
   fun reloadSourcesAndCompile(): CompilationResult {
      reloadSourcesWithoutCompiling()
      return this.compile()
   }

   private fun updateSource(uri: URI, content: String) {
      this.sources[uri] = content
      this.charStreams[uri] = CharStreams.fromString(content, uri.toASCIIString())
   }

   fun updateSource(path: String, content: String) {
      updateSource(URI.create(SourceNames.normalize(path)), content)
   }

   fun updateSource(identifier: TextDocumentIdentifier, content: String) {
      updateSource(identifier.uri, content)
   }

   fun compile(): CompilationResult {
      val (charStreams, compiler) = buildCompiler()
      val stopwatch = Stopwatch.createStarted()
      val compilationResult = try {

         val (messages, compiled) = compiler.compileWithMessages()

         CompilationResult(compiler, compiled, charStreams.size, stopwatch.elapsed(), messages)
      } catch (e: CompilationException) {
         CompilationResult(compiler, null, charStreams.size, stopwatch.elapsed(), e.errors)
      } catch (e: Exception) {
         log().error("An exception was thrown by the compiler - ${e.message}", e)
         CompilationResult(
            compiler,
            null,
            charStreams.size,
            stopwatch.elapsed(),
            listOf(CompilationError(1, 1, e.message ?: e.localizedMessage, null))
         )
      }
      lastCompilationResult.set(compilationResult)
      if (compilationResult.successful) {
         lastSuccessfulCompilationResult.set(compilationResult)
      }
      return compilationResult
   }

   private fun buildCompiler(): Pair<List<CharStream>, Compiler> {
      val charStreams = this.charStreams.values.toList()
      val configWithTaxiProjectSettings = taxiProjectConfig?.let { taxiConf ->
         compilerConfig.copy(
            linterRuleConfiguration = taxiConf.linter.toLinterRules()
         )
      } ?: this.compilerConfig
      val compiler = Compiler(charStreams, tokenCache = tokenCache, config = configWithTaxiProjectSettings)
      return Pair(charStreams, compiler)
   }

   fun getOrComputeLastCompilationResult(): CompilationResult {
      if (lastCompilationResult.get() == null) {
         compile()
      }
      return lastCompilationResult.get()
   }

   /**
    * Will use the last compilation result if present, and contains
    * the requested uri.
    * If the compilation result doesn't contain the uri, a compilation pass
    * is immediately forced.
    * If after that compilation, the uri still isn't present, a message is logged
    */
   fun getOrComputeLastCompilationResult(uriToAssertIsPreset: String): CompilationResult {
      val lastResult = getOrComputeLastCompilationResult()
      val compilerShouldKnowFile = uriToAssertIsPreset.endsWith("taxi")
      if (lastResult.containsTokensForSource(uriToAssertIsPreset) || !compilerShouldKnowFile) {
         return lastResult
      }
      // The requested uri wasn't present - maybe we're waiting on a compilation.
      val forcedCompilationResult = compile()
      if (!forcedCompilationResult.containsTokensForSource(uriToAssertIsPreset)) {
         log().info("The requested uri $uriToAssertIsPreset is not known to the compiler")
      }
      return forcedCompilationResult
   }

   /**
    * Initialize the compiler service, loading sources from the source service.
    * Note: Because of initialization order implicit in the LSP startup sequences,
    * the source service cannot be injected at creation time.
    */
   fun initialize(sourceService: WorkspaceSourceService) {
      this.workspaceSourceService = sourceService
      reloadSourcesWithoutCompiling()
   }

}
