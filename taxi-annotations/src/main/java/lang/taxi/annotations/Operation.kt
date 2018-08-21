package lang.taxi.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Service(val value: String = "")

fun Service.hasNamespace(): Boolean = Namespaces.hasNamespace(this.value)
fun Service.namespace(): String? = Namespaces.pluckNamespace(this.value)
fun Service.qualifiedName(defaultNamespace: String): String = Namespaces.qualifiedName(this.value, defaultNamespace)
fun Service.declaresName(): Boolean {
    return this.value.isNotEmpty()
}


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Operation(
        /** The name of the operation */
        val value: String = "")

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ResponseContract(
        val basedOn: String,
        vararg val constraints: ResponseConstraint
)

@Target()
@Retention(AnnotationRetention.RUNTIME)
annotation class ResponseConstraint(
        val value: String
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Parameter(
        val name: String = "",
        vararg val constraints: Constraint
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Constraint(
        val value: String
)

/**
 * Provides a polymorphic way of reading thw two constraint annotations.
 */
data class ConstraintAnnotationModel(val value: String, val annotation: Annotation) {
    constructor(constraint: Constraint) : this(constraint.value, constraint)
    constructor(constraint: ResponseConstraint) : this(constraint.value, constraint)
}
