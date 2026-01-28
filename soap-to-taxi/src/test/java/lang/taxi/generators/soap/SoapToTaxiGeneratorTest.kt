package lang.taxi.generators.soap

import com.google.common.io.Resources
import com.winterbe.expekt.should
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.types.UnresolvedImportedType
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createFile

class SoapToTaxiGeneratorTest {

   @TempDir
   @JvmField
   var folder: Path? = null


   @Test
   fun `generate from wsdl`() {
      val wsdl = deployToTempDir(Resources.getResource("CountryInfoServiceSpec.wsdl"))
      val generator = TaxiGenerator()
      println("Using URL at ${wsdl.toExternalForm()}")
      val taxi = generator.generateTaxiDocument(wsdl)
      taxi.types.should.have.size(172)
      taxi.services.should.have.size(1)
      taxi.services.single().operations.should.have.size(21)
   }

   @Test
   fun `return types from services are correct`() {
      val wsdl = deployToTempDir(Resources.getResource("CountryInfoServiceSpec.wsdl"))
      val taxi = TaxiGenerator().generateTaxiDocument(wsdl)
      val service = taxi.service("org.oorsprong.CountryInfoService")
      val operation = service.operation("CountryName")
      // Note that the return type is NOT the wrapper type (in this case, CountryNameResponse),
      // which is just SOAP envelope stuff -- it's the actual thing inside.
      operation.returnType.qualifiedName.shouldBe("org.oorsprong.countrynameresponse.CountryNameResult")
      operation.returnType.isScalar.shouldBeTrue()
   }

   /**
    * This test ensures that SOAP responses which are scalar get correctly unwrapped from the envelope
    * types, when the response type is an imported name.
    *
    * Note that SOAP clients unwrap envelope responses when that envelope contains a single scalar value.
    * The value returned out of the client isn't the envelope, but the scalar value from inside.
    *
    * The implementation needs to consider this - but the approach is different
    * when we're using an imported type, as we're not responsible for generating the actual
    * type - it's imported from elsewhere in the schema
    *
    */
   @Test
   fun `return types from services are correct when they are using an assigned taxi name`() {
      val wsdl = deployToTempDir(Resources.getResource("AnnotatedCountryInfoServiceSpec.wsdl"))
      val taxi = TaxiGenerator().generateTaxiDocument(wsdl)
      val service = taxi.service("org.oorsprong.CountryInfoService")
      val operation = service.operation("CountryName")
      // Note that the return type is NOT the wrapper type (in this case, CountryNameResponse),
      // which is just SOAP envelope stuff -- it's the actual thing inside.
      operation.returnType.qualifiedName.shouldBe("com.orbitalhq.CountryName")
      // We can't assert that the type is scalar here, as it's not actually defined
      // in this schema.
      operation.returnType.shouldBeInstanceOf<UnresolvedImportedType>()
   }

   private fun deployToTempDir(resource: URL): URL {
      val file = folder!!.resolve("spec.wsdl")
      file.createFile()
      Resources.copy(resource, file.toFile().outputStream())
      return file.toUri().toURL()
   }

   @Test
   fun `wsdl source is attached to the service`() {
      val wsdl = deployToTempDir(Resources.getResource("TrimmedCountryInfoServiceSpec.wsdl"))
      val generator = TaxiGenerator()
      val taxi = generator.generateTaxiDocument(wsdl)

      val service = taxi.services.single()
      val compilationUnit = service.compilationUnits.single { unit -> unit.source.language == SoapLanguage.WSDL }
      compilationUnit.source.content.should.equal(wsdl.readText())
   }

}
