package lang.taxi.xsd

import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test

class XsdPrimitivesTest {
   @Test
   fun loadsResources() {
      XsdPrimitives.primitivesTaxiSource.shouldNotBeNull()
   }
}
