package lang.taxi.lsp.gotoDefinition

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.lsp.document
import lang.taxi.lsp.documentServiceFor
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class GotoDefinitionServiceSpec : DescribeSpec({
   describe("goto definition") {
      it("should provide the correct link for a type") {
         val (service, workspaceRoot) = documentServiceFor("test-scenarios/simple-workspace")
         val locations = service.definition(
            DefinitionParams(
               workspaceRoot.document("trade.taxi"),
               Position(3, 22) // This is the term CurrencySymbol
            )
         ).get().left

         locations.should.have.size(1)
         val location = locations.first()
         location.uri.should.endWith("financial-terms.taxi")
         location.range.start.line.should.equal(14)
         location.range.start.character.should.equal(11)
      }
   }
})
