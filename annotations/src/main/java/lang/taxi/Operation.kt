package lang.taxi

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Service(val value: String = "")

fun Service.hasNamespace(): Boolean = Namespaces.hasNamespace(this.value)
fun Service.namespace(): String? = Namespaces.pluckNamespace(this.value)
fun Service.qualifiedName(defaultNamespace: String): String = Namespaces.qualifiedName(this.value, defaultNamespace)
fun Service.declaresName(): Boolean {
    return this.value.isNotEmpty()
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Operation(
        /** The name of the operation */
        val value: String = "")
