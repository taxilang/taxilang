package lang.taxi.utils

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A property delegate that memoizes a computed value, recomputing it only when
 * the cache key changes.
 *
 * This is useful for properties on mutable objects where the computation is
 * expensive, but the result is stable as long as the underlying state hasn't changed.
 *
 * ## How it works
 * On each property access, [keySelector] is invoked and its result is hashed.
 * If the hash matches the previously stored hash, the cached value is returned.
 * If the hash differs (or no value has been computed yet), [compute] is called,
 * and both the result and the new hash are stored.
 *
 * ## Why hashCode and not equality?
 * The key is often a complex object (e.g. [ObjectTypeDefinition]) where computing
 * full equality would itself be expensive. A hash change is sufficient signal to
 * recompute. Hash collisions could theoretically cause a stale cache to be returned,
 * but this is negligible for this use case.
 *
 * ## Null keys
 * If [keySelector] returns null (e.g. because the definition is not yet set),
 * the value is always recomputed. This is the safe behaviour for incomplete objects.
 *
 * ## Thread safety
 * This implementation is NOT thread-safe. If the owning object can be accessed
 * concurrently (e.g. during query plan construction), consider adding @Synchronized
 * or a volatile cachedKey field.
 *
 * ## Usage
 * ```kotlin
 * // Declare a memoized property using the definition as the cache key.
 * // The value is recomputed only when `definition` changes.
 * private val _referencedTypes = memoizedProperty({ definition }) {
 *     collectReferencedTypes(mutableSetOf()).toList()
 * }
 *
 * // Expose it via the standard `by` delegate syntax.
 * override val referencedTypes: List<Type> by _referencedTypes
 * ```
 *
 * @param T the type of the computed value
 * @param K the type of the cache key
 * @param keySelector returns the current cache key — typically the object's definition
 * @param compute produces the value to cache when the key has changed
 */
class MemoizedProperty<T, K>(
   private val keySelector: () -> K?,
   private val compute: () -> T
) : ReadOnlyProperty<Any?, T> {

   companion object {
      private const val UNINITIALISED = Int.MIN_VALUE
      private const val NULL_KEY = Int.MIN_VALUE + 1
   }

   // The hash of the key at the time the value was last computed.
   // Do not allow a nullable value here, as that then forces boxing, which leads to
   // masses of object allocation
   private var cachedKey: Int = UNINITIALISED

   // The last computed value. Typed as T? internally to allow lazy initialisation,
   // but the public API always returns T — the cast in getValue is safe because
   // cachedValue is only ever null before the first computation.
   private var cachedValue: T? = null

   override fun getValue(thisRef: Any?, property: KProperty<*>): T {
      val currentKey: Int = keySelector()?.hashCode() ?: NULL_KEY

      // Recompute if the key has changed, or if this is the first access.
      // Note: cachedValue == null is a fallback guard for the first-access case
      // where cachedKey happens to collide with a null key hash.
      if (cachedKey != currentKey || cachedValue == null) {
         cachedValue = compute()
         cachedKey = currentKey
      }

      // Safe: cachedValue is always set by the block above before we reach this line.
      @Suppress("UNCHECKED_CAST")
      return cachedValue as T
   }
}

/**
 * Creates a [MemoizedProperty] delegate.
 *
 * Shorthand for use with Kotlin's `by` property delegation syntax.
 *
 * @param keySelector returns the current cache key — recomputation is triggered when its hashCode changes
 * @param compute produces the value to cache
 */
fun <T, K> memoizedProperty(keySelector: () -> K?, compute: () -> T): MemoizedProperty<T, K> {
   return MemoizedProperty(keySelector, compute)
}
