package lang.taxi.lsp

import com.google.common.io.Resources
import com.nhaarman.mockito_kotlin.mock
import com.winterbe.expekt.should
import org.assertj.core.util.Files
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
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
    @Test
    fun initialSourcesExcludesPathsWithTaxi() {
        val (service, workspaceRoot) = documentServiceFor("test-scenarios/folder-containing-dottaxi-folder")
        service.compilerMessages.should.have.size(0)
        service.connect(mock<LanguageClient>())
        val compilationResult = service.lastCompilationResult
        compilationResult.document!!.types.should.have.size(3)
    }
}