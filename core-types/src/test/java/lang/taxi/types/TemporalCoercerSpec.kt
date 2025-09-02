package lang.taxi.types

import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import java.time.*

class TemporalCoercerTest : StringSpec({

   "Instant coercion should handle common formats" {
      forAll(
         row("2020-10-04T23:00:00", Instant.parse("2020-10-04T23:00:00Z")),
         row("2020-10-04T23:00:00Z", Instant.parse("2020-10-04T23:00:00Z")),
         row("2020-10-04T23:00:00+01:00", OffsetDateTime.parse("2020-10-04T23:00:00+01:00").toInstant()),
         row("2020-10-04T23:00:00+0100", OffsetDateTime.parse("2020-10-04T23:00:00+01:00").toInstant()),
         row("2020-10-04T23:00:00.123Z", Instant.parse("2020-10-04T23:00:00.123Z")),
         row("2025-09-02T07:47:46.220618717Z", Instant.parse("2025-09-02T07:47:46.220618717Z"))
      ) { input, expected ->
         PrimitiveType.INSTANT.coerce(input) shouldBe expected.right()
      }
   }

   "DateTime coercion should handle local datetime formats" {
      forAll(
         row("2020-10-04T23:00:00", LocalDateTime.parse("2020-10-04T23:00:00")),
         row("2020-10-04T23:00:00.123", LocalDateTime.parse("2020-10-04T23:00:00.123")),
         row("2020-10-04T23:00:00.123456789", LocalDateTime.parse("2020-10-04T23:00:00.123456789"))
      ) { input, expected ->
         PrimitiveType.DATE_TIME.coerce(input) shouldBe expected.right()
      }
   }

   "LocalDate coercion should handle yyyy-MM-dd format" {
      forAll(
         row("2025-09-02", LocalDate.parse("2025-09-02")),
         row("1999-12-31", LocalDate.parse("1999-12-31"))
      ) { input, expected ->
         PrimitiveType.LOCAL_DATE.coerce(input) shouldBe expected.right()
      }
   }

   "Time coercion should handle HH:mm:ss with optional fractions" {
      forAll(
         row("13:45:30", LocalTime.parse("13:45:30")),
         row("00:00:00", LocalTime.parse("00:00:00")),
         row("23:59:59.123", LocalTime.parse("23:59:59.123")),
         row("23:59:59.123456789", LocalTime.parse("23:59:59.123456789"))
      ) { input, expected ->
         PrimitiveType.TIME.coerce(input) shouldBe expected.right()
      }
   }
})
