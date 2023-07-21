package lang.taxi.generators.soap

import lang.taxi.types.Annotation
import lang.taxi.types.QualifiedName


object SoapLanguage {
   val WSDL = "wsdl"
}

object SoapAnnotations {
   val SERVICE_ANNOTATION = "lang.taxi.soap.SoapService"
   val WSDL_URL_PARAM = "wsdlUrl"
   val qualifiedName = QualifiedName.from(SERVICE_ANNOTATION)


   fun soapService(wsdlUrl: String): Annotation {
      return Annotation(
         SERVICE_ANNOTATION,
         mapOf(WSDL_URL_PARAM to wsdlUrl)
      )
   }
}
