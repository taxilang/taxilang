package lang.taxi.lsp

import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.CompilerConfig
import lang.taxi.CompilerTokenCache
import lang.taxi.lsp.completion.TypeProvider
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

class TaxiCompilerService(val config:CompilerConfig = CompilerConfig()) {
    private val sources: MutableMap<URI, String> = mutableMapOf()
    private val charStreams: MutableMap<URI, CharStream> = mutableMapOf()

    val lastSuccessfulCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
    val lastCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
    private val tokenCache: CompilerTokenCache = CompilerTokenCache()
    val typeProvider = TypeProvider(lastSuccessfulCompilationResult, lastCompilationResult)

    val sourceCount:Int
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

    fun updateSource(uri: URI, content: String) {
        this.sources[uri] = content
        this.charStreams[uri] = CharStreams.fromString(content, uri.toASCIIString())
    }

    fun updateSource(path: String, content: String) {
        updateSource(URI.create(SourceNames.normalize(path)), content)
    }

    fun updateSource(identifier: TextDocumentIdentifier, content: String) {
        updateSource(identifier.uri, content)
    }

    fun compile():CompilationResult {
        val charStreams = this.charStreams.values.toList()

        val compiler = Compiler(charStreams, tokenCache = tokenCache, config = config)

        val compilationResult = try {
            val (messages,compiled) = compiler.compileWithMessages()
            CompilationResult(compiler, compiled, messages)
        } catch (e: CompilationException) {
            CompilationResult(compiler, null, e.errors)
        }
        lastCompilationResult.set(compilationResult)
        if (compilationResult.successful) {
            lastSuccessfulCompilationResult.set(compilationResult)
        }
        return compilationResult
    }
    
    fun getOrComputeLastCompilationResult():CompilationResult {
        if (lastCompilationResult.get() == null) {
            compile()
        }
        return lastCompilationResult.get()
    }

}