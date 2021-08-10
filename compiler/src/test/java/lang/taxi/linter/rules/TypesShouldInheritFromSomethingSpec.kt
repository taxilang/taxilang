package lang.taxi.linter.rules

import com.winterbe.expekt.should
import lang.taxi.linter.LinterRules
import lang.taxi.validated
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TypesShouldInheritFromSomethingSpec : Spek({
   describe("types-should-inherit") {
      it("should raise message when a type doesn't inherit from anything") {
         val messages = """
            type Person
         """.validated(
            linterRules = LinterRules.onlyEnable(
               TypesShouldInheritRule
            )
         )
         messages.should.have.size(1)
      }
      it("should not raise message if a model doesn't inherit") {
         val messages = """
            model Person
         """.validated(
            linterRules = LinterRules.onlyEnable(
               TypesShouldInheritRule
            )
         )
         messages.should.be.empty
      }
   }
})
