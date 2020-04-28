package lang.taxi.lsp

import com.google.common.io.Resources
import com.winterbe.expekt.should
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.junit.Test
import java.nio.file.Paths

class TaxiTextDocumentServiceTest {

    @Test
    fun foo() {
        val service = TaxiTextDocumentService()

        // The initial state has a compiler error,
        // that financial-terms.taxi contains "namesapce" instead of "namespace"
        val workspaceUri = Resources.getResource("test-scenarios/workspace-with-errors-and-imports")
        val workspaceRoot = Paths.get(workspaceUri.toURI())
        service.initialize(InitializeParams().apply {
            rootUri = workspaceUri.toString()
        })
        service.compilerMessages.should.have.size(1)

        // Now fix the type
        val updatedFilePath = workspaceRoot.resolve("financial-terms.taxi")
        service.didChange(DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(updatedFilePath.toUri().toString(), 1),
                listOf(TextDocumentContentChangeEvent(
                        updatedFilePath.toFile().readText().replace("namesapce", "namespace")
                ))
        ))

        service.compilerMessages.should.have.size(0)
    }
}