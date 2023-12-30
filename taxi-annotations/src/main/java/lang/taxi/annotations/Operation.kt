package lang.taxi.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Service(val value: String = "",
                         /**
                          * Markdown docs that describe this service
                          */
                         val documentation: String = "")

fun Service.hasNamespace(): Boolean = Namespaces.hasNamespace(this.value)
fun Service.namespace(): String? = Namespaces.pluckNamespace(this.value)
fun Service.qualifiedName(defaultNamespace: String): String = Namespaces.qualifiedName(this.value, defaultNamespace)
fun Service.declaresName(): Boolean {
   return this.value.isNotEmpty()
}

// This is an intentional duplicateion of lang.taxi.services.OperationScope.
// So as not to add the dependency into this package
enum class OperationScope {
   READ_ONLY,
   MUTATION
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Operation(
   /** The name of the operation */
   val value: String = "",

   /**
    * The scope of operation - similar to the  verb in HTTP.
    * eg:
    * read, write, etc.
    *
    * When policies are defined against a specific operation type, it must match against the
    * value defined here.
    */
   val scope: OperationScope = OperationScope.READ_ONLY,

   /**
    * Markdown docs that describe this operation
    */
   val documentation: String = "",

   /**
    * Exclude this operation from schema generation
    */
   val excluded: Boolean = false
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ResponseContract(
   val basedOn: String = "",
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
   vararg val constraints: Constraint,
   val documentation:String = ""
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
