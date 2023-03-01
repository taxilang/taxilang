package lang.taxi

import nl.pvdberg.hashkode.HashKode
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


class Equality<T : Any>(val target: T, vararg val properties: T.() -> Any?) {

    fun isEqualTo(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (other.javaClass != target.javaClass) return false
        return properties.all { it.invoke(target) == it.invoke(other as T) }
    }

   private val atomicHash = AtomicReference<Int>()

    fun hash(): Int {
        val fields = properties
                .map {
                    val valueToHash = it.invoke(target)
                    valueToHash?.hashCode() ?: 0
                }
        return fields.fold(HashKode.DEFAULT_INITIAL_ODD_NUMBER) { a, b -> HashKode.DEFAULT_MULTIPLIER_PRIME * a + b }
    }
}
