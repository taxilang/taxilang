package lang.taxi.linter.rules

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.linter.LinterRules
import lang.taxi.validated
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TypesShouldNotHaveFieldsSpec : DescribeSpec({
   describe("type-should-not-have-fields") {
      it("should generate message if type has fields") {
        val messages =  """
            type Person {
               firstName : FirstName inherits String
            }
         """.validated(
            linterRules = LinterRules.onlyEnable(
               TypesShouldNotHaveFieldsRule
            )
         )
         messages.should.have.size(1)
      }
   }
})
