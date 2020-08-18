package lang.taxi.types

/**
 * Interface for language elements that support
 * generating themselves back as taxi.
 *
 * Used for tooling where we need to support codegen
 */
interface TaxiStatementGenerator {
   fun asTaxi():String
}
