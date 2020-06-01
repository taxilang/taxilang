package lang.taxi.lsp.gotoDefinition

import lang.taxi.TaxiParser
import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.completion.TypeProvider
import lang.taxi.lsp.completion.uriPath
import lang.taxi.types.CompilationUnit
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

class GotoDefinitionService(private val typeProvider: TypeProvider) {
    fun definition(compilationResult: CompilationResult, params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        val context = compilationResult.compiler.contextAt(params.position.line, params.position.character, params.textDocument.uriPath())
        val compilationUnit = when (context) {
            is TaxiParser.TypeTypeContext -> compilationResult.compiler.getDeclarationSource(context)
            is TaxiParser.EnumSynonymSingleDeclarationContext -> {
                // Drop off the value within the enum for now, we'll navigate to the enum class, but not
                // the value within it.
                val enumName = context.text.split(".").dropLast(1).joinToString(".")
                compilationResult.compiler.getDeclarationSource(enumName, context)
            }
            else -> null
        }
        val location = if (compilationUnit != null) {
            listOf(compilationUnit.toLocation())
        } else {
            emptyList()
        }.toMutableList()
        val either = Either.forLeft<MutableList<out Location>, MutableList<out LocationLink>>(location)
        return CompletableFuture.completedFuture(either)
    }

}


fun CompilationUnit.toLocation(): Location {
    val position = Position(this.location.line - 1, this.location.char)
    return Location(this.source.normalizedSourceName, Range(position, position))
}