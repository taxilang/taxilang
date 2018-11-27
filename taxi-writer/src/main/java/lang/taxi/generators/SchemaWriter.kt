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
    private val formatter = SourceFormatter()
    fun generateSchemas(docs: List<TaxiDocument>): List<String> {
        return docs.flatMap { generateSchema(it) }
    }

    private fun generateSchema(doc: TaxiDocument): List<String> {
        return doc.toNamespacedDocs().map { namespacedDoc ->
            val types = namespacedDoc.types.map { generateTypeDeclaration(it, namespacedDoc.namespace) }
            val typesTaxiString = types.joinToString("\n\n").trim()

            val servicesTaxiString = namespacedDoc.services.map { generateServiceDeclaration(it, namespacedDoc.namespace) }.joinToString("\n").trim()
            //return:
            val rawTaxi = """namespace ${namespacedDoc.namespace} {

${typesTaxiString.prependIndent()}

${servicesTaxiString.prependIndent()}
}
"""
            formatter.format(rawTaxi)
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
            val paramDeclaration = typeAsTaxi(param.type, namespace)
            paramAnnotations + paramName + paramDeclaration + constraintString
        }.joinToString(", ")
        val returnDeclaration = if (operation.returnType != VoidType.VOID) {
            val returnType = typeAsTaxi(operation.returnType, namespace)
            val returnContract = if (operation.contract != null) generateReturnContract(operation.contract!!) else ""
            " : $returnType$returnContract"
        } else {
            ""
        }

        val operationName = operation.name

        val annotations = generateAnnotations(operation)
        return """$annotations
operation $operationName( $params )$returnDeclaration""".trimIndent().trim()
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
        val aliasTypeString = typeAsTaxi(aliasType, currentNamespace)

        return "type alias ${type.toQualifiedName().typeName.reservedWordEscaped()} as $aliasTypeString"
    }

    private fun generateObjectTypeDeclaration(type: ObjectType, currentNamespace: String): String {

        val fieldDelcarations = type.fields.map { generateFieldDeclaration(it, currentNamespace) }.joinToString("\n").prependIndent()
        val modifiers = type.modifiers.map { it.token }.joinToString(" ")
        val inheritanceString = getInheritenceString(type)

        return """$modifiers type ${type.toQualifiedName().typeName.reservedWordEscaped()}$inheritanceString {
$fieldDelcarations
}"""
    }

    private fun getInheritenceString(type: ObjectType): String {
        return if (type.inheritsFromNames.isEmpty()) {
            ""
        } else {
            " inherits ${type.inheritsFromNames.joinToString(",")}"
        }
    }

    private fun generateFieldDeclaration(field: Field, currentNamespace: String): String {
        val fieldType = field.type
        val fieldTypeString = typeAsTaxi(fieldType, currentNamespace)

        val constraints = constraintString(field.constraints)

        return "${field.name.reservedWordEscaped()} : $fieldTypeString $constraints".trim()
    }

    private fun typeAsTaxi(type: Type, currentNamespace: String): String {
        return when (type) {
            is ArrayType -> typeAsTaxi(type.type, currentNamespace) + "[]"
            else -> type.toQualifiedName().qualifiedRelativeTo(currentNamespace)
        }
    }
}

private fun Any.inQuotesIfNeeded(): String {
    return when (this) {
        is Boolean -> this.toString()
        is Number -> this.toString()
        else -> "\"${this}\""
    }
}
