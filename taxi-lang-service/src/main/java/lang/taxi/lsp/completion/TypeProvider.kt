package lang.taxi.lsp.completion

import lang.taxi.lsp.CompilationResult
import lang.taxi.types.Documented
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import java.util.concurrent.atomic.AtomicReference

class TypeProvider(private val lastSuccessfulCompilationResult: AtomicReference<CompilationResult>,
                   private val lastCompilationResult: AtomicReference<CompilationResult>
) {
    val primitives = PrimitiveType.values()
            .map { type ->
                CompletionItem(type.declaration).apply {
                    kind = CompletionItemKind.Class
                    insertText = type.declaration
                    detail = type.typeDoc
                }
            }

    /**
     * Returns all types, including Taxi primitives
     */
    fun getTypes(decorators: List<CompletionDecorator> = emptyList()): List<CompletionItem> {
        val compiledDoc = lastSuccessfulCompilationResult.get()?.document
        val typeNames = lastCompilationResult.get()?.compiler?.declaredTypeNames() ?: emptyList()
        val completionItems = typeNames.map { name ->
            val type = if (compiledDoc?.containsType(name.typeName) == true) { // == true because of nulls
                compiledDoc.type(name.typeName)
            } else {
                null
            }
            val doc = if (type is Documented) {
                type.typeDoc
            } else null
            val completionItem = CompletionItem(name.typeName).apply {
                kind = CompletionItemKind.Class
                insertText = name.typeName
                detail = listOfNotNull(name, doc).joinToString("\n")
            }

            decorators.fold(completionItem) { itemToDecorate, decorator -> decorator.decorate(name, type, itemToDecorate) }
        }
        return completionItems + primitives
    }
}

interface CompletionDecorator {
    fun decorate(typeName: QualifiedName, type: Type?, completionItem: CompletionItem): CompletionItem
}