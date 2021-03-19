package lang.taxi.linter.rules

import com.winterbe.expekt.should
import lang.taxi.linter.LinterRules
import lang.taxi.validated
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NoDuplicateTypesOnModelSpec : Spek({
   describe("no-duplicate-types-on-models") {
      it("should raise message when duplicate types are present") {
         val messages = """
            type Name inherits String
            model Person {
               firstName : Name
               lastName : Name
            }
         """.validated(
            linterRules = LinterRules.onlyEnable(
               NoDuplicateTypesOnModelsRule
            )
         )
         messages.should.have.size(2)
      }
   }
})
