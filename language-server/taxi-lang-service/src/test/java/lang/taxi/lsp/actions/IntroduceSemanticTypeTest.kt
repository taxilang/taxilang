package lang.taxi.lsp.actions

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import lang.taxi.lsp.LspServicesConfig
import lang.taxi.lsp.document
import lang.taxi.lsp.documentServiceFor
import lang.taxi.lsp.documentServiceWithFiles
import org.eclipse.lsp4j.*

class IntroduceSemanticTypeTest : DescribeSpec({
   describe("Introduce semantic type") {
      val languageServiceConfig = LspServicesConfig(
         codeActionService = CodeActionService(listOf(IntroduceSemanticType()))
      )
      it("should offer introducing semantic type when on the field name of a primitive") {
         val (service, workspacePath) = documentServiceWithFiles(
            tempdir(),
            languageServiceConfig,
            "person.taxi" to """model Person {
               |firstName : String
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
         actions.shouldHaveSize(1)
         val action = actions.single().right
         action.title.shouldBe(IntroduceSemanticType.TITLE)
         val edits = action.edit.changes.values.single()
         edits.shouldHaveSize(2)
      }
   }
})
