package lang.taxi.lsp.hocon

import com.typesafe.config.Config
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

/**
 * Generic schema provider for HOCON configuration files using Kotlin reflection.
 *
 * Extracts schema information from any Kotlin data class to provide:
 * - Property names, types, and nullability
 * - Default values (when available)
 * - Support for primitives, collections, nested objects, and enums
 *
 * This ensures schema information stays synchronized with the actual Kotlin types.
 *
 * @param T The Kotlin type representing the HOCON configuration
 */
class HoconSchemaProvider<T : Any>(
   private val klass: KClass<T>,
   private val ignoredFields: Set<String> = emptySet()
) {

   companion object {
      private val PRIMITIVE_TYPES = setOf(
         String::class,
         Int::class,
         Long::class,
         Double::class,
         Float::class,
         Boolean::class
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
      data class Object(val className: String, val klass: KClass<*>, val properties: List<PropertySchema>) : PropertyType()
      data class Map(val keyType: String, val valueType: PropertyType) : PropertyType()
      data class List(val elementType: PropertyType) : PropertyType()
      data class Enum(val className: String, val values: kotlin.collections.List<String>) : PropertyType()
   }

   private val schemaCache = mutableMapOf<KClass<*>, kotlin.collections.List<PropertySchema>>()

   /**
    * Get the complete schema for the root configuration type
    */
   fun getSchema(): kotlin.collections.List<PropertySchema> {
      return extractSchemaFromClass(klass, ignoredFields)
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

   /**
    * Get all top-level property names
    */
   fun getTopLevelKeys(): kotlin.collections.List<String> {
      return getSchema().map { it.name }
   }

   /**
    * Get properties of a nested object type
    */
   fun getObjectProperties(objectType: PropertyType.Object): kotlin.collections.List<PropertySchema> {
      return objectType.properties
   }

   /**
    * Get properties of a nested object by class
    */
   fun getObjectProperties(klass: KClass<*>): kotlin.collections.List<PropertySchema> {
      return extractSchemaFromClass(klass, emptySet())
   }

   private fun extractSchemaFromClass(
      targetClass: KClass<*>,
      fieldsToIgnore: Set<String> = emptySet()
   ): kotlin.collections.List<PropertySchema> {
      if (schemaCache.containsKey(targetClass)) {
         return schemaCache[targetClass]!!
      }

      val constructor = targetClass.primaryConstructor ?: return emptyList()
      val parameters = constructor.parameters.associateBy { it.name }

      val properties = targetClass.memberProperties
         .filter { it.name !in fieldsToIgnore }
         .mapNotNull { prop ->
            val param = parameters[prop.name] ?: return@mapNotNull null

            PropertySchema(
               name = prop.name,
               type = extractPropertyType(prop.returnType),
               isNullable = prop.returnType.isMarkedNullable,
               defaultValue = if (param.isOptional) null else null // Default values would need bytecode inspection
            )
         }

      schemaCache[targetClass] = properties
      return properties
   }

   private fun extractPropertyType(type: KType): PropertyType {
      val classifier = type.classifier as? KClass<*> ?: return PropertyType.Primitive("Any")

      // Check for primitives
      if (classifier in PRIMITIVE_TYPES) {
         return PropertyType.Primitive(classifier.simpleName ?: "Unknown")
      }

      // Check for known types that should be treated as primitives
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
            // Complex object - recursively extract schema
            PropertyType.Object(
               className = classifier.simpleName ?: "Unknown",
               klass = classifier,
               properties = extractSchemaFromClass(classifier, emptySet())
            )
         }
      }
   }

   /**
    * Get type description as a human-readable string
    */
   fun getTypeDescription(type: PropertyType): String {
      return when (type) {
         is PropertyType.Primitive -> type.typeName
         is PropertyType.Object -> type.className
         is PropertyType.Map -> "Map<${type.keyType}, ${getTypeDescription(type.valueType)}>"
         is PropertyType.List -> "List<${getTypeDescription(type.elementType)}>"
         is PropertyType.Enum -> type.className
      }
   }
}
