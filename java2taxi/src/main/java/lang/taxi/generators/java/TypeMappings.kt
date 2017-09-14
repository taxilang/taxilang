package lang.taxi.generators.java

import lang.taxi.Type
import lang.taxi.TypeNames
import lang.taxi.annotations.DataType
import lang.taxi.annotations.ParameterType
import lang.taxi.annotations.declaresName
import lang.taxi.annotations.qualifiedName
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.log
import org.jetbrains.annotations.NotNull
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

interface ExtensionProvider
interface ServiceExtensionProvider : ExtensionProvider


interface TypeMapper {
    fun getTaxiType(element: Class<*>, existingTypes: MutableSet<Type>): Type {
        val namespace = TypeNames.deriveNamespace(element)
        return getTaxiType(element, existingTypes, namespace)
    }

    fun getTaxiType(element: AnnotatedElement, existingTypes: MutableSet<Type>, defaultNamespace: String, containingMember: AnnotatedElement? = null): Type
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

    /**
     * Only considers the class itself, and not any declared
     * annotationed datatypes
     */
    fun isClassTaxiPrimitive(rawType: Class<*>): Boolean {
        return isTaxiPrimitive(rawType.typeName)
    }

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

    // A containingMember is typically a function, which has declared a returnType.
    // Since the annotations can't go directly on the return type, they go on the function itself,
    // meaning we need to evaluate the function when considering the type.
    override fun getTaxiType(element: AnnotatedElement, existingTypes: MutableSet<Type>, defaultNamespace: String, containingMember: AnnotatedElement?): Type {
        val elementType = TypeNames.typeFromElement(element)

        if (isTaxiPrimitiveWithoutAnnotation(element)) {
            if (containingMember == null) return PrimitiveTypes.getTaxiPrimitive(elementType.typeName)
            if (isTaxiPrimitiveWithoutAnnotation(containingMember)) {
                // If the type has a DataType annotation, we use that
                // Otherwise, return the primitive
                return PrimitiveTypes.getTaxiPrimitive(elementType.typeName)
            }
        }

        val targetTypeName = getTargetTypeName(element, defaultNamespace, containingMember)

        if (declaresTypeAlias(element)) {
            val typeAliasName = getDeclaredTypeAliasName(element, defaultNamespace)!!
            val aliasedTaxiType = getTypeDeclaredOnClass(element, existingTypes)
            if (typeAliasName != aliasedTaxiType.qualifiedName) {
                return getOrCreateTypeAlias(element, typeAliasName, existingTypes)
            } else {
                log().warn("Element $element declares a redundant type alias of $typeAliasName, which is already " +
                        "declared on the type itself.  Consider removing this name (replacing it with an " +
                        "empty @DataType annotation, otherwise future refactoring bugs are likely to occur")
            }
        }

        if (containingMember != null && declaresTypeAlias(containingMember)) {
            val typeAliasName = getDeclaredTypeAliasName(containingMember, defaultNamespace)!!
            return getOrCreateTypeAlias(containingMember, typeAliasName, existingTypes)
        }

//        if (isImplicitAliasForPrimitiveType(targetTypeName,element)) {
//
//        }
        if (PrimitiveTypes.isTaxiPrimitive(targetTypeName)) {
            return PrimitiveTypes.getTaxiPrimitive(targetTypeName)
        }


        val existing = existingTypes.findByName(targetTypeName)
        if (existing != null) {
            return existing
        }

        return mapNewObjectType(element, defaultNamespace, existingTypes)
    }

//    // This is typically for functions who's parameters are
//    // a primitive type (eg., String).  These
//    private fun isImplicitAliasForPrimitiveType(targetTypeName: String, element: AnnotatedElement): Boolean {
//
//    }

    private fun isTaxiPrimitiveWithoutAnnotation(element: AnnotatedElement?): Boolean {
        if (element == null)
            return false
        return (!TypeNames.declaresDataType(element) && PrimitiveTypes.isClassTaxiPrimitive(TypeNames.typeFromElement(element)))
    }

    private fun getOrCreateTypeAlias(element: AnnotatedElement, typeAliasName: String, existingTypes: MutableSet<Type>): TypeAlias {
        val existingAlias = existingTypes.findByName(typeAliasName)
        if (existingAlias != null) {
            return existingAlias as TypeAlias
        } else {
            val aliasedTaxiType = getTypeDeclaredOnClass(element, existingTypes)
            val typeAlias = TypeAlias(typeAliasName, aliasedTaxiType)
            existingTypes.add(typeAlias)
            return typeAlias
        }
    }

    fun getTypeDeclaredOnClass(element: AnnotatedElement, existingTypes: MutableSet<Type>): Type {
        val rawType = TypeNames.typeFromElement(element)
        return getTaxiType(rawType, existingTypes)
    }

    private fun declaresTypeAlias(element: AnnotatedElement): Boolean {
        return getDeclaredTypeAliasName(element, "") != null
    }

    private fun getDeclaredTypeAliasName(element: AnnotatedElement, defaultNamespace: String): String? {
        // TODO : We may consider type aliases for Objects later.
        if (element !is Field && element !is Parameter && element !is Method) return null
        val dataType = element.getAnnotation(DataType::class.java) ?: return null
        if (dataType.declaresName()) {
            return dataType.qualifiedName(defaultNamespace)
        } else {
            return null
        }
    }

    private fun mapNewObjectType(element: AnnotatedElement, defaultNamespace: String, existingTypes: MutableSet<Type>): ObjectType {
        val name = getTargetTypeName(element, defaultNamespace)
        val fields = mutableListOf<lang.taxi.types.Field>()
        val modifiers = if (element.getAnnotation(ParameterType::class.java) != null) {
            listOf(Modifier.PARAMETER_TYPE)
        } else emptyList()
        val definition = ObjectTypeDefinition(fields, emptyList(), modifiers)
        val objectType = ObjectType(name, definition)

        // Note: Add the type while it's empty, and then collect the fields.
        // This allows circular references to resolve
        existingTypes.add(objectType)
        fields.addAll(this.mapTaxiFields(lang.taxi.TypeNames.typeFromElement(element), defaultNamespace, existingTypes))
        return objectType
    }

    private fun getTargetTypeName(element: AnnotatedElement, defaultNamespace: String, containingMember: AnnotatedElement? = null): String {
        val rawType = TypeNames.typeFromElement(element)

        if (containingMember != null && TypeNames.declaresDataType(containingMember)) {
            return TypeNames.deriveTypeName(containingMember, defaultNamespace)
        }
        if (!TypeNames.declaresDataType(element) && PrimitiveTypes.isClassTaxiPrimitive(rawType)) {
            return PrimitiveTypes.getTaxiPrimitive(rawType.typeName).qualifiedName
        }
        return TypeNames.deriveTypeName(element, defaultNamespace)
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


