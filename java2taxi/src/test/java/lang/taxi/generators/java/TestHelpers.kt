package lang.taxi.generators.java

import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.Type
import lang.taxi.services.Service
import java.lang.AssertionError

object TestHelpers {
    fun expectToCompileTheSame(generated: List<String>, expected: String) {
        return expectToCompileTheSame(generated, listOf(expected))
    }
    fun expectToCompileTheSame(generated: List<String>, expected: List<String>) {
        val generatedDoc = Compiler.forStrings(generated).compile()
        val expectedDoc = Compiler.forStrings(expected).compile()
        if (generatedDoc == expectedDoc) return

        val typeErros = expectedDoc.types.flatMap { type -> collateTypeErrors(type, generatedDoc) }
        val serviceErrors = expectedDoc.services.flatMap { service -> collateServiceErrors(service,generatedDoc) }
        val errors = typeErros + serviceErrors
        throw AssertionError("Generated docs did not match expected.  Errors: \n" + errors.joinToString("\n"))
    }

    private fun collateTypeErrors(type: Type, generatedDoc: TaxiDocument): List<String> {
        if (!generatedDoc.containsType(type.qualifiedName)) {
            return listOf("Type ${type.qualifiedName} not present")
        }
        val generatedType = generatedDoc.type(type.qualifiedName)
        val errors = mutableListOf<String>()
        if (type != generatedType)
            return listOf("Type ${type.qualifiedName} is not the same as expected")
        else
            return emptyList()
    }

    private fun collateServiceErrors(service:Service, generatedDoc:TaxiDocument):List<String> {
        if (!generatedDoc.containsService(service.qualifiedName)) {
            return listOf("Service ${service.qualifiedName} not present")
        }
        val generatedService = generatedDoc.service(service.qualifiedName)
        return service.operations.flatMap { operation ->

            if (!generatedService.containsOperation(operation.name)) {
                listOf("Operation ${operation.name} is not present")
            } else {
                val errors = mutableListOf<String>()
                val generatedOperation = generatedService.operation(operation.name)
                if (generatedOperation.contract != operation.contract) errors.add("Contract on ${operation.name} differs from expected")
                if (generatedOperation.parameters != operation.parameters) errors.add("Parameters on ${operation.name} differs from expected")
                if (generatedOperation.returnType != operation.returnType) errors.add("Return type on ${operation.returnType} differs from expected")
                if (generatedOperation.annotations != operation.annotations) errors.add("Annotations on ${operation.name} differs from expected")
                errors
            }
        }
    }
}
