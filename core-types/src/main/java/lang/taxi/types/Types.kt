package lang.taxi.types

import arrow.core.Either
import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class LazyLoadingWrapper(private val type: Type) {
   companion object {
      val log: Logger = LoggerFactory.getLogger(LazyLoadingWrapper::class.java)
   }

   private val types by lazy { type.allInheritedTypes + type }

   val allInheritedTypes: Set<Type>
      by lazy { type.getInheritanceGraph() }

   val inheritsFromPrimitive: Boolean
      by lazy { basePrimitive != null }

   val baseEnum: EnumType? by lazy {
      if (type !is EnumType) {
         null
      }

      val baseEnums = types.filterIsInstance<EnumType>().filter { it.inheritsFrom.isEmpty() }

      when (baseEnums.size) {
         0 -> error("Couldn't find base type for ${type.qualifiedName}")
         1 -> baseEnums.first()
         else -> error("Inheriting from multiple enums isn't supported, and technically shouldn't be possible: ${type.qualifiedName}\"")
      }

   }


   val basePrimitive: PrimitiveType?
      by lazy {
         val primitives = types.filter { it is PrimitiveType || it is EnumType }
            .filter { it.inheritsFrom.isEmpty() }
         when {
            primitives.isEmpty() -> null
            primitives.size == 1 -> {
               val baseType = primitives.first()
               if (baseType is PrimitiveType) {
                  baseType
               } else {
                  // We can revisit this later if neccessary.
                  require(baseType is EnumType) { "Expected baseType to be EnumType" }
                  PrimitiveType.STRING
               }
            }
            else -> error("Type ${type.qualifiedName} inherits from multiple primitives: ${primitives.joinToString { type.qualifiedName }}")
         }
      }

   val definitionHash: String by lazy {
      val hasher = Hashing.sha256().newHasher()
      //println("============= ${this.type.qualifiedName} hash debug ==============")// comments kept on purpose, useful when debugging issues
      computeTypeHash(hasher, type)
      hasher.hash().toString().substring(0, 6)
   }

   private fun computeTypeHash(hasher: Hasher, type: Type) {
      detectHashCollision(type.compilationUnits)

      // include sources
      type.compilationUnits
         .sortedBy { it.source.content.hashCode() }
         .forEach {
            hasher.putUnencodedChars(it.source.content)
            //println("CompilationUnit:(hc:${it.source.content.hashCode()}) ${it.source.normalizedSourceName} #${it.source.hashCode()} -> ${type.qualifiedName}: ${it.source.content}")
         }

      when (type) {
         is UserType<*, *> -> {
            // changing referenced type changes hash
            type.referencedTypes
               .sortedBy { it.qualifiedName }
               .forEach {
                  //println("Reference: ${type.qualifiedName} ---> ${it.qualifiedName}")
                  computeTypeHash(hasher, it)
               }

            detectHashCollision(type.extensions.map { it.compilationUnit })

            // changing extensions changes hash
            type.extensions
               .sortedBy { it.compilationUnit.source.content.hashCode() }
               .forEach {
                  hasher.putUnencodedChars(it.compilationUnit.source.content)
                  //println("Extension:(hc:${it.compilationUnit.source.content.hashCode()}) ${it.compilationUnit.source.normalizedSourceName} #${it.compilationUnit.source.hashCode()}: ${it.compilationUnit.source.content}")
               }
         }
      }
   }

   private fun detectHashCollision(compilationUnits: List<CompilationUnit>) {
      val sourcesWithHashCollision = compilationUnits
         .groupBy { it.source.hashCode() }
         .filter { it.value.size > 1 }
         .flatMap { it.value }
         .map { it.source.normalizedSourceName }

      if (sourcesWithHashCollision.isNotEmpty()) {
         log.warn(("There's a hash clash in the underlying sources $sourcesWithHashCollision " +
            "This will generate indeterminate behaviour that can re-trigger recompilation of sources, lost nights, and torn hair. " +
            "You shouldn't ignore this!"))
      }
   }

}

interface ImportableToken : Named, Compiled

interface Type : Named, Compiled, ImportableToken {
   val inheritsFrom: Set<Type>

   val allInheritedTypes: Set<Type>

   val format: List<String>?

   val inheritsFromPrimitive: Boolean

   val basePrimitive: PrimitiveType?

   val formattedInstanceOfType: Type?

   val definitionHash: String?

   val calculation: Formula?

   fun getInheritanceGraph(typesToExclude: Set<Type> = emptySet()): Set<Type> {
      val allExcludedTypes: Set<Type> = typesToExclude + setOf(this)
      val aliasType = if (this is TypeAlias) {
         this.aliasType!!
      } else {
         null
      }
      return (this.inheritsFrom + aliasType)
         .filterNotNull()
         .flatMap { type ->
            // Include aliases if this is a TypeAlias
            if (type is TypeAlias) {
               listOf(type, type.aliasType!!)
            } else {
               listOf(type)
            }
         }
         .flatMap { inheritedType ->
            if (!typesToExclude.contains(inheritedType))
               setOf(inheritedType) + inheritedType.getInheritanceGraph(allExcludedTypes)
            else emptySet<Type>()
         }.toSet()
   }
}

interface TokenDefinition {
   val compilationUnit: CompilationUnit
}
interface TypeDefinition : TokenDefinition {

}

interface Documented {
   val typeDoc: String?

   companion object {
      fun typeDoc(sources: List<Documented?>): String? {
         return sources.filterNotNull().typeDoc()
      }
   }

}


/**
 * A type that can be declared by users explicity.
 * eg:  Object type, Enum type.
 * ArrayType is excluded (as arrays are primitive, and the inner
 * type will be a UserType)
 */
interface UserType<TDef : TypeDefinition, TExt : TypeDefinition> : Type, DefinableToken<TDef> {

   val extensions: List<TExt>

   fun addExtension(extension: TExt): Either<ErrorMessage, TExt>



   override val compilationUnits: List<CompilationUnit>
      get() = (this.extensions.map { it.compilationUnit } + this.definition?.compilationUnit).filterNotNull()

   /**
    * A list of all the other types this UserType makes reference to.
    * Used when importing this type, to ensure the full catalogue of types is imported
    */
   val referencedTypes: List<Type>
}

interface DefinableToken<TDef : TokenDefinition> : ImportableToken {
   var definition: TDef?

   val isDefined: Boolean
      get() {
         return this.definition != null
      }
}


fun List<Documented>.typeDoc(): String? {
   return this.mapNotNull { it.typeDoc }.joinToString("\n")
}
