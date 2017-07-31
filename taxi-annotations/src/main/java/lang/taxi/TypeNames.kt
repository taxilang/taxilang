package lang.taxi

import lang.taxi.annotations.declaresName
import lang.taxi.annotations.hasNamespace
import lang.taxi.annotations.namespace
import lang.taxi.annotations.qualifiedName
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Parameter

object TypeNames {
    fun deriveNamespace(javaClass: Class<*>): String {
        val dataType = javaClass.getAnnotation(lang.taxi.annotations.DataType::class.java)
        val service = javaClass.getAnnotation(lang.taxi.annotations.Service::class.java)
        val namespaceAnnotation = javaClass.getAnnotation(lang.taxi.annotations.Namespace::class.java)

        return when {
            namespaceAnnotation != null -> namespaceAnnotation.value
            dataType != null && dataType.hasNamespace() -> dataType.namespace()!!
            service != null && service.hasNamespace() -> dataType.namespace()!!
            else -> javaClass.`package`.name
        }
    }

    fun deriveTypeName(type: Class<*>): String {
        val namespace = deriveNamespace(type)
        return deriveTypeName(type, namespace)
    }

    fun declaresDataType(element:AnnotatedElement):Boolean {
        return element.isAnnotationPresent(lang.taxi.annotations.DataType::class.java)
    }
    fun deriveTypeName(element: AnnotatedElement, defaultNamespace: String): String {
        if (declaresDataType(element)) {
            val annotation = element.getAnnotation(lang.taxi.annotations.DataType::class.java)
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
            is Parameter -> element.type
            else -> error("Unhandled type : $element")
        }
        return type
    }
}
