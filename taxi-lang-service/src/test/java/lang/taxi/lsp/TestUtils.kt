package lang.taxi.lsp

import com.google.common.io.Resources
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import java.nio.file.Path
import java.nio.file.Paths

fun documentServiceFor(rootResourcePath: String): Pair<TaxiTextDocumentService, Path> {
    val service = TaxiTextDocumentService(TaxiCompilerService())

    // The initial state has a compiler error,
    // that financial-terms.taxi contains "namesapce" instead of "namespace"
    val workspaceUri = Resources.getResource(rootResourcePath)
    val workspaceRoot = Paths.get(workspaceUri.toURI())
    service.initialize(InitializeParams().apply {
        rootUri = workspaceUri.toString()
    })
    return service to workspaceRoot
}

fun Path.versionedDocument(name: String, version: Int = 1): VersionedTextDocumentIdentifier {
    return VersionedTextDocumentIdentifier(
            this.resolve(name).toUri().toString(),
            version
    )
}

fun Path.document(name: String): TextDocumentIdentifier {
    return TextDocumentIdentifier(this.resolve(name).toUri().toString())
}
