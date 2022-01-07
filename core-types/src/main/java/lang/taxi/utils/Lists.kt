package lang.taxi.utils

/**
 * Returns the first item in the list,
 * along with a list having the first item removed
 */
fun <T> List<T>.takeHead(): Pair<T, List<T>> {
   val first = this.first()
   val rest = this.subList(1,this.size)
   return first to rest
}

fun <T> List<T>.takeTail(): Pair<T, List<T>> {
   val last = this.last()
   val rest = this.dropLast(1)
   return last to rest
}
