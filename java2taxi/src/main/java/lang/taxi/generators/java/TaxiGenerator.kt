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

   private val extensions = mutableListOf<TaxiGeneratorExtension>(DefaultTaxiExtension)

   fun addExtension(extension: TaxiGeneratorExtension): TaxiGenerator {
      this.extensions.add(extension)
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
      val classes = extensions.flatMap {
         it.getClassesToScan(
            reflections, packageName
         )
      }

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
      val serviceClassesToGenerate = extensions.flatMap { extension ->
         classes.filter { extension.isServiceType(it) }
      }.distinct()
      val generatedServices = serviceClassesToGenerate.flatMap { clazz ->
         serviceMapper.getTaxiServices(
            clazz,
            this.typeMapper,
            this.generatedTypes
         )
      }
      services.addAll(generatedServices)
   }

   private fun generateTaxiTypes() {
      val typeClassesToGenerate = extensions.flatMap { extension ->
         classes.filter { extension.shouldGenerateTaxiType(it) }
      }

      typeClassesToGenerate.forEach { clazz ->
         typeMapper.getTaxiType(clazz, generatedTypes)
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

object DefaultTaxiExtension : TaxiGeneratorExtension {
   override fun getClassesToScan(reflections: Reflections, packageName: String): List<Class<*>> {
      val dataTypes = reflections.getTypesAnnotatedWith(DataType::class.java)
         .filter { it.`package`.name.startsWith(packageName) }
      val services = reflections.getTypesAnnotatedWith(Service::class.java)
         .filter { it.`package`.name.startsWith(packageName) }
      return (dataTypes + services).distinct()
   }

   override val isServiceType: (Class<*>) -> Boolean
      get() {
         return { clazz -> clazz.isAnnotationPresent(Service::class.java) }
      }

   override val shouldGenerateTaxiType: (Class<*>) -> Boolean
      get() = { clazz -> clazz.isAnnotationPresent(DataType::class.java) }
}
