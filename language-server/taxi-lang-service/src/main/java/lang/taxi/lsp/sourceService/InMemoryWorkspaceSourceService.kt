package lang.taxi.lsp.sourceService

import lang.taxi.packages.TaxiPackageProject
import lang.taxi.sources.SourceCode
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier

/**
 * A source service that takes some initial state as a string, and
 * doesn't load anything directly from disk.
 * Useful in tests
 */
class InMemoryWorkspaceSourceService(
   private val sources: List<SourceCode>
) : WorkspaceSourceService {
   companion object {
      fun from(vararg source: String): InMemoryWorkspaceSourceService {
         return InMemoryWorkspaceSourceService(source.mapIndexed { index, s ->
            SourceCode("Schema$index.taxi", s)
         })
      }
   }

   override fun loadSources(): Sequence<SourceCode> {
      return sources.asSequence()
   }

   override fun loadProject(): TaxiPackageProject? {
      return null
   }
}

fun inmemoryUri(name: String) = "inmemory://$name"
fun inMemoryIdentifier(modelName: String): TextDocumentIdentifier = TextDocumentIdentifier(inmemoryUri(modelName))
fun inMemoryVersionedId(modelName: String, version: Int = 1): VersionedTextDocumentIdentifier =
   VersionedTextDocumentIdentifier(
      inmemoryUri(modelName), version
   )
