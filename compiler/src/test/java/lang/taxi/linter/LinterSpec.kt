package lang.taxi.linter

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.linter.rules.NoPrimitiveTypesOnModelsRule
import lang.taxi.messages.Severity
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.validated
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object LinterSpec : Spek({
   describe("simple linter evaluation") {
      it("should report a model with a primitive type") {
         val messages = """
            model Bad {
               name : String
            }
         """.validated(linterRules = LinterRules.onlyEnable(NoPrimitiveTypesOnModelsRule))
         messages.should.have.size(1)
      }
      it("allow overriding the severity of a rule") {
         val rules = LinterRules.onlyEnable(NoPrimitiveTypesOnModelsRule)
            .map { it.copy(severity = Severity.ERROR) }
         val messages = """
            model Bad {
               name : String
            }
         """.validated(linterRules = rules)
         messages.should.have.size(1)
         messages.first().severity.should.equal(Severity.ERROR)
      }
   }

   describe("configuring linter from taxi.conf") {
      it("should respect overriding of severity in taxi.conf") {
         val project = TaxiSourcesLoader.loadPackage(
            Paths.get(Resources.getResource("custom-linter-config").toURI())
         ).project
         project.linter["no-duplicate-types-on-models"]!!.enabled.should.be.`true`
         project.linter["no-duplicate-types-on-models"]!!.severity.should.equal(Severity.INFO)
         project.linter["no-primitive-types-on-models"]!!.enabled.should.be.`false`
         project.linter["no-primitive-types-on-models"]!!.severity.should.equal(Severity.WARNING)
      }
   }
})
