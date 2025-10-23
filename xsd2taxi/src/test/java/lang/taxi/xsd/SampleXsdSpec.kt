package lang.taxi.xsd

import com.google.common.io.Resources
import io.kotest.core.spec.style.DescribeSpec
import kotlin.io.path.toPath

class SampleXsdSpec : DescribeSpec({
   describe("converting sample xsds") {
      it("should attribute imported types to correct namespace") {
         val resource = Resources.getResource("samples/small-xsd-with-imports/main.xsd")
         val converter = TaxiGenerator(
            config =
               XsdReaderConfig(
                  xsdImportOverrides = mapOf(
                     "http://example.com/common" to resource.toURI().toPath().parent.resolve("common-types.xsd")
                  )
               )
         )
         val generated = converter.generateAsStrings(resource.openStream())
         val generatedTaxi = generated.concatenatedSourceExcludingNamespaces()
         generatedTaxi
      }

      it("should convert the FSAHSFFeedHP-v2-0.xsd spec") {
         val resource = Resources.getResource("samples/xsd-with-imports/FSAHSFFeedHP-v2-0.xsd")
         val converter = TaxiGenerator(
            config =
               XsdReaderConfig(
                  xsdImportOverrides = mapOf(
                     "http://www.fsa.gov.uk/XMLSchema/FSAFeedCommon-v1-2" to resource.toURI().toPath().parent.resolve("CommonTypes-Schema.xsd")
                  )
               )
         )
         val generated = converter.generateAsStrings(resource.openStream())
         generated
      }
   }

})
