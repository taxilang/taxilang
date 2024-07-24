package lang.taxi.types

import arrow.core.Either

/**
 * A schema member who has child members
 * (eg., Enum -> EnumValue, Service -> Operations, etc)
 */
interface  HasMembers<T : Named> {
   fun getMember(name: String): Either<String, out T>
}
