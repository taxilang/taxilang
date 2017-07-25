package lang.taxi.generators.java

import lang.taxi.Operation
import lang.taxi.Type
import lang.taxi.declaresName
import lang.taxi.qualifiedName
import lang.taxi.services.Parameter
import lang.taxi.services.Service

interface ServiceMapper {
    fun getTaxiServices(javaClass: Class<*>, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Set<Service>
}

class DefaultServiceMapper : ServiceMapper {
    override fun getTaxiServices(type: Class<*>, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Set<Service> {
        val namespace = Namespaces.deriveNamespace(type)
        val serviceName = deriveServiceName(type,namespace)
        val operations = type.methods.filter {
            it.isAnnotationPresent(Operation::class.java)
        }.map { method ->
            val annotation = method.getAnnotation(Operation::class.java)
            val name = annotation.value.orDefault(method.name)
            val params = method.parameters.map { param ->
                val paramType = typeMapper.getTaxiType(param, mappedTypes, namespace)
                Parameter(annotations = emptyList(), // todo,
                        type = paramType)
            }
            val returnType = typeMapper.getTaxiType(method.returnType, mappedTypes, namespace)
            lang.taxi.services.Operation(name,
                    parameters = params,
                    annotations = emptyList(), // TODO
                    returnType = returnType)
        }
        return setOf(Service(serviceName, operations, annotations = emptyList()))
    }


    fun String.orDefault(default: String): String {
        return if (this.isEmpty()) default else this
    }

    fun deriveServiceName(element: Class<*>, defaultNamespace: String): String {
        if (element.isAnnotationPresent(lang.taxi.Service::class.java)) {
            val annotation = element.getAnnotation(lang.taxi.Service::class.java)
            if (annotation.declaresName()) {
                return annotation.qualifiedName(defaultNamespace)
            }
        }

        // If it's an inner class, trim the qualifier
        // This may cause problems with duplicates, but let's encourage
        // peeps to solve that via the DataType annotation.
        val typeName = element.simpleName.split("$").last()
        return "$defaultNamespace.$typeName"
    }
}
