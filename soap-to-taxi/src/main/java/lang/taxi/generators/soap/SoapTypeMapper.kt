package lang.taxi.generators.soap

import com.sun.xml.xsom.XSSchemaSet
import com.sun.xml.xsom.parser.XSOMParser
import lang.taxi.TaxiDocument
import lang.taxi.sources.SourceCode
import lang.taxi.xsd.SaxErrorHandler
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory

/**
 * Extracts types defined in a SOAP WSDL.
 *
 * Types are actually defined in embedded xsd: schemas.
 * To access this, we extract the schemas, and parse them with the
 * xsd-to-taxi parser.
 */
class SoapTypeMapper {

   /**
    * Returns a TaxiDocument containing the types present in the WSDL,
    * along with additional source files for XSDs that were imported, external
    * to the WSDL itself.
    *
    * These source files should be attached to the services, so that the WSDL can be parsed correctly.
    */
   fun parseTypes(wsdlSourceUrl: URL): Pair<TaxiDocument,List<SourceCode>> {
      val doc = parseDocument(wsdlSourceUrl)

      val expr = buildXPathExpression()
      val schemas = expr.evaluate(doc, XPathConstants.NODESET) as org.w3c.dom.NodeList

      val (schemaSet, capturedXsds) = parseXsdSchemas(schemas, doc, wsdlSourceUrl)
      val taxiDoc = lang.taxi.xsd.TaxiGenerator()
         .generateTaxiDocument(schemaSet)
      return taxiDoc to capturedXsds

   }

   private fun parseXsdSchemas(schemas: NodeList, doc: Document, wsdlSourceUrl: URL): Pair<XSSchemaSet, List<SourceCode>> {
      val capturedXsds = mutableListOf<SourceCode>()
      val parser = XSOMParser(SAXParserFactory.newDefaultInstance())
      parser.errorHandler = SaxErrorHandler()
      parser.entityResolver = RecordingEntityResolver(capturedXsds)

      for (i in 0 until schemas.length) {
         val schema = schemas.item(i) as Element

         copyNamespacesFromRoot(doc, schema)

         val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
         val transformer = transformerFactory.newTransformer()
         val source = javax.xml.transform.dom.DOMSource(schema)
         val result = javax.xml.transform.stream.StreamResult(StringWriter())
         transformer.transform(source, result)
         val schemaString = result.writer.toString()
         val inputSource = InputSource(StringReader(schemaString))
         // Set the systemId, which tells XSOM where this schema came from.
         // This is important for resolving relative xsd imports
         inputSource.systemId = wsdlSourceUrl.toExternalForm()
         parser.parse(inputSource)
      }

      return parser.result to capturedXsds
   }

   private fun copyNamespacesFromRoot(doc: Document, schema: Element) {
      // Copy the namespace declarations from the WSDL root element
      val rootElement = doc.documentElement
      val rootAttributes = rootElement.attributes
      for (j in 0 until rootAttributes.length) {
         val attr = rootAttributes.item(j)
         if (attr.nodeName.startsWith("xmlns:")) {
            schema.setAttribute(attr.nodeName, attr.nodeValue)
         }
      }
   }

   private fun parseDocument(wsdlSourceUrl: URL): Document {
      val documentBuilderFactory = DocumentBuilderFactory.newInstance()
      documentBuilderFactory.isNamespaceAware = true
      val docBuilder = documentBuilderFactory.newDocumentBuilder()
      val doc = docBuilder.parse(wsdlSourceUrl.toExternalForm())
      return doc
   }

   /**
    * Returns an XPath expression which extracts the xsd schema.
    */
   private fun buildXPathExpression(): XPathExpression {
      val xPathfactory = XPathFactory.newInstance()
      val xpath = xPathfactory.newXPath()
      xpath.namespaceContext = object : NamespaceContext {
         override fun getNamespaceURI(prefix: String): String = when (prefix) {
            "wsdl" -> "http://schemas.xmlsoap.org/wsdl/"
            "xsd" -> "http://www.w3.org/2001/XMLSchema"
            else -> throw IllegalArgumentException("No namespace for prefix $prefix")
         }

         override fun getPrefix(uri: String): String? = null
         override fun getPrefixes(namespaceURI: String?): MutableIterator<String> =
            mutableListOf<String>().listIterator()
      }

      val expr = xpath.compile("//wsdl:types/xsd:schema")
      return expr
   }
}

/**
 * Captures the XSD's loaded externally, so they can be used later
 * when we need to create a SOAP client.
 * The sources will be attached to the Service as compilation units, then consumed
 * in the SoapInvoker to build a SOAP client
 *
 */
private class RecordingEntityResolver(
   private val capture: MutableList<SourceCode>
) : org.xml.sax.EntityResolver {

   private val seen = mutableSetOf<String>()

   override fun resolveEntity(publicId: String?, systemId: String?): InputSource? {
      if (systemId.isNullOrBlank()) return null
      // Avoid duplicates / loops
      if (!seen.add(systemId)) return null

      // Open via URL (works for file:, http(s):, jar:file:...!)
      val url =  URI.create(systemId).toURL()
      val bytes = url.openStream().use { it.readAllBytes() }
      val text = bytes.toString(Charsets.UTF_8)

      // Return an InputSource with the SAME systemId (critical for nested imports)
      val inputSource = InputSource(java.io.StringReader(text))
      inputSource.systemId = systemId

      // Best-effort name/path
      val name = runCatching { java.nio.file.Paths.get(java.net.URI(systemId)).fileName?.toString() }
         .getOrNull() ?: systemId.substringAfterLast('/')

      val path = runCatching {
         val uri = java.net.URI(systemId)
         if (uri.scheme == "file") java.nio.file.Paths.get(uri) else null
      }.getOrNull()

      capture += SourceCode(
         sourceName = name,
         content = text,
         path = path ?: Paths.get(name), // harmless placeholder for non-file URLs
         language = SoapLanguage.XSD,
      )

      return inputSource
   }
}
