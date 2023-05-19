package lang.taxi.values

import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.Any
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.to

object PrimitiveValues {
   private val taxiPrimitiveToJavaTypes = mapOf(
      PrimitiveType.BOOLEAN to listOf(Boolean::class.java),
      PrimitiveType.STRING to listOf(String::class.java, Char::class.java),
      PrimitiveType.INTEGER to listOf(
         Integer::class.java,
         Int::class.java,
         BigInteger::class.java,
         kotlin.Short::class.java,
         kotlin.Long::class.java,
         Long::class.java,
         Short::class.java
      ),
      PrimitiveType.DECIMAL to listOf(
         BigDecimal::class.java,
         kotlin.Double::class.java,
         kotlin.Float::class.java,
         Double::class.java,
         Float::class.java
      ),
      PrimitiveType.DOUBLE to listOf(
         kotlin.Double::class.java,
         kotlin.Float::class.java,
         Double::class.java,
         Float::class.java
      ),
      PrimitiveType.ANY to listOf(Any::class.java),
      PrimitiveType.LOCAL_DATE to listOf(LocalDate::class.java),
      PrimitiveType.TIME to listOf(LocalTime::class.java),
      PrimitiveType.DATE_TIME to listOf(LocalDateTime::class.java),
      PrimitiveType.INSTANT to listOf(Instant::class.java)
   )
   private val javaTypeToPrimitive: Map<String, PrimitiveType> =
      taxiPrimitiveToJavaTypes.flatMap { (primitive, javaTypes) ->
         javaTypes.map { it.canonicalName to primitive }
      }.toMap() + mapOf(
         // Can't seem get reference to this class in Kotlin
         "java.lang.Boolean" to PrimitiveType.BOOLEAN
      )

   fun getTaxiPrimitive(rawType: Class<*>): Type {
      return javaTypeToPrimitive[rawType.canonicalName]!!
   }

   fun getTaxiPrimitive(value: Any): Type {
      return getTaxiPrimitive(value::class.java)
   }
}
