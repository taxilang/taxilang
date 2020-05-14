package lang.taxi.utils

fun <T> List<T>.pop(): Pair<T, List<T>> {
   val first = this.first()
   val rest = this.subList(1,this.size)
   return first to rest
}
