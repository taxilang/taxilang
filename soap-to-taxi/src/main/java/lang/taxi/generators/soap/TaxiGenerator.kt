package lang.taxi.generators.soap

import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import lang.taxi.generators.SourceMap
import lang.taxi.services.Service
import lang.taxi.sources.SourceCode
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory
import java.net.URI
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
   private fun generateTaxiDocument(wsdlSource: String): Pair<TaxiDocument, List<SourceCode>> {
      val tempFile = Files.createTempFile("soapspec-", "wsdl")
      tempFile.writeText(wsdlSource)
      return generateTaxiDocumentAndWsdlSources(tempFile.toUri().toURL())
   }


   fun generateTaxiDocumentAndWsdlSources(wsdlSourceUrl: URL): Pair<TaxiDocument, List<SourceCode>> {
      val wsdl = wsdlSourceUrl.readText()
      val wsdlSource = SourceCode(
         wsdlSourceUrl.file,
         wsdl,
         wsdlSourceUrl.toURI().toPath(),
         language = SoapLanguage.WSDL
      )

      val (parsedXsdSchema, importedXsds) = soapTypeMapper.parseTypes(wsdlSourceUrl)
      val allWsdlSources = listOf(wsdlSource) + importedXsds
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
            services.add(serviceMapper.generateService(importedXsds))
            mutatedTypes.putAll(serviceMapper.modifiedTypes)
         }

      val types = parsedXsdSchema.types.associateBy { it.toQualifiedName() }
         .toMutableMap()
      // Take the updated definitions for types (ie., which add parameter modifiers, etc)
      types.putAll(mutatedTypes)
      return TaxiDocument(
         types = types.values.toSet(),
         services = services.toSet(),

         ) to allWsdlSources
   }

   fun generateTaxiDocument(wsdlSourceUrl: URL): TaxiDocument {
      return generateTaxiDocumentAndWsdlSources(wsdlSourceUrl).first
   }

   /**
    * Returns the Taxi generated code, along with the actual
    * sources (including any imported XSD sources) from the wsdl
    */
   fun wsdlToGeneratedSources(wsdlUri: URI): Pair<GeneratedTaxiCode, List<SourceCode>> {
      val (taxiDoc, wsdlSources) = generateTaxiDocumentAndWsdlSources(wsdlUri.toURL())
      val generatedCode = taxiDocToGeneratedCode(taxiDoc, wsdlSources)
      return generatedCode to wsdlSources
   }

   fun generateTaxiAndCompile(
      wsdlSource: String
   ): Pair<TaxiDocument, GeneratedTaxiCode> {
      val (taxiDoc, wsdlSources) = generateTaxiDocument(wsdlSource)
      val generatedCode = taxiDocToGeneratedCode(taxiDoc, wsdlSources)
      return taxiDoc to generatedCode
   }

   private fun taxiDocToGeneratedCode(taxiDoc: TaxiDocument, wsdlSources: List<SourceCode>): GeneratedTaxiCode {
      val taxi = schemaWriter.generateSchemas(
         listOf(taxiDoc)
      )
      val sourceNames = wsdlSources.map { it.sourceName }
      val sourceMap = SourceMap.forMembers(sourceNames, taxiDoc.types, taxiDoc.services)
      return GeneratedTaxiCode(
         taxi, logger.messages,
         sourceMap = sourceMap
      )
   }

}
