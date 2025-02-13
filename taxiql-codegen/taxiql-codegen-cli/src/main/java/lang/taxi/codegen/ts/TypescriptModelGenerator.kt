package lang.taxi.codegen.ts

import lang.taxi.codegen.GeneratedCode
import lang.taxi.types.ArrayType
import lang.taxi.types.EnumType
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.utils.quoted
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

// We need to detect name collisions (eg., foo.Name and bar.Name), so require
// the set of types to generate up-front.
class TypescriptModelGenerator(
   private val typesToGenerate: Set<Type>,
   private val fileName: String = "types.ts",
   private val nameSubstitutions: Map<QualifiedName, QualifiedName> = emptyMap()
) {
   constructor(
      typesToGenerate: Collection<Type>,
      fileName: String = "types.ts",
      nameSubstitutions: Map<QualifiedName, QualifiedName> = emptyMap()
   ) : this(typesToGenerate.toSet(), fileName, nameSubstitutions)

   constructor(
      type: Type,
      fileName: String = "types.ts",
      nameSubstitutions: Map<QualifiedName, QualifiedName> = emptyMap()
   ) : this(
      listOf(type), fileName, nameSubstitutions
   )

   private val generatedTaxi = ConcurrentHashMap<QualifiedName, GeneratedTypescriptType>()

   private val allTypes = this.typesToGenerate
      .flatMap {
         fun getReferencedTypes(type: Type): List<Type> {
            return when (type) {
               is ObjectType -> type.referencedTypes + type
               is ArrayType -> listOf(type.memberType) + getReferencedTypes(type.memberType)
               is StreamType -> listOf(type.type) + getReferencedTypes(type.type)
               else -> listOf(it)
            }
         }
         getReferencedTypes(it)

      }

   private val uniqueNames: Map<QualifiedName, String> =
      getUniqueNames(
         allTypes
            .map { it.toQualifiedName() }.toSet(),
         nameSubstitutions.toMap()
      )

   fun getTypeName(type: Type): String {
      return getType(type).name
   }

   private fun getType(type: Type, generateAnonymousTypesInline: Boolean = false): GeneratedTypescriptType {
      return when (type) {
         is ArrayType -> generateArray(type, generateAnonymousTypesInline)
         is StreamType -> generateStream(type, generateAnonymousTypesInline)
         is ObjectType -> generateObjectType(type, generateAnonymousTypesInline)
         is EnumType -> generateEnumType(type)
         is PrimitiveType -> generateScalarType(type)
         else -> error("Unsupported type ${type::class.simpleName}")
      }
   }

   private fun generateEnumType(type: EnumType): GeneratedTypescriptType {
      return buildIfAbsent(type) { tsName ->
         val enumValues = type.values.joinToString(" | ", postfix = ";") { it.name.quoted("'") }
         GeneratedTypescriptType("export type $tsName = $enumValues", tsName)
      }
   }

   private fun generateObjectType(
      type: ObjectType,
      generateAnonymousTypesInline: Boolean = false
   ): GeneratedTypescriptType {
      return if (type.isScalar) {
         generateScalarType(type)
      } else {
         buildIfAbsent(type) { tsName ->
            val properties = type.fields.map {
               it.name to getType(it.type, generateAnonymousTypesInline = true)
            }

            val codeBody = """{
${properties.joinToString("\n") { (fieldName, generatedType) -> "  $fieldName: ${generatedType.nameOrInlineDefinition}" }}
}""".trimMargin()

            val code = if (type.anonymous && generateAnonymousTypesInline) {
               codeBody
            } else {
               """export interface $tsName $codeBody"""
            }
            GeneratedTypescriptType(
               code,
               tsName,
               type.anonymous,
               generatedInline = type.anonymous && generateAnonymousTypesInline
            )
         }
      }
   }

   private fun generateScalarType(type: Type): GeneratedTypescriptType {
      return buildIfAbsent(type) { tsName ->
         require(type.basePrimitive != null) { "Type ${type.toQualifiedName().parameterizedName} is scalar but does not have a primitive type" }
         val typescriptPrimitive = TypescriptPrimitive.from(type.basePrimitive!!)
         val code = """export type $tsName = ${typescriptPrimitive.symbol};"""
         GeneratedTypescriptType(code, tsName)
      }
   }

   /**
    * Builds and updates the map of generated types, only if this type hasn't yet been built
    */
   private fun <T : Type> buildIfAbsent(
      type: T,
      builder: (String) -> GeneratedTypescriptType
   ): GeneratedTypescriptType {
      val fromMap = generatedTaxi.computeIfAbsent(type.toQualifiedName()) {
         GeneratedTypescriptType.underConstruction(tsName(type))
      }
      return if (fromMap.underConstruction) {
         val constructed = fromMap.construct { uniqueName -> builder(uniqueName) }
         generatedTaxi[type.toQualifiedName()] = constructed
         constructed
      } else {
         fromMap
      }
   }

   private fun tsName(type: Type): String {
      return uniqueNames[type.toQualifiedName()]
         ?: error("A typescript type name has not been generated for ${type.qualifiedName}")
   }

   private fun generateArray(type: ArrayType, generateAnonymousTypesInline: Boolean): GeneratedTypescriptType {
      val innerType = getType(type.memberType, generateAnonymousTypesInline)
      return GeneratedTypescriptType("", innerType.nameOrInlineDefinition + "[]")
   }

   private fun generateStream(type: StreamType, generateAnonymousTypesInline: Boolean): GeneratedTypescriptType {
      val innerType = getType(type.type, generateAnonymousTypesInline)
      return GeneratedTypescriptType("", innerType.nameOrInlineDefinition)
   }

   /**
    * Generates code for the provided types.
    * Returns the generated code, along with a map of the QualifiedName of taxi types, and their
    * corresponding typescript names.
    */
   fun build(): Pair<Map<QualifiedName, String>, GeneratedCode> {
      typesToGenerate.forEach { getType(it) }
      // First, detect any name conflicts

      val typescript = generatedTaxi
         .filter { !it.value.generatedInline }
         .toList()
         .sortedBy { it.second.name }
         .map { it.second.code }
         .joinToString("\n")

      return uniqueNames to GeneratedCode(
         Paths.get(fileName),
         typescript
      )
   }


   companion object {
      /**
       * Creates a set of unique names for all the provided type names, dealing with conflicts
       * If names don't conflict on their type name alone, then the type name is used.
       * Otherwise, the namespace is progressively added until the name becomes unique.
       */
      fun getUniqueNames(names: Set<QualifiedName>, nameSubstitutions: Map<QualifiedName, QualifiedName> = emptyMap()): Map<QualifiedName, String> {
         fun addNamespaces(name: QualifiedName, namespacesToAdd: Int): String {
            val namespaceParts = name.namespace.split(".")
            val namespaceToAdd = namespaceParts.takeLast(namespacesToAdd).joinToString("_")
            return listOf(namespaceToAdd, name.typeName)
               .filter { it.isNotEmpty() }
               .joinToString("_")
         }

         fun isUnique(name: QualifiedName, namespacesToAdd: Int): Boolean {
            val sameSimpleName = names.filter { it.typeName == name.typeName }
            if (sameSimpleName.size == 1) return true

            val uniqueNamesWithNamespaces = sameSimpleName.map { addNamespaces(it, namespacesToAdd) }
               .distinct()
            return uniqueNamesWithNamespaces.size == sameSimpleName.size
         }


         val uniqueNames = names.map { typeName ->
            val nameToConvert = nameSubstitutions.getOrDefault(typeName, typeName)
            var namespacesToAdd = 0
            while (!isUnique(nameToConvert, namespacesToAdd)) {
               namespacesToAdd++
            }
            typeName to addNamespaces(nameToConvert, namespacesToAdd)
         }.toMap()
         return uniqueNames
      }
   }
}

