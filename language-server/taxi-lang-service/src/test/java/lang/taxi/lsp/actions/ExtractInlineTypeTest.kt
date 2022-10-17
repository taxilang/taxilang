package lang.taxi.lsp.actions

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import lang.taxi.lsp.LspServicesConfig
import lang.taxi.lsp.document
import lang.taxi.lsp.documentServiceWithFiles
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class ExtractInlineTypeTest : DescribeSpec({
   describe("Extract inline type") {
      val languageServiceConfig = LspServicesConfig(
         codeActionService = CodeActionService(listOf(ExtractInlineType()))
      )
      it("should offer extraction for inline type defined on a field") {
         val (service, workspacePath) = documentServiceWithFiles(
            tempdir(),
            languageServiceConfig,
            "person.taxi" to """model Person {
               |firstName : FirstName inherits String
               |}
            """.trimMargin()
         )
         val actions = service.codeAction(
            CodeActionParams(
               workspacePath.document("person.taxi"),
               Range(Position(1, 12), Position(1, 12)),
               CodeActionContext(emptyList())
            )
         ).get()

      }
   }

})
