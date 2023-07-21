package lang.taxi.generators.soap

import com.sun.xml.xsom.XSSchemaSet
import com.sun.xml.xsom.parser.XSOMParser
import lang.taxi.TaxiDocument
import lang.taxi.xsd.SaxErrorHandler
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import java.net.URL
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

   fun parseTypes(wsdlSourceUrl: URL): TaxiDocument {
      val doc = parseDocument(wsdlSourceUrl)

      val expr = buildXPathExpression()
      val schemas = expr.evaluate(doc, XPathConstants.NODESET) as org.w3c.dom.NodeList

      val schemaSet = parseXsdSchemas(schemas, doc)
      val taxiDoc = lang.taxi.xsd.TaxiGenerator()
         .generateTaxiDocument(schemaSet)
      return taxiDoc

   }

   private fun parseXsdSchemas(schemas: NodeList, doc: Document): XSSchemaSet {
      val parser = XSOMParser(SAXParserFactory.newDefaultInstance())
      parser.errorHandler = SaxErrorHandler()
      for (i in 0 until schemas.length) {
         val schema = schemas.item(i) as Element

         // Copy the namespace declarations from the WSDL root element
         val rootElement = doc.documentElement
         val rootAttributes = rootElement.attributes
         for (j in 0 until rootAttributes.length) {
            val attr = rootAttributes.item(j)
            if (attr.nodeName.startsWith("xmlns:")) {
               schema.setAttribute(attr.nodeName, attr.nodeValue)
            }
         }

         val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
         val transformer = transformerFactory.newTransformer()
         val source = javax.xml.transform.dom.DOMSource(schema)
         val result = javax.xml.transform.stream.StreamResult(StringWriter())
         transformer.transform(source, result)
         val schemaString = result.writer.toString()
         parser.parse(InputSource(StringReader(schemaString)))
      }

      return parser.result
   }

   private fun parseDocument(wsdlSourceUrl: URL): Document {
      val documentBuilderFactory = DocumentBuilderFactory.newInstance()
      documentBuilderFactory.isNamespaceAware = true
      val docBuilder = documentBuilderFactory.newDocumentBuilder()
      val doc = docBuilder.parse(wsdlSourceUrl.openStream())
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
