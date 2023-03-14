package lang.taxi

import lang.taxi.annotations.Namespace
import lang.taxi.annotations.declaresName
import lang.taxi.annotations.hasNamespace
import lang.taxi.annotations.namespace
import lang.taxi.annotations.qualifiedName
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

abstract class TypeReference<T> : Comparable<TypeReference<T>> {
    val type: Type =
            (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]

    override fun compareTo(other: TypeReference<T>) = 0
}

object TypeNames {
    fun deriveNamespace(javaClass: Class<*>): String {
        val dataType = javaClass.getAnnotation(lang.taxi.annotations.DataType::class.java)
        val service = javaClass.getAnnotation(lang.taxi.annotations.Service::class.java)
        val namespaceAnnotation = javaClass.getAnnotation(lang.taxi.annotations.Namespace::class.java)

        return when {
            namespaceAnnotation != null -> namespaceAnnotation.value
            dataType != null && dataType.hasNamespace() -> dataType.namespace()!!
            service != null && service.hasNamespace() -> service.namespace()!!
            else -> javaClass.`package`?.name ?: ""
        }
    }

    inline fun <reified T> deriveTypeName(): String {
        val typeRef = object : TypeReference<T>() {}
        return deriveTypeName(typeRef)
    }

    fun deriveTypeName(typeReference: TypeReference<*>): String {
        val type = typeReference.type
        if (type is ParameterizedType) {
            val rawType = type.rawType as Class<*>
            if (Collection::class.java.isAssignableFrom(rawType)) {
                val memberType = type.actualTypeArguments[0] as WildcardType
                val memberTypeClass = memberType.upperBounds[0] as Class<*>
                val collectionTypeName = deriveTypeName(memberTypeClass)
                return "lang.taxi.Array<$collectionTypeName>"
            } else {
                return deriveTypeName(rawType)
            }
        } else {
            return deriveTypeName(type as Class<*>)
        }
    }

    fun deriveTypeName(type: Class<*>): String {
        val namespace = deriveNamespace(type)
        return deriveTypeName(type, namespace)
    }

    fun declaresDataType(element: AnnotatedElement): Boolean {
        return element.isAnnotationPresent(lang.taxi.annotations.DataType::class.java)
    }


   fun deriveTypeName(element: AnnotatedElement, defaultNamespace: String): String {
        if (declaresDataType(element)) {
            val typeNameOnElement = detectDeclaredTypeName(element, defaultNamespace)
            if (typeNameOnElement != null) return typeNameOnElement
        }

        val type = typeFromElement(element)
        if (declaresDataType(type)) {
            val typeNameOnType = detectDeclaredTypeName(type, defaultNamespace)
            if (typeNameOnType != null) return typeNameOnType
        }

        // If it's an inner class, trim the qualifier
        // This may cause problems with duplicates, but let's encourage
        // peeps to solve that via the DataType annotation.
        val typeName = type.simpleName.split("$").last()

        // TODO : Why did I think this needed to use a defaultNamespace, rather than the
        // namespace the class is declared in?
        // Note : This is probably why some types are appearing under "java.xxxx" namespaces,
        // as the 'defaultNamespace' does mutate depending on where in the parsing process we are.
//        return "$defaultNamespace.$typeName"

        val packageName = type.`package`.name
        val namespace = getDeclaredNamespace(element) ?: packageName
        return "$namespace.$typeName"
    }

    private fun detectDeclaredTypeName(element: AnnotatedElement, defaultNamespace: String): String? {
        val annotation = element.getAnnotation(lang.taxi.annotations.DataType::class.java)
        if (annotation.declaresName()) {
            return annotation.qualifiedName(defaultNamespace)
        }
        return null
    }

    private fun getDeclaredNamespace(element: AnnotatedElement): String? {
        val target = typeFromElement(element)

        val annotation = target.getAnnotation(Namespace::class.java) ?: return null
        return annotation.value
    }

    fun typeFromElement(element: AnnotatedElement): Class<*> {
        val type = when (element) {
            is AnnotatedElementWrapper -> typeFromElement(element.delegate)
            is Class<*> -> element
            is Field -> element.type
            is Parameter -> element.type
            is Method -> element.returnType
            else -> error("Unhandled type : $element")
        }
        return type
    }
}

interface AnnotatedElementWrapper {
    val delegate: AnnotatedElement
}
