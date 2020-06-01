package lang.taxi.lsp.hover

import lang.taxi.TaxiParser
import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.completion.uriPath
import lang.taxi.types.Documented
import lang.taxi.types.QualifiedName
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import java.util.concurrent.CompletableFuture

class HoverService {
    fun hover(compilationResult: CompilationResult, lastSuccessfulCompilationResult: CompilationResult?, params: HoverParams): CompletableFuture<Hover> {
        val context = compilationResult.compiler.contextAt(params.position.line, params.position.character, params.textDocument.uriPath())
        val qualifiedName = when (context) {
            is TaxiParser.TypeTypeContext -> compilationResult.compiler.lookupTypeByName(context)
            else -> null
        }

        val content = getTypeDoc(qualifiedName, lastSuccessfulCompilationResult, compilationResult)
        val hover = if (content != null) {
            Hover(content)
        } else {
            Hover(emptyList())
        }
        return CompletableFuture.completedFuture(hover)
    }

    private fun getTypeDoc(qualifiedName: QualifiedName?, lastSuccessfulCompilationResult: CompilationResult?, compilationResult: CompilationResult): MarkupContent? {
        if (qualifiedName != null) {
            val taxiDocument = listOfNotNull(lastSuccessfulCompilationResult?.document,
                    compilationResult.document).firstOrNull()
            if (taxiDocument != null && taxiDocument.containsType(qualifiedName.fullyQualifiedName)) {
                val type = taxiDocument.type(qualifiedName)
                if (type is Documented) {
                    return MarkupContent("markdown", type.typeDoc)
                }
            }
        }
        return null
    }

}