package lang.taxi.lsp

import com.winterbe.expekt.should
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.mock

@Ignore("This was previously not running as part of the build, and it fails")
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
    @Test
    fun initialSourcesExcludesPathsWithTaxi() {
        val (service, workspaceRoot) = documentServiceFor("test-scenarios/folder-containing-dottaxi-folder")
        service.compilerMessages.should.have.size(0)
        service.connect(mock<LanguageClient>())
        val compilationResult = service.lastCompilationResult
        compilationResult.document!!.types.should.have.size(3)
    }
}
