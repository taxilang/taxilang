package lang.taxi.utils

fun <T> List<T>.takeHead(): Pair<T, List<T>> {
   val first = this.first()
   val rest = this.subList(1,this.size)
   return first to rest
}
