package lang.taxi.generators.java

import lang.taxi.*
import lang.taxi.types.Annotation
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TypeAlias
import org.jetbrains.annotations.NotNull
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

interface TypeMapper {
    fun getTaxiType(element: Class<*>, existingTypes: MutableSet<Type>): Type {
        val namespace = this.deriveNamespace(element)
        return getTaxiType(element, existingTypes, namespace)
    }

    fun getTaxiType(element: AnnotatedElement, existingTypes: MutableSet<Type>, defaultNamespace: String): Type

    fun deriveNamespace(javaClass: Class<*>): String {
        val dataType = javaClass.getAnnotation(DataType::class.java)
        val namespaceAnnotation = javaClass.getAnnotation(Namespace::class.java)
        return when {
            namespaceAnnotation != null -> namespaceAnnotation.value
            dataType != null && dataType.hasNamespace() -> dataType.namespace()!!
            else -> javaClass.`package`.name
        }
    }

    fun deriveTypeName(element: AnnotatedElement, defaultNamespace: String): String {
        if (element.isAnnotationPresent(DataType::class.java)) {
            val annotation = element.getAnnotation(DataType::class.java)
            if (annotation.declaresName()) {
                return annotation.qualifiedName(defaultNamespace)
            }
        }

        val type = typeFromElement(element)
        // If it's an inner class, trim the qualifier
        // This may cause problems with duplicates, but let's encourage
        // peeps to solve that via the DataType annotation.
        val typeName = type.simpleName.split("$").last()
        return "$defaultNamespace.$typeName"
    }

    fun typeFromElement(element: AnnotatedElement): Class<*> {
        val type = when (element) {
            is Class<*> -> element
            is Field -> element.type
            else -> error("Unhandled type : $element")
        }
        return type
    }
}

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

    fun isTaxiPrimitive(javaTypeQualifiedName: String): Boolean {
        return this.javaTypeToPrimitive.keys.any { it.canonicalName == javaTypeQualifiedName }
    }

    fun getTaxiPrimitive(qualifiedTypeName: String): Type {
        return this.javaTypeToPrimitive.filterKeys { it.canonicalName == qualifiedTypeName }
                .values.first()
    }
}

class DefaultTypeMapper : TypeMapper {

    fun MutableSet<Type>.findByName(qualifiedTypeName: String): Type? {
        return this.firstOrNull { it.qualifiedName == qualifiedTypeName }
    }

    override fun getTaxiType(element: AnnotatedElement, existingTypes: MutableSet<Type>, defaultNamespace: String): Type {
        val type = typeFromElement(element)
        val targetTypeName = deriveTypeName(element, defaultNamespace)

        if (declaresTypeAlias(element)) {
            val typeAliasName = getDeclaredTypeAliasName(element, defaultNamespace)!!
            return getOrCreateTypeAlias(element, typeAliasName, existingTypes)
        }
        if (PrimitiveTypes.isTaxiPrimitive(type.name)) {
            return PrimitiveTypes.getTaxiPrimitive(type.name)
        }


        val existing = existingTypes.findByName(targetTypeName)
        if (existing != null) {
            return existing
        }

        return mapNewObjectType(element, defaultNamespace, existingTypes)
    }

    private fun getOrCreateTypeAlias(element: AnnotatedElement, typeAliasName: String, existingTypes: MutableSet<Type>): TypeAlias {
        val existingAlias = existingTypes.findByName(typeAliasName)
        if (existingAlias != null) {
            return existingAlias as TypeAlias
        } else {
            val aliasedJavaType = typeFromElement(element)
            val aliasedTaxiType = getTaxiType(aliasedJavaType, existingTypes)
            val typeAlias = TypeAlias(typeAliasName, aliasedTaxiType)
            existingTypes.add(typeAlias)
            return typeAlias
        }
    }

    private fun declaresTypeAlias(element: AnnotatedElement): Boolean {
        return getDeclaredTypeAliasName(element, "") != null
    }

    private fun getDeclaredTypeAliasName(element: AnnotatedElement, defaultNamespace: String): String? {
        // TODO : We may consider type aliases for Objects later.
        if (element !is Field) return null
        val dataType = element.getAnnotation(DataType::class.java) ?: return null
        if (dataType.declaresName()) {
            return dataType.qualifiedName(defaultNamespace)
        } else {
            return null
        }
    }

    private fun mapNewObjectType(element: AnnotatedElement, defaultNamespace: String, existingTypes: MutableSet<Type>): ObjectType {
        val name = deriveTypeName(element, defaultNamespace)
        val fields = mutableListOf<lang.taxi.types.Field>()
        val objectType = ObjectType(name, fields)
        existingTypes.add(objectType)
        fields.addAll(this.mapTaxiFields(typeFromElement(element), defaultNamespace, existingTypes))
        return objectType
    }


    private fun mapTaxiFields(javaClass: Class<*>, defaultNamespace: String, existingTypes: MutableSet<Type>): List<lang.taxi.types.Field> {
        return javaClass.declaredFields.map { field ->
            lang.taxi.types.Field(name = field.name,
                    type = getTaxiType(field, existingTypes, defaultNamespace),
                    nullable = isNullable(field),
                    annotations = mapAnnotations(field))
        }
    }

    private fun mapAnnotations(field: java.lang.reflect.Field): List<Annotation> {
        // TODO
        return emptyList()
    }

    private fun isNullable(field: java.lang.reflect.Field): Boolean {
        val isNotNull = field.isAnnotationPresent(NotNull::class.java) ||
                return field.isAnnotationPresent(javax.validation.constraints.NotNull::class.java)
        return !isNotNull
    }
}


