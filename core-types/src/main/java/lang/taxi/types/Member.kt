package lang.taxi.types

import arrow.core.Either

/**
 * A schema member who has child members
 * (eg., Enum -> EnumValue, Service -> Operations, etc)
 */
interface  HasMembers<T : Named> {
   fun getMember(name: String,
                 /**
                  * Indciates if default / fallback values can be used.
                  * Eg., in an enum with a default value, can that be returned
                  * as a valid value from this lookup exercise?
                  */
                 permitImplicitResolution: Boolean = true): Either<String, out T>
}
