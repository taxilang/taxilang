package lang.taxi.lsp.linter

import com.winterbe.expekt.should
import lang.taxi.Compiler
import lang.taxi.lsp.documentServiceFor
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.URI

object UnusedImportSpec : Spek({
    describe("unused imports") {
        it("should detect unused imports") {
            val (service, workspaceRoot) = documentServiceFor("test-scenarios/unused-imports")

            val compilationResult = service.compileAndReport()
            val source = URI.create(SourceNames.normalize(workspaceRoot.resolve("Country.taxi").toString()))
            val messages = UnusedImport().computeInsightFor(source, compilationResult)
            messages.should.have.size(1)
            messages.first().message.should.equal("Import FirstName is not used in this file")
        }
    }
})