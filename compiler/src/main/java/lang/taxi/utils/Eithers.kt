package lang.taxi.utils

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right


fun <A,B> Either<A,B>.getOrThrow(message:String = "Unhandled failure of Either"):B {
   return this.getOrHandle { error(message) }
}
fun <A, B> List<Either<A, B>>.invertEitherList(): Either<List<A>, List<B>> {
   val mapLeft = this.any { it.isLeft() }
   val values = this.mapNotNull { either ->
      when (either) {
         is Either.Left -> if (mapLeft) either.value else null
         is Either.Right -> if (!mapLeft) either.value else null
      }
   }
   // Almost certainly a smarter way to do this.
   return if (mapLeft) {
      (values as List<A>).left()
   } else {
      (values as List<B>).right()
   }
}

fun <L, R> R?.toEither(valueIfNull: () -> L): Either<L, R> {
   return if (this == null) {
      (valueIfNull()).left()
   } else {
      this.right()
   }
}

fun <L, R> R?.toEither(valueIfNull: L): Either<L, R> {
   return if (this == null) {
      (valueIfNull).left()
   } else {
      this.right()
   }
}

fun <A, B> Either<A, B>.leftOr(default: A): A {
   return when (this) {
      is Either.Left -> this.value
      is Either.Right -> default
   }
}

// Equivalent methods, I always forget which to call.
fun <A, B> Either<A, B>.leftOrNull(): A? = errorOrNull()
fun <A, B> Either<A, B>.errorOrNull(): A? {
   return when (this) {
      is Either.Left -> this.value
      is Either.Right -> null
   }
}

fun <A, B> Either<List<List<A>>, B>.flattenErrors(): Either<List<A>, B> {
   return this.mapLeft { it.flatten() }
}

fun <A, B> Either<A, B>.wrapErrorsInList(): Either<List<A>, B> {
   return this.mapLeft { listOf(it) }
}

fun <E,A,E2> Either<E,A>.flatMapLeft(transform: (E) -> Either<E2,A>):Either<E2,A> {
   return when (this) {
      is Either.Left -> transform(this.value)
      is Either.Right -> this
   }
}
