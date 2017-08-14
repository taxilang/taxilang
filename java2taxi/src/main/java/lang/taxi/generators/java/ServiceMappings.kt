package lang.taxi.generators.java

import lang.taxi.Type
import lang.taxi.TypeNames
import lang.taxi.annotations.Operation
import lang.taxi.annotations.ResponseContract
import lang.taxi.annotations.declaresName
import lang.taxi.annotations.qualifiedName
import lang.taxi.services.Constraint
import lang.taxi.services.OperationContract
import lang.taxi.services.Parameter
import lang.taxi.services.Service

interface ServiceMapper {
    fun getTaxiServices(javaClass: Class<*>, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Set<Service>
}

class DefaultServiceMapper(val constraintAnnotationMapper: ConstraintAnnotationMapper = ConstraintAnnotationMapper()) : ServiceMapper {
    override fun getTaxiServices(type: Class<*>, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Set<Service> {
        val namespace = TypeNames.deriveNamespace(type)
        val serviceName = deriveServiceName(type, namespace)
        val operations = type.methods.filter {
            it.isAnnotationPresent(Operation::class.java)
        }.map { method ->
            val operationAnnotation = method.getAnnotation(Operation::class.java)
            val name = operationAnnotation.value.orDefault(method.name)
            val params = method.parameters.map { param ->
                val paramType = typeMapper.getTaxiType(param, mappedTypes, namespace)
                val paramAnnotation = param.getAnnotation(lang.taxi.annotations.Parameter::class.java)
                Parameter(annotations = emptyList(), // todo,
                        type = paramType,
                        name = paramAnnotation?.name,
                        constraints = parseConstraints(paramAnnotation))
            }
            val returnType = typeMapper.getTaxiType(method.returnType, mappedTypes, namespace, method)
            lang.taxi.services.Operation(name,
                    parameters = params,
                    annotations = emptyList(), // TODO
                    returnType = returnType,
                    contract = OperationContract(
                            returnType = returnType,
                            returnTypeConstraints = parseConstraints(method.getAnnotation(ResponseContract::class.java))
                    ))
        }
        return setOf(Service(serviceName, operations, annotations = emptyList()))
    }

    private fun parseConstraints(contract: ResponseContract?): List<Constraint> {
        if (contract == null) {
            return emptyList()
        }
        return constraintAnnotationMapper.convert(contract)
    }

    private fun parseConstraints(paramAnnotation: lang.taxi.annotations.Parameter?): List<Constraint> {
        if (paramAnnotation == null) {
            return emptyList()
        }
        return constraintAnnotationMapper.convert(paramAnnotation.constraints.toList())
    }


    fun String.orDefault(default: String): String {
        return if (this.isEmpty()) default else this
    }

    fun deriveServiceName(element: Class<*>, defaultNamespace: String): String {
        if (element.isAnnotationPresent(lang.taxi.annotations.Service::class.java)) {
            val annotation = element.getAnnotation(lang.taxi.annotations.Service::class.java)
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
