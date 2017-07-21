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

    private fun generateModel(): List<TaxiDocument> {
        // Note : Use a forEach, rather than map here.
        // We need to stick partially built types into the
        // 'buildTypes' set, so that when types are self-referential,
        // we don't end up in an infinite loop.
        return this.classes.map { javaClass ->
            val taxiType = typeMapper.getTaxiType(javaClass, generatedTypes)
            TaxiDocument(namespace = null, types = generatedTypes.toList(), services = emptyList())
        }
    }


    fun generateAsStrings(): List<String> {
        val taxiDocs = generateModel()
        return schemaWriter.generateSchemas(taxiDocs)
    }


}
