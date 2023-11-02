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

   annotation XmlAttribute
   annotation XmlBody
}
   """.trimIndent()

   val XML_BODY_TYPE: AnnotationType
   val XML_ATTRIBUTE_TYPE: AnnotationType

   init {
      annotationsTaxiDoc = Compiler(annotationsTaxiSource)
         .compile()
      XML_BODY_TYPE = annotationsTaxiDoc.annotation("lang.taxi.xml.XmlBody")
      XML_ATTRIBUTE_TYPE = annotationsTaxiDoc.annotation("lang.taxi.xml.XmlAttribute")
   }

   fun xmlBody(): lang.taxi.types.Annotation {
      return lang.taxi.types.Annotation(XML_BODY_TYPE, emptyMap())
   }

   fun xmlAttribute(): lang.taxi.types.Annotation {
      return lang.taxi.types.Annotation(XML_ATTRIBUTE_TYPE, emptyMap())
   }

}
