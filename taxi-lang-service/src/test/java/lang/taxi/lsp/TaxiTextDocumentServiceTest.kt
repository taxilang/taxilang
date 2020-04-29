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
    fun afterEditingWorkspaceFile_then_compilerErrorsAreRemoved() {
        val (service, workspaceRoot) = documentServiceFor("test-scenarios/workspace-with-errors-and-imports")

        // The initial state has a compiler error,
        // that financial-terms.taxi contains "namesapce" instead of "namespace"
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