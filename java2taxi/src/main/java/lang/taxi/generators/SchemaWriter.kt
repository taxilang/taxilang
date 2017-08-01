package lang.taxi.generators

import lang.taxi.TaxiDocument
import lang.taxi.Type
import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.types.Field
import lang.taxi.types.ObjectType
import lang.taxi.types.TypeAlias


class SchemaWriter {
    fun generateSchemas(docs: List<TaxiDocument>): List<String> {
        return docs.flatMap { generateSchema(it) }
    }

    private fun generateSchema(doc: TaxiDocument): List<String> {
        return doc.toNamespacedDocs().map { namespacedDoc ->
            val types = namespacedDoc.types.map { generateTypeDeclaration(it, namespacedDoc.namespace) }
            val typesTaxiString = types.joinToString("\n\n").trim()

            val servicesTaxiString = namespacedDoc.services.map { generateServiceDeclaration(it, namespacedDoc.namespace) }.joinToString("\n").trim()
            //return:
            """namespace ${namespacedDoc.namespace} {

${typesTaxiString.prependIndent()}

${servicesTaxiString.prependIndent()}
}
"""
        }
    }

    private fun generateServiceDeclaration(service: Service, namespace: String): String {
        val operations = service.operations
                .map { generateOperationDeclaration(it, namespace) }
                .joinToString("\n").prependIndent()
//        service PersonService {
//            @Get("/foo/bar")
//            operation getPerson(@AnotherAnnotation PersonId):Person
//        }

        return """
service ${service.toQualifiedName().qualifiedRelativeTo(namespace)} {
$operations
}"""
    }

    private fun generateOperationDeclaration(operation: Operation, namespace: String): String {
        val params = operation.parameters.map { it.type.toQualifiedName().qualifiedRelativeTo(namespace) }.joinToString(", ")
        val returnType = operation.returnType.toQualifiedName().qualifiedRelativeTo(namespace)
        val operationName = operation.name

        // TODO : Annotations
        return "operation $operationName($params) : $returnType"
    }

    private fun generateTypeDeclaration(type: Type, currentNamespace: String): String {
        return when (type) {
            is ObjectType -> generateObjectTypeDeclaration(type, currentNamespace)
            is TypeAlias -> generateTypeAliasDeclaration(type, currentNamespace)
            else -> TODO()
        }
    }

    private fun generateTypeAliasDeclaration(type: TypeAlias, currentNamespace: String): String {
        return "type alias ${type.toQualifiedName().typeName} as ${type.aliasType!!.toQualifiedName().qualifiedRelativeTo(currentNamespace)}"
    }

    private fun generateObjectTypeDeclaration(type: ObjectType, currentNamespace: String): String {

        val fieldDelcarations = type.fields.map { generateFieldDeclaration(it, currentNamespace) }.joinToString("\n").prependIndent()
        return """type ${type.toQualifiedName().typeName} {
$fieldDelcarations
}"""
    }

    private fun generateFieldDeclaration(field: Field, currentNamespace: String): String {
        return "${field.name} : ${field.type.toQualifiedName().qualifiedRelativeTo(currentNamespace)}"
    }
}
