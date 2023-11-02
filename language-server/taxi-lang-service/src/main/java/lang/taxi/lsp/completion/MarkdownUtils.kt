package lang.taxi.lsp.completion

import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.jsonrpc.messages.Either

fun String?.toMarkup(): Either<String, MarkupContent>? {
   return this?.let {
      Either.forRight(MarkupContent("markdown", this))
   }
}

fun String?.toMarkupOrEmpty(): MarkupContent {
   val content = this ?: ""
   return MarkupContent("markdown", content)
}
