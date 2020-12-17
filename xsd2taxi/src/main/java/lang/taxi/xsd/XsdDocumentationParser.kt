package lang.taxi.xsd

import com.sun.xml.xsom.parser.AnnotationContext
import com.sun.xml.xsom.parser.AnnotationParser
import com.sun.xml.xsom.parser.AnnotationParserFactory
import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.EntityResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.helpers.DefaultHandler

class XsdDocumentationParserFactory : AnnotationParserFactory {
   override fun create(): AnnotationParser = XsdDocumentationParser()
}

class XsdDocumentationParser : AnnotationParser() {
   private val stringBuilder = StringBuilder()
   override fun getContentHandler(context: AnnotationContext?, parentElementName: String?, errorHandler: ErrorHandler?, entityResolver: EntityResolver?): ContentHandler {

      return object : DefaultHandler() {

         private var parsingDocumentation: Boolean = false
         override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            if (uri == XsdPrimitives.XML_NAMESPACE && localName == "documentation") {
               parsingDocumentation = true
            }
         }

         override fun characters(ch: CharArray, start: Int, length: Int) {
            if (parsingDocumentation) {
               stringBuilder.append(ch, start, length)
            }
         }

         override fun endElement(uri: String?, localName: String, qName: String?) {
            if (uri == XsdPrimitives.XML_NAMESPACE && localName == "documentation") {
               parsingDocumentation = false
            }
         }
      }
   }

   override fun getResult(existing: Any?): Any {
      return XsdDocumentationElement(stringBuilder.toString().trim())
   }

}

data class XsdDocumentationElement(val content:String)
