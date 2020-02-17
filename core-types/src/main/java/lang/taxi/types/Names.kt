package lang.taxi.types

data class QualifiedName(val namespace: String, val typeName: String, val parameters: List<QualifiedName> = emptyList()) {
    companion object {
        private val nativeNamespaces = listOf("lang.taxi")
        fun from(value: String): QualifiedName {
            val parts = value.split(".")
            val typeName = parts.last()
            val namespace = parts.dropLast(1).joinToString(".")
            return QualifiedName(namespace, typeName)
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

    override fun toString(): String {
        return if (namespace.isNotEmpty()) {
            "${namespace}.$typeName"
        } else {
            typeName
        }
    }

    fun qualifiedRelativeTo(otherNamespace: String): String {
        if (this.namespace == otherNamespace) {
            return typeName
        }
        if (nativeNamespaces.contains(this.namespace)) {
            return typeName
        }
        return "$namespace.$typeName"
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
}
