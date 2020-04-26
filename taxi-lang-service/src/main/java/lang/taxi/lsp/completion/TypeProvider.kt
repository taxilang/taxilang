package lang.taxi.lsp.completion

import lang.taxi.TaxiDocument
import lang.taxi.types.Documented
import lang.taxi.types.PrimitiveType
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import java.util.concurrent.atomic.AtomicReference

class TypeProvider(val taxiDocument: AtomicReference<TaxiDocument>) {
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
    fun getTypes(): List<CompletionItem> {
        val types = taxiDocument.get()?.types ?: emptySet()
        val completionItems = types.map { type ->
            val name = type.toQualifiedName()
            val doc = if (type is Documented) {
                type.typeDoc
            } else null
            CompletionItem(name.typeName).apply {
                kind = CompletionItemKind.Class
                insertText = type.toQualifiedName().typeName
                detail = listOfNotNull(type.qualifiedName, doc).joinToString("\n")
            }
        }
        return completionItems + primitives
    }
}