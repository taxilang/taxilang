package lang.taxi.generators.java

import lang.taxi.TaxiDocument
import lang.taxi.Type
import lang.taxi.generators.SchemaWriter

class TaxiGenerator(val typeMapper: TypeMapper = DefaultTypeMapper(), val schemaWriter: SchemaWriter = SchemaWriter()) {
    private val classes = mutableSetOf<Class<*>>()
    private val generatedTypes = mutableSetOf<Type>()
    fun forClasses(vararg classes: Class<*>): TaxiGenerator {
        this.classes.addAll(classes)
        return this
    }

    private fun generateModel(): TaxiDocument {
        this.classes.forEach { javaClass ->
            typeMapper.getTaxiType(javaClass, generatedTypes)
        }

        return TaxiDocument(generatedTypes.toList(), services = emptyList())
    }


    fun generateAsStrings(): List<String> {
        val taxiDocs = generateModel()
        return schemaWriter.generateSchemas(listOf(taxiDocs))
    }


}
