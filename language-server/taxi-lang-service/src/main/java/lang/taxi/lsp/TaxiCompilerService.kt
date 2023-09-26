package lang.taxi.lsp

import com.google.common.base.Stopwatch
import lang.taxi.*
import lang.taxi.linter.toLinterRules
import lang.taxi.lsp.completion.TypeProvider
import lang.taxi.lsp.sourceService.WorkspaceSourceService
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.packages.utils.log
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.eclipse.lsp4j.TextDocumentIdentifier
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class TaxiCompilerService(val compilerConfig: CompilerConfig = CompilerConfig()) {
   private var taxiProjectConfig: TaxiPackageProject? = null
   private lateinit var workspaceSourceService: WorkspaceSourceService
   private val sources: MutableMap<URI, String> = mutableMapOf()
   private val charStreams: MutableMap<URI, CharStream> = mutableMapOf()

   private val lastSuccessfulCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
   private val lastCompilationResult: AtomicReference<CompilationResult> = AtomicReference();

   private val tokenCache: CompilerTokenCache = CompilerTokenCache()
   val typeProvider = TypeProvider(lastSuccessfulCompilationResult, lastCompilationResult)

   private val compileTriggerSink = Sinks.many().unicast().onBackpressureBuffer<CompilationTrigger>()
   val compilationResults: Flux<CompilationResult>

   fun lastSuccessfulCompilation(): CompilationResult? {
      return lastCompilationResult.get()
   }

   fun lastCompilation(): CompilationResult? {
      return lastCompilationResult.get()
   }

   init {
      compilationResults = compileTriggerSink.asFlux()
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
   }

   fun triggerAsyncCompilation(trigger: CompilationTrigger): Sinks.EmitResult {
      return this.compileTriggerSink.tryEmitNext(trigger)
   }

   val sourceCount: Int
      get() {
         return sources.size
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

   fun reloadSourcesWithoutCompiling() {
      this.sources.clear()
      this.charStreams.clear()
      this.taxiProjectConfig = this.workspaceSourceService.loadProject()
      this.workspaceSourceService.loadSources()
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
    * If after that compilation, the uri still isn't present, an exception is thrown.
    */
   fun getOrComputeLastCompilationResult(uriToAssertIsPreset: String): CompilationResult {
      val lastResult = getOrComputeLastCompilationResult()
      if (lastResult.containsTokensForSource(uriToAssertIsPreset)) {
         return lastResult
      }
      // The requested uri wasn't present - maybe we're waiting on a compilation.
      val forcedCompilationResult = compile()
      if (forcedCompilationResult.containsTokensForSource(uriToAssertIsPreset)) {
         return forcedCompilationResult
      } else {
         throw RuntimeException("The requested uri $uriToAssertIsPreset is not known to the compiler")
      }
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
