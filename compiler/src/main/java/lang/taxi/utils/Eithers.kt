package lang.taxi.utils

import arrow.core.Either

fun <A, B> List<Either<A, B>>.invertEitherList(): Either<List<A>, List<B>> {
   val mapLeft = this.any { it.isLeft() }
   val values = this.mapNotNull { either ->
      when (either) {
         is Either.Left -> if (mapLeft) either.a else null
         is Either.Right -> if (!mapLeft) either.b else null
      }
   }
   // Almost certainly a smarter way to do this.
   return if (mapLeft) {
      Either.left(values as List<A>)
   } else {
      Either.right(values as List<B>)
   }
}

fun <L,R> R?.toEither(valueIfNull:L):Either<L,R> {
   return if (this == null) {
      Either.left(valueIfNull)
   } else {
      Either.right(this)
   }
}
