package lang.taxi.lsp.linter

import lang.taxi.lsp.CompilationResult
import org.eclipse.lsp4j.Diagnostic
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI

class LintingService {
    private val linters:List<LintingRule> = listOf(UnusedImport())

    fun computeInsightFor(uri: URI, compilationResult: CompilationResult):List<Diagnostic> {
        return linters.flatMap { it.computeInsightFor(uri,compilationResult) }
    }
    fun provideInsightFor(uri: URI, compilationResult: CompilationResult): Flux<List<Diagnostic>> {
        val linters = linters.map { it.provideInsightFor(uri, compilationResult) }
        return Flux.merge(
                linters
        )
    }
}

interface LintingRule {
    fun computeInsightFor(uri: URI, compilationResult: CompilationResult):List<Diagnostic>
    fun provideInsightFor(uri: URI, compilationResult: CompilationResult): Mono<List<Diagnostic>>
}