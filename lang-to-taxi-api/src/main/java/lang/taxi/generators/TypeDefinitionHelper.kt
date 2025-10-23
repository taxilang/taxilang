package lang.taxi.generators

import lang.taxi.generators.NamingUtils.replaceIllegalCharacters
import lang.taxi.generators.NamingUtils.toCapitalizedWords
import lang.taxi.types.QualifiedName
import lang.taxi.utils.takeTail

/**
 * Encapsulates common rules for creating type names when generating
 * Taxi schemas.
 *
 * As of 2024, this is the preferred approach for generating type names, whereas
 * previously each language mapper would implement their own mapping logic.
 *
 * To use, incrementally append TypeNameHint instances as you iterate the schema.
 * See AvroTypeMapper for a working example.
 */
data class TypeDefinitionHelper private constructor(private val hints: List<TypeDefinitionHint>) {
   companion object {
      fun forHint(hint: TypeNameHint): TypeDefinitionHelper {
         return TypeDefinitionHelper(listOf(hint))
      }
   }

   val isDefiningField = hints.any { it is FieldName }
   val hasExplicitName = hints.any { it is SchemaMetadataTaxiDefinition }

   /**
    * When parsing a type, indicates if we should consider creating a sepearte
    * semantic subtype of this type
    */
   val considerCreatingSemanticSubtype = isDefiningField && !hasExplicitName

   fun declaresNewType(declarationLocation: SchemaTypeDeclaration.DeclarationLocation): Boolean {
      // We take the most recently appended TypeDeclarationHint
      val mostRecentDefinition = this.hints.reversed()
         .filterIsInstance<TypeDeclarationHint>()
         .firstOrNull { it.declareNewType != null }
      return mostRecentDefinition?.declareNewType ?: declarationLocation.defaultDeclareNewTypeBehaviour
   }


   fun append(nextHint: TypeDefinitionHint): TypeDefinitionHelper {
      return this.copy(hints = hints + nextHint)
   }

   fun appendNotNull(nextHint: TypeDefinitionHint?): TypeDefinitionHelper {
      return if (nextHint != null) {
         append(nextHint)
      } else this
   }

   fun suggestName(): QualifiedName {
      val name = this.hints
         .filterIsInstance<TypeNameHint>()
         .fold(null as QualifiedName?) { acc, hint ->
         hint.decorateName(acc)
      } ?: error("Failed to generate type name with the following hints: ${hints.joinToString()}")

      // Replace common names with something more helpful
      if (name.typeName == "Id" && name.namespace.isNotEmpty()) {
         val (tail, remaining) = name.namespace.split(".")
            .takeTail()
         val idName = tail.toCapitalizedWords() + "Id"
         return QualifiedName(name.namespace, idName)
      } else {
         return name
      }

   }
}

data class DefaultTypeDeclaration(
   override val declareNewType: Boolean
) : TypeDeclarationHint


sealed interface TypeDefinitionHint

/**
 * A hint as to whether or not this declaration declares a new type
 * (vs importing an existing type)
 */
interface TypeDeclarationHint : TypeDefinitionHint {
   val declareNewType: Boolean?
}
interface TypeNameHint : TypeDefinitionHint{
   fun decorateName(name: QualifiedName?): QualifiedName?
}

data class NamespacedType(val namespace: String, val typeName: String) : TypeNameHint {
   constructor(qualifiedName: QualifiedName) : this(qualifiedName.namespace, qualifiedName.typeName)
   override fun decorateName(name: QualifiedName?): QualifiedName? {
      if (name == null) return QualifiedName(namespace, typeName)
      TODO("How does NamespacedType decorate?")
   }
}

data class FieldName(val fieldName: String) : TypeNameHint {
   override fun decorateName(name: QualifiedName?): QualifiedName? {
      val thisNamePart = fieldName.replaceIllegalCharacters()
         .toCapitalizedWords()

      val nameParts = listOfNotNull(
         name?.parameterizedName?.toLowerCase(),
         thisNamePart
      )
      return QualifiedName.from(nameParts.joinToString("."))
   }
}

/**
 * This is where a type has been explicitly configured using metadata.
 * It should contain a name, and optionally a create: behaviour, which determines
 * if a new type is being defined
 */
data class SchemaMetadataTaxiDefinition(
   val providedName: QualifiedName,
   /**
    * Allows explicity definition as to whether a new type is being declared.
    * Null indicates should default to the default behaviour (which varies depending on Type vs Model)
    */
   override val declareNewType: Boolean? = null
) : TypeNameHint, TypeDeclarationHint {
   override fun decorateName(name: QualifiedName?): QualifiedName? {
      return providedName
   }

   companion object {
      /**
       * Pass:
       *  - A String? (the name to use)
       *  - A Map? of { name : String, create : Boolean }
       *  - Null
       */
      fun ifPresent(declaration: SchemaTypeDeclaration?): SchemaMetadataTaxiDefinition? {
         return if (declaration != null) {
            SchemaMetadataTaxiDefinition(declaration.qualifiedName, declaration.declareNewType)
         } else null
      }

      fun ifPresent(name: String?, declareNewType: Boolean? = null): SchemaMetadataTaxiDefinition? {
         return if (name != null) {
            SchemaMetadataTaxiDefinition(QualifiedName.from(name), declareNewType = declareNewType)
         } else null
      }
   }
}

/**
 * A type declaration inside an OAS / Avro etc, schema.
 *
 */
data class SchemaTypeDeclaration(val name: String, val declareNewType: Boolean)  {
   enum class DeclarationLocation(val defaultDeclareNewTypeBehaviour: Boolean) {
      Field(false),
      Model(true)
   }
   val qualifiedName = QualifiedName.from(name)

   companion object {
      fun forName(name: String?, declarationLocation: DeclarationLocation): SchemaTypeDeclaration? {
         if (name == null) return null;
         return SchemaTypeDeclaration(name, declarationLocation.defaultDeclareNewTypeBehaviour)
      }
      fun fromMap(map: Map<*, *>?, declarationLocation: DeclarationLocation): SchemaTypeDeclaration? {
         if (map == null) return null;
         val name = map.get("name") as String? ?: return null
         val declareNewType = map.get("create") as Boolean? ?: declarationLocation.defaultDeclareNewTypeBehaviour
         return  SchemaTypeDeclaration(name, declareNewType)
      }
   }
}
