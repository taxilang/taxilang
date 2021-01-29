package lang.taxi.xsd

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.Compiler
import lang.taxi.testing.TestHelpers
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FpmlBusinessEventsSpek : Spek({
   describe("parsing fpml") {
      it("should parse a business events file") {
         val (generated, errors) = compileXsdResource("samples/fpml/confirmation-5-10_xml/confirmation/fpml-business-events-5-10.xsd")

         errors.should.be.empty
         val expected = Resources.getResource("samples/fpml/confirmation-5-10_xml/confirmation/expected-taxi/fpml.taxi")
            .readText()

         val generatedDoc = Compiler.forStrings(generated.taxi).compile()
         val expectedDoc = Compiler.forStrings(xsdTaxiSources(expected)).compile()
         TestHelpers.expectToCompileTheSame(generated.taxi, xsdTaxiSources(expected))
      }
   }
}) {
}
