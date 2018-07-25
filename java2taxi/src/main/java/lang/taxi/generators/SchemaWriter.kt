package lang.taxi.generators

import lang.taxi.Annotatable
import lang.taxi.TaxiDocument
import lang.taxi.Type
import lang.taxi.services.Constraint
import lang.taxi.services.Operation
import lang.taxi.services.OperationContract
import lang.taxi.services.Service
import lang.taxi.types.*


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
${generateAnnotations(service)}
service ${service.toQualifiedName().qualifiedRelativeTo(namespace)} {
$operations
}""".trim()
    }

    private fun generateAnnotations(annotatedElement: Annotatable): String {
        return annotatedElement.annotations.map { annotation ->
            if (annotation.parameters.isEmpty()) {
                "@${annotation.name}"
            } else {
                val annotationParams = annotation.parameters.map { "${it.key} = ${it.value!!.inQuotesIfNeeded()}" }.joinToString(" , ")
                "@${annotation.name}($annotationParams)"
            }
        }.joinToString("\n")
    }

    private fun generateOperationDeclaration(operation: Operation, namespace: String): String {
        val params = operation.parameters.map { param ->
            val constraintString = constraintString(param.constraints)
            val paramAnnotations = generateAnnotations(param) + " "

            val paramName = if (!param.name.isNullOrEmpty()) param.name + " : " else ""
            val paramDeclaration = param.type.toQualifiedName().qualifiedRelativeTo(namespace)
            paramAnnotations + paramName + paramDeclaration + constraintString
        }.joinToString(", ")
        val returnType = operation.returnType.toQualifiedName().qualifiedRelativeTo(namespace)
        val returnContract = if (operation.contract != null) generateReturnContract(operation.contract!!) else ""
        val operationName = operation.name

        val annotations = generateAnnotations(operation)
        return """$annotations
operation $operationName( $params ) : $returnType$returnContract""".trimIndent().trim()
    }

    private fun generateReturnContract(contract: OperationContract): String {
        return constraintString(contract.returnTypeConstraints)
    }

    private fun constraintString(constraints: List<Constraint>): String {
        if (constraints.isEmpty()) {
            return ""
        }
        val constraintString = constraints
                .map { it.asTaxi() }
                .joinToString(", ")

        return "( $constraintString )"
    }

    private fun generateTypeDeclaration(type: Type, currentNamespace: String): String {
        return when (type) {
            is ObjectType -> generateObjectTypeDeclaration(type, currentNamespace)
            is TypeAlias -> generateTypeAliasDeclaration(type, currentNamespace)
            is EnumType -> generateEnumDeclaration(type, currentNamespace)
            else -> TODO("No schema writer defined for type $type")
        }
    }

    private fun generateEnumDeclaration(type: EnumType, currentNamespace: String): String {
        val enumValueDeclarations = type.values.map { enumValue ->
            "${generateAnnotations(enumValue)} ${enumValue.name}".trim()
        }.joinToString(",\n").prependIndent()
        return """
${generateAnnotations(type)} enum ${type.toQualifiedName().typeName} {
$enumValueDeclarations
}
        """
    }

    private fun generateTypeAliasDeclaration(type: TypeAlias, currentNamespace: String): String {
        val aliasType = type.aliasType!!
        val aliasTypeString = when(aliasType) {
            is ArrayType -> aliasType.type.toQualifiedName().qualifiedRelativeTo(currentNamespace) + "[]"
            else -> aliasType.toQualifiedName().qualifiedRelativeTo(currentNamespace)
        }

        return "type alias ${type.toQualifiedName().typeName} as $aliasTypeString"
    }

    private fun generateObjectTypeDeclaration(type: ObjectType, currentNamespace: String): String {

        val fieldDelcarations = type.fields.map { generateFieldDeclaration(it, currentNamespace) }.joinToString("\n").prependIndent()
        val modifiers = type.modifiers.map { it.token }.joinToString(" ")
        return """$modifiers type ${type.toQualifiedName().typeName} {
$fieldDelcarations
}"""
    }

    private fun generateFieldDeclaration(field: Field, currentNamespace: String): String {
        val fieldType = field.type
        val fieldTypeString = when (fieldType) {
            is ArrayType -> fieldType.type.toQualifiedName().qualifiedRelativeTo(currentNamespace) + "[]"
            else -> fieldType.toQualifiedName().qualifiedRelativeTo(currentNamespace)
        }

        return "${field.name} : $fieldTypeString"
    }
}

private fun Any.inQuotesIfNeeded(): String {
    return when (this) {
        is Boolean -> this.toString()
        is Number -> this.toString()
        else -> "\"${this}\""
    }
}
