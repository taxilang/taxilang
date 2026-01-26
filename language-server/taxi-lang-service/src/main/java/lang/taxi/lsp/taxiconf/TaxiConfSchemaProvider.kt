package lang.taxi.lsp.taxiconf

import com.typesafe.config.Config
import lang.taxi.linter.TaxiConfLinterRuleConfig
import lang.taxi.messages.Severity
import lang.taxi.packages.Credentials
import lang.taxi.packages.PluginSettings
import lang.taxi.packages.Repository
import lang.taxi.packages.TaxiPackageProject
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

/**
 * Provides schema information for taxi.conf files by inspecting
 * the TaxiPackageProject data class using reflection.
 *
 * This ensures the schema stays up-to-date as the TaxiPackageProject class evolves.
 */
class TaxiConfSchemaProvider {

   companion object {
      private val PRIMITIVE_TYPES = setOf(
         String::class,
         Int::class,
         Long::class,
         Double::class,
         Float::class,
         Boolean::class
      )

      private val IGNORED_FIELDS = setOf(
         "identifier",
         "dependencyPackages",
         "packageRootPath",
         "sourceRootPath"
      )
   }

   data class PropertySchema(
      val name: String,
      val type: PropertyType,
      val isNullable: Boolean,
      val defaultValue: Any?,
      val description: String? = null
   )

   sealed class PropertyType {
      data class Primitive(val typeName: String) : PropertyType()
      data class Object(val className: String, val properties: List<PropertySchema>) : PropertyType()
      data class Map(val keyType: String, val valueType: PropertyType) : PropertyType()
      data class List(val elementType: PropertyType) : PropertyType()
      data class Enum(val className: String, val values: kotlin.collections.List<String>) : PropertyType()
   }

   private val schemaCache = mutableMapOf<KClass<*>, kotlin.collections.List<PropertySchema>>()

   /**
    * Get the schema for taxi.conf files
    */
   fun getSchema(): kotlin.collections.List<PropertySchema> {
      return extractSchemaFromClass(TaxiPackageProject::class)
   }

   /**
    * Get property schema by path (e.g., "plugins", "linter.no-duplicate-types")
    */
   fun getPropertySchema(path: String): PropertySchema? {
      val parts = path.split(".")
      var currentSchema = getSchema()
      var result: PropertySchema? = null

      for (part in parts) {
         result = currentSchema.find { it.name == part }
         if (result == null) return null

         when (val type = result.type) {
            is PropertyType.Object -> currentSchema = type.properties
            is PropertyType.Map -> {
               // For maps, we can't navigate further by key name
               return result
            }
            else -> return result
         }
      }

      return result
   }

   private fun extractSchemaFromClass(klass: KClass<*>): kotlin.collections.List<PropertySchema> {
      if (schemaCache.containsKey(klass)) {
         return schemaCache[klass]!!
      }

      val constructor = klass.primaryConstructor ?: return emptyList()
      val parameters = constructor.parameters.associateBy { it.name }

      val properties = klass.memberProperties
         .filter { it.name !in IGNORED_FIELDS }
         .mapNotNull { prop ->
            val param = parameters[prop.name] ?: return@mapNotNull null

            PropertySchema(
               name = prop.name,
               type = extractPropertyType(prop.returnType),
               isNullable = prop.returnType.isMarkedNullable,
               defaultValue = param.isOptional.let {
                  if (it) getDefaultValue(prop) else null
               }
            )
         }

      schemaCache[klass] = properties
      return properties
   }

   private fun extractPropertyType(type: KType): PropertyType {
      val classifier = type.classifier as? KClass<*> ?: return PropertyType.Primitive("Any")

      // Check for primitives
      if (classifier in PRIMITIVE_TYPES) {
         return PropertyType.Primitive(classifier.simpleName ?: "Unknown")
      }

      // Check for known types
      return when (classifier) {
         Path::class -> PropertyType.Primitive("String")
         Config::class -> PropertyType.Map("String", PropertyType.Primitive("Any"))

         // Collections
         kotlin.collections.Map::class -> {
            val typeArgs = type.arguments
            val keyType = typeArgs.getOrNull(0)?.type?.let {
               (it.classifier as? KClass<*>)?.simpleName ?: "String"
            } ?: "String"
            val valueType = typeArgs.getOrNull(1)?.type?.let {
               extractPropertyType(it)
            } ?: PropertyType.Primitive("Any")
            PropertyType.Map(keyType, valueType)
         }

         kotlin.collections.List::class -> {
            val elementType = type.arguments.firstOrNull()?.type?.let {
               extractPropertyType(it)
            } ?: PropertyType.Primitive("Any")
            PropertyType.List(elementType)
         }

         // Enum
         else -> if (classifier.java.isEnum) {
            @Suppress("UNCHECKED_CAST")
            val enumValues = (classifier.java.enumConstants as Array<Enum<*>>).map { it.name }
            PropertyType.Enum(classifier.simpleName ?: "Unknown", enumValues)
         } else {
            // Complex object
            PropertyType.Object(
               className = classifier.simpleName ?: "Unknown",
               properties = extractSchemaFromClass(classifier)
            )
         }
      }
   }

   private fun getDefaultValue(prop: KProperty1<*, *>): Any? {
      // We need to check the primary constructor parameter for default values
      // This is a simplified implementation - in reality, we'd need to
      // instantiate a default instance or parse the bytecode
      val klass = when (prop.name) {
         "sourceRoot" -> return "."
         "output" -> return "dist/"
         "dependencies" -> return emptyMap<String, String>()
         "repositories" -> return emptyList<Repository>()
         "plugins" -> return emptyMap<String, Config>()
         "pluginSettings" -> return PluginSettings()
         "publishToRepository" -> return null
         "credentials" -> return emptyList<Credentials>()
         "linter" -> return emptyMap<String, TaxiConfLinterRuleConfig>()
         "additionalSources" -> return emptyMap<String, String>()
         "taxiConfFile" -> return null
         else -> null
      }
      return klass
   }

   /**
    * Get all possible top-level keys in taxi.conf
    */
   fun getTopLevelKeys(): kotlin.collections.List<String> {
      return getSchema().map { it.name }
   }

   /**
    * Get all properties of a complex type by class name
    */
   fun getObjectProperties(className: String): kotlin.collections.List<PropertySchema> {
      val klass = when (className) {
         "Repository" -> Repository::class
         "Credentials" -> Credentials::class
         "PluginSettings" -> PluginSettings::class
         "TaxiConfLinterRuleConfig" -> TaxiConfLinterRuleConfig::class
         else -> return emptyList()
      }
      return extractSchemaFromClass(klass)
   }

   /**
    * Get enum values for Severity (used in linter configuration)
    */
   fun getSeverityValues(): kotlin.collections.List<String> {
      return Severity.entries.map { it.name }
   }
}
