package lang.taxi.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS,AnnotationTarget.TYPE,AnnotationTarget.TYPEALIAS)
annotation class Namespace(val value: String)

object Namespaces {
    fun hasNamespace(value: String): Boolean {
        return value.contains(".")
    }

    fun pluckNamespace(source: String): String? {
        if (!this.hasNamespace(source)) return null
        return source.split(".")
                .dropLast(1)
                .joinToString(".")
    }

    fun qualifiedName(name: String, defaultNamespace: String): String {
        return if (hasNamespace(name)) name else "$defaultNamespace.$name".removePrefix(".")
    }
}

