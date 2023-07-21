package lang.taxi.generators.soap

import com.google.common.io.Resources
import com.winterbe.expekt.should
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
      taxi.types.should.have.size(105)
      taxi.services.should.have.size(1)
      taxi.services.single().operations.should.have.size(21)
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
