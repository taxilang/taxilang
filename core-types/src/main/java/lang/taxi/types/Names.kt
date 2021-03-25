package lang.taxi.types

import lang.taxi.utils.takeHead

data class QualifiedName(val namespace: String, val typeName: String, val parameters: List<QualifiedName> = emptyList()) {
   companion object {
      private val nativeNamespaces = listOf("lang.taxi")
      fun from(value: String): QualifiedName {
         return QualifiedNameParser.parse(value)
      }
   }

   val parameterizedName: String
      get() {
         return if (parameters.isEmpty()) {
            toString()
         } else {
            val params = this.parameters.joinToString(",") { it.parameterizedName }
            "${toString()}<$params>"
         }
      }

   val fullyQualifiedName = this.toString()
   override fun toString(): String {
      return if (namespace.isNotEmpty()) {
         "${namespace}.$typeName"
      } else {
         typeName
      }
   }

   fun qualifiedRelativeTo(otherNamespace: String): String {
      return when {
         this.namespace == otherNamespace -> typeName
         nativeNamespaces.contains(this.namespace) -> typeName
         this.namespace.isEmpty() -> typeName
         else -> "$namespace.$typeName"
      }
   }

   val firstTypeParameterOrSelf: String
      get() {
         return if (parameters.isEmpty()) {
            toString()
         } else {
            parameters.first().toString()
         }
      }
}


interface Named {
   val qualifiedName: String

   fun toQualifiedName(): QualifiedName {
      return QualifiedName.from(qualifiedName)
   }
}

/**
 * A series of named attributes on an entity that describe a path
 * eg foo.baz.bar
 */
data class AttributePath(val parts: List<String>) {
   // Moved:  Use QualifiedNameContext.toAttriubtePath()
//    constructor(qualifiedName: TaxiParser.QualifiedNameContext) : this(qualifiedName.Identifier().map { it.text })

   companion object {
      fun from(value: String): AttributePath {
         return AttributePath(value.split("."))
      }
   }

   val path = parts.joinToString(".")

   override fun toString() = "AttributePath ($path)"
   fun canResolve(parameters: List<NameTypePair>): Boolean {
      if (parameters.isEmpty()) {
         return false
      }
      val (part, remainingParts) = parts.takeHead()
      val thisPart = parameters.firstOrNull { it.name == part } ?: return false
      return if (remainingParts.isEmpty()) {
         true
      } else {
         when (thisPart.type) {
            is ObjectType -> AttributePath(remainingParts).canResolve((thisPart.type as ObjectType).allFields)
            else -> false
         }
      }

   }

}


