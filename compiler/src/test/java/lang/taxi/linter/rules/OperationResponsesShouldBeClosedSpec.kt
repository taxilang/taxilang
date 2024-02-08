package lang.taxi.linter.rules

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import lang.taxi.linter.LinterRules
import lang.taxi.validated

class OperationResponsesShouldBeClosedSpec : DescribeSpec({
   describe("Operation rules should be closed linter rule") {
      it("should detect return types that aren't closed") {
         val messages = """
model Person {
   name : Name inherits String
}
service People {
   operation getPerson():Person
}
         """.validated(
            linterRules = LinterRules.onlyEnable(OperationResponsesShouldBeClosed)
         )

         messages.shouldHaveSize(2)
      }
   }
}) {
}
