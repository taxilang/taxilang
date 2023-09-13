package lang.taxi

import io.kotest.matchers.nulls.shouldNotBeNull
import lang.taxi.functions.stdlib.StdLib
import lang.taxi.stdlib.StdLibSchema
import org.junit.Test

class StdLibTest {

   @Test
   fun `stdlib compiles`() {
      StdLibSchema.taxiDocument.shouldNotBeNull()
   }
}
