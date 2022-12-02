package lang.taxi.compiler

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import lang.taxi.types.PrimitiveType
import java.time.Instant

class TypeCasterTest : DescribeSpec({

   it("can coerce strings to instants") {
      PrimitiveType.INSTANT.canCoerce("2020-11-02T10:00:00Z").shouldBeTrue()
      PrimitiveType.INSTANT.coerce("2020-11-02T10:00:00Z").shouldBe(Instant.parse("2020-11-02T10:00:00Z").right())
   }
   it("will return error if parse fails") {
      PrimitiveType.INSTANT.coerce("not right").shouldBe("Text 'not right' could not be parsed at index 0".left())
   }

})
