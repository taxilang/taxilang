package lang.taxi.xsd

import com.google.common.io.Resources
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.types.AnnotationType
import org.antlr.v4.runtime.CharStreams
import java.io.File
import java.nio.charset.Charset

object XsdAnnotations {

   val annotationsTaxiDoc: TaxiDocument

   // Declare inline.
   // Loading resources from classpaths is tricky in native images.
   val annotationsTaxiSource : String = """
namespace lang.taxi.xml {

   // The root annotation added to an Xml object. Indicates that this should be serialized / deserailized as Xml
   annotation Xml

   annotation XmlAttribute
   annotation XmlBody

   annotation XmlNamespace {
      uri: NamespaceUri inherits String
   }
}
   """.trimIndent()

   val XML_BODY_TYPE: AnnotationType
   val XML_ATTRIBUTE_TYPE: AnnotationType
   val XML_NAMESPACE_TYPE: AnnotationType
   val XML_ROOT_TYPE: AnnotationType

   init {
      annotationsTaxiDoc = Compiler(annotationsTaxiSource)
         .compile()
      XML_BODY_TYPE = annotationsTaxiDoc.annotation("lang.taxi.xml.XmlBody")
      XML_ATTRIBUTE_TYPE = annotationsTaxiDoc.annotation("lang.taxi.xml.XmlAttribute")
      XML_NAMESPACE_TYPE = annotationsTaxiDoc.annotation("lang.taxi.xml.XmlNamespace")
      XML_ROOT_TYPE = annotationsTaxiDoc.annotation("lang.taxi.xml.Xml")
   }

   val xmlBody: lang.taxi.types.Annotation =  lang.taxi.types.Annotation(XML_BODY_TYPE, emptyMap())
   val xmlAttribute: lang.taxi.types.Annotation = lang.taxi.types.Annotation(XML_ATTRIBUTE_TYPE, emptyMap())
   fun xmlNamespace(uri: String): lang.taxi.types.Annotation = lang.taxi.types.Annotation(XML_NAMESPACE_TYPE, mapOf("uri" to uri))
   fun xmlNamespaceOrNull(uri: String?): lang.taxi.types.Annotation? {
      if (uri.isNullOrEmpty()) return null
      // Don't emit default namespaces
      if (uri == "http://www.w3.org/2001/XMLSchema") return null
      return xmlNamespace(uri)
   }
   val xmlRoot: lang.taxi.types.Annotation = lang.taxi.types.Annotation(XML_ROOT_TYPE, emptyMap())

}
