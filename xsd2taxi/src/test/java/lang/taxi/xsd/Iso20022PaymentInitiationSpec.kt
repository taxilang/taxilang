package lang.taxi.xsd

import com.google.common.io.Resources
import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.CompilationError
import lang.taxi.Compiler
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.messages.Severity
import java.io.File

object Iso20022PaymentInitiationSpec : DescribeSpec({
   describe("parsing Iso20022 payment initiation spec") {
      it("should parse pain.001.001.10_1.xsd") {
         val (generated, errors) = compileXsdResource("samples/iso20022/payments-initiation/pain.001.001.10_1.xsd")
         errors.filter { it.severity == Severity.ERROR }.should.be.empty

         val expected = Resources.getResource("samples/iso20022/payments-initiation/expected/expected.taxi")
            .readText()
         //TestHelpers.expectToCompileTheSame(generated.taxi, xsdTaxiSources(expected))
      }

   }
})

fun compileXsdResource(resourceName: String): Pair<GeneratedTaxiCode,List<CompilationError>> {
   val resource = Resources.getResource(resourceName)
   val generated = TaxiGenerator().generateAsStrings(File(resource.file))
   return generated to Compiler.forStrings(generated.taxi).validate()
}
