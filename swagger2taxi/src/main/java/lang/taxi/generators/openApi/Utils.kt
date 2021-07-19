package lang.taxi.generators.openApi

import lang.taxi.types.QualifiedName

object Utils {

     private val illegalIdentifierCharacters = "[^a-zA-Z0-9\$_]+".toRegex()
     fun String.normalise(): String = replace(illegalIdentifierCharacters, "_")

     fun qualifyTypeNameIfRaw(typeName: String, defaultNamespace: String): String {
        val qualifiedName = QualifiedName.from(typeName)
        return if (qualifiedName.namespace.isEmpty()) {
            QualifiedName(defaultNamespace, typeName).toString()
        } else {
            typeName
        }
    }

}
