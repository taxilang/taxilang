package lang.taxi.types

import arrow.core.right
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime


class InstantCoercerTest {

   @Test
   fun `can coerce string without timezone to instant`() {
      PrimitiveType.INSTANT.coerce("2020-10-04T23:00:00")
         .shouldBe(Instant.parse("2020-10-04T23:00:00Z").right())
   }

   @Test
   fun `can coerce string with timezone to instant`() {
      PrimitiveType.INSTANT.coerce("2020-10-04T23:00:00Z")
         .shouldBe(Instant.parse("2020-10-04T23:00:00Z").right())
   }

   @Test
   fun `can coerce string with offset timezone to instant`() {
      val expected = OffsetDateTime.parse("2020-10-04T23:00:00+01:00").toInstant()
      PrimitiveType.INSTANT.coerce("2020-10-04T23:00:00+0100")
         .shouldBe(expected.right())
   }

   @Test
   fun `can coerce string with nanoseconds to instant`() {
      val expected = Instant.parse("2025-09-02T07:47:46.220618717Z")
      PrimitiveType.INSTANT.coerce("2025-09-02T07:47:46.220618717Z")
         .shouldBe(expected.right())
   }

   @Test
   fun `can coerce to date`() {
      val expected = LocalDate.parse("2025-09-02")
      PrimitiveType.LOCAL_DATE.coerce("2025-09-02")
         .shouldBe(expected.right())
   }
}
