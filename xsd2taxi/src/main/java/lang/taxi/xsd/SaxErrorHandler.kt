package lang.taxi.xsd

import lang.taxi.utils.log
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException

class SaxErrorHandler  :ErrorHandler {
   override fun warning(p0: SAXParseException?) {
      log().warn("Validation warning: ${p0!!.message}")
   }

   override fun error(p0: SAXParseException) {
      throw  p0
   }

   override fun fatalError(p0: SAXParseException) {
      throw p0
   }
}
