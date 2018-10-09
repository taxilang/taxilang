package lang.taxi.jvm.common

import lang.taxi.Type
import lang.taxi.types.PrimitiveType
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

object PrimitiveTypes {
    private val taxiPrimitiveToJavaTypes = mapOf(
            PrimitiveType.BOOLEAN to listOf(Boolean::class.java),
            PrimitiveType.STRING to listOf(String::class.java, Char::class.java),
            PrimitiveType.INTEGER to listOf(Int::class.java, BigInteger::class.java, Short::class.java, Long::class.java),
            PrimitiveType.DECIMAL to listOf(Float::class.java, BigDecimal::class.java, Double::class.java),
            PrimitiveType.LOCAL_DATE to listOf(LocalDate::class.java),
            PrimitiveType.TIME to listOf(LocalTime::class.java),
            PrimitiveType.DATE_TIME to listOf(Date::class.java),
            PrimitiveType.INSTANT to listOf(Instant::class.java)
    )
    private val javaTypeToPrimitive: Map<Class<out Any>, PrimitiveType> = taxiPrimitiveToJavaTypes.flatMap { (primitive, javaTypes) ->
        javaTypes.map { it to primitive }
    }.toMap()

    /**
     * Only considers the class itself, and not any declared
     * annotationed datatypes
     */
    fun isClassTaxiPrimitive(rawType: Class<*>): Boolean {
        return isTaxiPrimitive(rawType.typeName)
    }

    fun getTaxiPrimitive(rawType: Class<*>): Type {
        return javaTypeToPrimitive[rawType]!!
    }

    fun isTaxiPrimitive(javaTypeQualifiedName: String): Boolean {
        return this.javaTypeToPrimitive.keys.any { it.canonicalName == javaTypeQualifiedName }
    }

    fun getTaxiPrimitive(qualifiedTypeName: String): Type {
        return this.javaTypeToPrimitive.filterKeys { it.canonicalName == qualifiedTypeName }
                .values.first()
    }

    fun getJavaType(type: PrimitiveType): Class<*> {
        return this.taxiPrimitiveToJavaTypes[type]?.first() ?: error("Type ${type.name} is not mapped to a Java type")
    }
}

