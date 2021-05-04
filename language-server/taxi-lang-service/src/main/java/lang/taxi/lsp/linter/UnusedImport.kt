package lang.taxi.lsp.linter

import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.actions.RemoveUnusedImport
import lang.taxi.lsp.utils.asLineRange
import lang.taxi.lsp.utils.asRange
import lang.taxi.types.SourceNames
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import reactor.core.publisher.Mono
import java.net.URI

class UnusedImport : LintingRule {
    override fun computeInsightFor(uri: URI, compilationResult: CompilationResult):List<Diagnostic> {
        val compiler = compilationResult.compiler
        val sourceName = SourceNames.normalize(uri.toString())
        val importedTypes = compiler.importTokensInSource(sourceName)
        val usedTypeNames = compilationResult.compiler.usedTypedNamesInSource(uri.toString())
        val messages = importedTypes.filter { !usedTypeNames.contains(it.first) }
                .map { (name, unusedImportToken) ->
                    val range = unusedImportToken.asLineRange()
                    Diagnostic(
                            range,
                            "Import ${name.fullyQualifiedName} is not used in this file",
                            DiagnosticSeverity.Information,
                            "Compiler",
                            RemoveUnusedImport.ACTION_CODE
                    )
                }
        return messages
    }
    override fun provideInsightFor(uri: URI, compilationResult: CompilationResult): Mono<List<Diagnostic>> {
       return Mono.create {  sink ->
           sink.success(computeInsightFor(uri, compilationResult))
       }
    }

}