private data class GeneratedTypescriptType(
   val code: String,
   val name: String,
   val anonymous: Boolean = false,
   val generatedInline: Boolean = false
) {
   val nameOrInlineDefinition = if (generatedInline) code else name

   companion object {
      const val UNDER_CONSTRUCTION = "// under construction"
      fun underConstruction(name: String) = GeneratedTypescriptType(UNDER_CONSTRUCTION, name)
   }

   private var building: Boolean = false
   val underConstruction = code == UNDER_CONSTRUCTION
   fun construct(builder: (String) -> GeneratedTypescriptType): GeneratedTypescriptType {
      return if (underConstruction && !building) {
         building = true
         val built = builder.invoke(name)
         building = false
         built
      } else this
   }
}

enum class TypescriptPrimitive(val symbol: kotlin.String) {
   String("string"),
   Number("number"),
   Boolean("boolean"),
   Never("never"),
   Void("void"),
   Any("any");

   companion object {
      private val taxiToTypeScriptPrimitiveMap = mapOf(
         PrimitiveType.NOTHING to String,
         PrimitiveType.ANY to Any,
         PrimitiveType.BOOLEAN to Boolean,
         PrimitiveType.STRING to String,
         PrimitiveType.INTEGER to Number,
         PrimitiveType.LONG to Number, // TypeScript doesn't distinguish long vs int
         PrimitiveType.DECIMAL to Number,
         PrimitiveType.DOUBLE to Number,
         PrimitiveType.LOCAL_DATE to String, // Could also map to Date
         PrimitiveType.TIME to String,
         PrimitiveType.DATE_TIME to String, // Could also map to Date
         PrimitiveType.INSTANT to String, // Could also map to Date
         PrimitiveType.VOID to Void
      )

      fun from(primitive: PrimitiveType): TypescriptPrimitive {
         return taxiToTypeScriptPrimitiveMap[primitive]
            ?: error("No primitive type mapping exists for Taxi primitive ${primitive}")
      }
   }


}
