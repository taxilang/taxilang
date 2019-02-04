package lang.taxi


class Equality<T : Any>(val target: T, vararg val properties: T.() -> Any?) {
    companion object {
        const val DEFAULT_INITIAL_ODD_NUMBER = 17
        const val DEFAULT_MULTIPLIER_PRIME = 37
    }


    fun isEqualTo(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (other.javaClass != target.javaClass) return false
        return properties.all { it.invoke(target) == it.invoke(other as T) }
    }

    fun hash(): Int {
        val fields = properties
                .map {
                    val valueToHash = it.invoke(target)
                    valueToHash?.hashCode() ?: 0
                }
        return fields.fold(DEFAULT_INITIAL_ODD_NUMBER) { a, b -> DEFAULT_MULTIPLIER_PRIME * a + b }
    }
}