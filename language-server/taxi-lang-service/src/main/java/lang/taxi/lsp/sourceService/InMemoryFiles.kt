package lang.taxi.lsp.sourceService

import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier

fun inmemoryUri(name: String) = "inmemory://$name"
fun inMemoryIdentifier(modelName: String): TextDocumentIdentifier = TextDocumentIdentifier(inmemoryUri(modelName))
fun inMemoryVersionedId(modelName: String, version: Int = 1): VersionedTextDocumentIdentifier =
   VersionedTextDocumentIdentifier(
      inmemoryUri(modelName), version
   )

fun isWebIdeUri(uri: String): Boolean {
   return uri.startsWith("inmemory://") // Older versions of Monaco
           || uri.startsWith("file:///web/sandbox")
}
