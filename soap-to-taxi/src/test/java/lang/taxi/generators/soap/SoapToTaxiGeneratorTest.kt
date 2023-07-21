package lang.taxi.generators.soap

import com.google.common.io.Resources
import com.winterbe.expekt.should
import org.junit.jupiter.api.Test

class SoapToTaxiGeneratorTest {

   @Test
   fun `generate from wsdl`() {
      val wsdl = Resources.getResource("TrimmedCountryInfoServiceSpec.wsdl")
      val generator = TaxiGenerator()
      val taxi = generator.generateTaxiDocument(wsdl)
      TODO()
   }

   @Test
   fun `wsdl source is attached to the service`() {
      val wsdl = Resources.getResource("TrimmedCountryInfoServiceSpec.wsdl")
      val generator = TaxiGenerator()
      val taxi = generator.generateTaxiDocument(wsdl)

      val service = taxi.services.single()
      val compilationUnit = service.compilationUnits.single { unit -> unit.source.language == SoapLanguage.WSDL }
      compilationUnit.source.content.should.equal(wsdl.readText())
   }

}
