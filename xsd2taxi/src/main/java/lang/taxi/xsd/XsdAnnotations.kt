package lang.taxi.xsd

import com.google.common.io.Resources
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.types.AnnotationType
import java.io.File

object XsdAnnotations {

   val annotationsTaxiDoc: TaxiDocument
   val annotationsTaxiSource : String

   val XML_BODY_TYPE: AnnotationType
   val XML_ATTRIBUTE_TYPE: AnnotationType

   init {
      val file = File(Resources.getResource("XmlAnnotations.taxi").toURI())
      annotationsTaxiSource = file.readText()
      annotationsTaxiDoc = Compiler(file)
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
