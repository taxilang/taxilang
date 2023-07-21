package lang.taxi.generators.soap

import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import lang.taxi.services.Service
import lang.taxi.sources.SourceCode
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory
import java.net.URL
import java.nio.file.Files
import kotlin.io.path.toPath
import kotlin.io.path.writeText

class TaxiGenerator(
   private val schemaWriter: SchemaWriter = SchemaWriter(),
   private val logger: Logger = Logger()
) {

   private val soapTypeMapper = SoapTypeMapper()
   private var dcf = DynamicClientFactory.newInstance()

   /**
    * Accepts the contents of a WSDL document.
    * Because of limitations in CXF (can only parse from a URL),
    * this document is first written to a temporary file
    */
   fun generateTaxiDocument(wsdlSource: String): TaxiDocument {
      val tempFile = Files.createTempFile("soapspec-", "wsdl")
      tempFile.writeText(wsdlSource)
      return generateTaxiDocument(tempFile.toUri().toURL())
   }

   fun generateTaxiDocument(wsdlSourceUrl: URL): TaxiDocument {
      val wsdl = wsdlSourceUrl.readText()
      val wsdlSource = SourceCode(
         wsdlSourceUrl.file,
         wsdl,
         wsdlSourceUrl.toURI().toPath(),
         language = SoapLanguage.WSDL
      )

      val parsedXsdSchema = soapTypeMapper.parseTypes(wsdlSourceUrl)

      val client = try {
         dcf.createClient(wsdlSourceUrl)
      } catch (e: Exception) {
         throw e
      }

      val services = mutableListOf<Service>()
      val mutatedTypes = mutableMapOf<QualifiedName, Type>()
      client.endpoint.service.serviceInfos
         .forEach { serviceInfo ->
            val serviceMapper = SoapServiceMapper(wsdlSourceUrl, serviceInfo, logger, parsedXsdSchema, wsdlSource)
            services.add(serviceMapper.generateService())
            mutatedTypes.putAll(serviceMapper.modifiedTypes)
         }

      val types = parsedXsdSchema.types.associateBy { it.toQualifiedName() }
         .toMutableMap()
      // Take the updated definitions for types (ie., which add parameter modifiers, etc)
      types.putAll(mutatedTypes)
      return TaxiDocument(
         types = types.values.toSet(),
         services = services.toSet(),

         )
   }

   fun generateTaxi(
      wsdlSourceUrl: URL
   ): GeneratedTaxiCode {
      val taxiDoc = generateTaxiDocument(wsdlSourceUrl)
      val taxi = schemaWriter.generateSchemas(
         listOf(taxiDoc)
      )
      return GeneratedTaxiCode(
         taxi, logger.messages
      )
   }

}
