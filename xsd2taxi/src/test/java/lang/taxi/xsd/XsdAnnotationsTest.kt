package lang.taxi.xsd

import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test

class XsdAnnotationsTest {
   @Test
   fun loadsResources() {
      // Getting errors at build time, so adding this test.
      XsdAnnotations.annotationsTaxiSource.shouldNotBeNull()
   }
}
