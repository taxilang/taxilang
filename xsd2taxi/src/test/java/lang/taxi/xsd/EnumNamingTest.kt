package lang.taxi.xsd

import com.winterbe.expekt.expect
import org.junit.jupiter.api.Test

class EnumNamingTest {

   @Test
   fun `replaces numeric names with $ prefix`() {
      expect(EnumNaming.toValidEnumName("2")).to.equal("$2")
   }
   @Test
   fun `replaces spaces with underscore`() {
      expect(EnumNaming.toValidEnumName("Foo bar")).to.equal("Foo_bar")
   }
}

