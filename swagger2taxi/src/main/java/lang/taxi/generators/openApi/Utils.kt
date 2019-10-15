package lang.taxi.generators.openApi

import lang.taxi.types.QualifiedName

object Utils {
    fun qualifyTypeNameIfRaw(typeName: String, defaultNamespace: String): String {
        val qualifiedName = QualifiedName.from(typeName)
        return if (qualifiedName.namespace.isEmpty()) {
            QualifiedName(defaultNamespace, typeName).toString()
        } else {
            typeName
        }
    }

}