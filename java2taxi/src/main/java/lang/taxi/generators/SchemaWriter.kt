package lang.taxi.generators

import lang.taxi.TaxiDocument
import lang.taxi.Type
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
            val typeString = types.joinToString("\n\n")

            //return:
            """namespace ${namespacedDoc.namespace}

$typeString
"""
        }
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
