package lang.taxi.generators

import lang.taxi.TaxiDocument
import lang.taxi.Type
import lang.taxi.types.Field
import lang.taxi.types.ObjectType
import lang.taxi.types.TypeAlias


class SchemaWriter {
    fun generateSchemas(docs: List<TaxiDocument>): List<String> {
        return docs.map { generateSchema(it) }
    }

    private fun generateSchema(doc: TaxiDocument): String {
        val types = doc.types.map { generateTypeDeclaration(it) }
        return types.joinToString("\n\n")
    }

    private fun generateTypeDeclaration(type: Type): String {
        return when (type) {
            is ObjectType -> generateObjectTypeDeclaration(type)
            is TypeAlias -> generateTypeAliasDeclaration(type)
            else -> TODO()
        }
    }

    private fun generateTypeAliasDeclaration(type: TypeAlias): String {
        return "type alias ${type.qualifiedName} as ${type.aliasType!!.qualifiedName}"
    }

    private fun generateObjectTypeDeclaration(type: ObjectType): String {

        val fieldDelcarations = type.fields.map { generateFieldDeclaration(it) }.joinToString("\n").prependIndent()
        return """type ${type.qualifiedName} {
$fieldDelcarations
}"""
    }

    private fun generateFieldDeclaration(field: Field): String {
        return "${field.name} : ${field.type.qualifiedName}"
    }
}