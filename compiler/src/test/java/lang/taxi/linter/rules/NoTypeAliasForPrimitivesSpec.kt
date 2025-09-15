package lang.taxi.linter.rules

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.linter.LinterRules
import lang.taxi.validated

class NoTypeAliasForPrimitivesSpec : DescribeSpec({
   describe("no-type-alias-on-primitivies") {
      it("should raise message when type alias present on primitive") {
         val messages = """
            type alias Person as String
         """.validated(
            linterRules = LinterRules.onlyEnable(
               NoTypeAliasOnPrimitivesTypeRule
            )
         )
         messages.should.have.size(1)
      }
      it("should raise message when type alias used in model on primitive") {
         val messages = """
            model Person {
             firstName : FirstName as String
          }
         """.validated(
            linterRules = LinterRules.onlyEnable(
               NoTypeAliasOnPrimitivesTypeRule
            )
         )
         messages.should.have.size(1)
      }
   }
})
