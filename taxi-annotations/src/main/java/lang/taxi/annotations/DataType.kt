package lang.taxi.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.PROPERTY, AnnotationTarget.FIELD,
        // When on a Function, indicates the return type.
        // Useful for methods that return String etc.
        AnnotationTarget.FUNCTION,
        // TypeAliases don't work yet, because of missing Kotlin features,
        // but allow the annotation
        AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DataType(
        /**
         * The qualified name of the attribute within a taxonomy.
         * If blank, will be inferred from the class name
         */
        val value: String = ""

)

class Foo

/**
 * Indicates that a class is a Parameter type, meaning that
 * it's valid to be constructed during query time for passing
 * to another service.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ParameterType

fun DataType.hasNamespace(): Boolean = Namespaces.hasNamespace(this.value)
fun DataType.namespace(): String? = Namespaces.pluckNamespace(this.value)

fun DataType.declaresName(): Boolean {
    return this.value.isNotEmpty()
}

fun DataType.qualifiedName(defaultNamespace: String): String = Namespaces.qualifiedName(this.value, defaultNamespace)

/**
 * Specifies that an input must be provided in a specific format.
 * Eg: On a currency amount field, may specify the currency
 * On a date field, may specify the format
 * On a weight or height field, may specify the unit of measurement.
 *
 * Should be a pointer to a fully qualified Enum value within the
 * global taxonomy
 *
 * // TODO : Consider how DataFormats relate to contracts discussed here:
 * https://gitlab.com/osmosis-platform/taxi-lang/issues/1
 *
 * Contracts may be a more powerful expression, as it's more contextual
 */
annotation class DataFormat(val value: String)

