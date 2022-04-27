package lang.taxi.generators.java

import com.google.common.annotations.VisibleForTesting
import lang.taxi.TaxiDocument
import lang.taxi.annotations.DataType
import lang.taxi.annotations.Service
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.SchemaWriter
import lang.taxi.generators.TaxiCodeGenerator
import lang.taxi.types.Type
import org.reflections8.Reflections
import org.reflections8.util.ConfigurationBuilder

class TaxiGenerator(
   val typeMapper: TypeMapper = DefaultTypeMapper(),
   private var serviceMapper: ServiceMapper = DefaultServiceMapper(),
   private val schemaWriter: SchemaWriter = SchemaWriter()
) : TaxiCodeGenerator {

   private val classes = mutableSetOf<Class<*>>()
   private val generatedTypes = mutableSetOf<Type>()
   private val services = mutableSetOf<lang.taxi.services.Service>()

   fun addExtension(extension: TaxiGeneratorExtension): TaxiGenerator {
      serviceMapper = serviceMapper
         .addServiceExtensions(extension.serviceMapperExtensions)
         .addOperationExtensions(extension.operationMapperExtensions)
      return this
   }


   fun forPackage(classInPackage: Class<*>): TaxiGenerator {
      val packageName = classInPackage.`package`.name
      val reflections = Reflections(
         ConfigurationBuilder()
            .forPackages(packageName)
      )
      val dataTypes = reflections.getTypesAnnotatedWith(DataType::class.java)
         .filter { it.`package`.name.startsWith(packageName) }
      val services = reflections.getTypesAnnotatedWith(Service::class.java)
         .filter { it.`package`.name.startsWith(packageName) }
      val classes = dataTypes + services
      return forClasses(classes.toList())
   }

   fun forClasses(vararg classes: Class<*>): TaxiGenerator {
      this.classes.addAll(classes)
      return this
   }

   fun forClasses(classes: List<Class<*>>): TaxiGenerator {
      this.classes.addAll(classes)
      return this
   }

   @VisibleForTesting
   internal fun generateModel(): TaxiDocument {
      generateTaxiTypes()
      generateTaxiServices()
      return TaxiDocument(generatedTypes.toSet(), services.toSet(), emptySet())
   }

   private fun generateTaxiServices() {
      services.addAll(classesWithAnnotation(Service::class.java)
         .flatMap { javaClass ->
            serviceMapper.getTaxiServices(javaClass, this.typeMapper, this.generatedTypes)
         })
   }

   private fun generateTaxiTypes() {
      classesWithAnnotation(DataType::class.java)
         .forEach { javaClass ->
            typeMapper.getTaxiType(javaClass, generatedTypes)
         }
   }

   private fun classesWithAnnotation(annotation: Class<out Annotation>) = this.classes
      .filter { it.isAnnotationPresent(annotation) }


   fun generateAsStrings(): List<String> {
      val taxiDocs = generateModel()
      return schemaWriter.generateSchemas(listOf(taxiDocs))
   }

   override fun generate(): List<GeneratedTaxiCode> {
      return listOf(
         GeneratedTaxiCode(
            generateAsStrings(),
            emptyList()
         )
      )
   }


}
