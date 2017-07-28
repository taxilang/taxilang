package lang.taxi

import lang.taxi.annotations.*
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Parameter

object TypeNames {
    fun deriveNamespace(javaClass: Class<*>): String {
        val dataType = javaClass.getAnnotation(DataType::class.java)
        val service = javaClass.getAnnotation(Service::class.java)
        val namespaceAnnotation = javaClass.getAnnotation(Namespace::class.java)
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
            is Parameter -> element.type
            else -> error("Unhandled type : $element")
        }
        return type
    }
}
