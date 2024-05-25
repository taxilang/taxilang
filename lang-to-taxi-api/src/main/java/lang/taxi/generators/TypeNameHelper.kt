package lang.taxi.generators

import lang.taxi.generators.NamingUtils.replaceIllegalCharacters
import lang.taxi.generators.NamingUtils.toCapitalizedWords
import lang.taxi.types.QualifiedName

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
data class TypeNameHelper private constructor(private val hints: List<TypeNameHint>) {
   companion object {
      fun forHint(hint: TypeNameHint): TypeNameHelper {
         return TypeNameHelper(listOf(hint))
      }
   }

   fun append(nextHint: TypeNameHint): TypeNameHelper {
      return this.copy(hints = hints + nextHint)
   }

   fun appendNotNull(nextHint: TypeNameHint?): TypeNameHelper {
      return if (nextHint != null) {
         append(nextHint)
      } else this
   }

   fun suggestName(): QualifiedName {
      val name = this.hints.fold(null as QualifiedName?) { acc, hint ->
         hint.decorateName(acc)
      } ?: error("Failed to generate type name with the following hints: ${hints.joinToString()}")
      return name
   }


}

interface TypeNameHint {
   fun decorateName(name: QualifiedName?): QualifiedName?
}

data class NamespacedType(val namespace: String, val typeName: String) : TypeNameHint {
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
 * The name has been explicitly provided using metadata.
 * Note - this is for where the Taxi type name is embedded using explicit Taxi metadata within the schema.
 */
data class NameFromTaxiMetadata(val providedName: QualifiedName) : TypeNameHint {
   override fun decorateName(name: QualifiedName?): QualifiedName? {
      return providedName
   }

   companion object {
      fun ifPresent(name: String?): NameFromTaxiMetadata? {
         return if (name != null) {
            NameFromTaxiMetadata(QualifiedName.from(name))
         } else null
      }
   }
}
