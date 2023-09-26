package lang.taxi.lsp.completion

import com.google.common.cache.CacheBuilder
import lang.taxi.TaxiDocument
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat

typealias TaxiDocHashCode = Int

class FunctionCompletionProvider {
   private val cache = CacheBuilder
      .newBuilder()
      .maximumSize(1)
      .build<TaxiDocHashCode, List<CompletionItem>>()

   fun buildFunctions(schema: TaxiDocument, decorators: List<CompletionDecorator>): List<CompletionItem> {
      return cache.get(schema.hashCode()) {
         schema.functions.map { function ->
            val qualifiedName = function.toQualifiedName()
            val functionName = qualifiedName.typeName
            CompletionItem(functionName).apply {
               this.documentation = function.typeDoc.toMarkup()
               this.kind = CompletionItemKind.Function
               this.insertText = "$functionName($0)"
               this.insertTextFormat = InsertTextFormat.Snippet
            }.decorate(decorators, qualifiedName, function)
         }
      }
   }
}
