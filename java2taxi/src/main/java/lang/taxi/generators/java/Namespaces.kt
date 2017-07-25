package lang.taxi.generators.java

import lang.taxi.*

object Namespaces {
    fun deriveNamespace(javaClass: Class<*>): String {
        val dataType = javaClass.getAnnotation(DataType::class.java)
        val service = javaClass.getAnnotation(Service::class.java)
        val namespaceAnnotation = javaClass.getAnnotation(Namespace::class.java)
        return when {
            namespaceAnnotation != null -> namespaceAnnotation.value
            dataType != null && dataType.hasNamespace() -> dataType.namespace()!!
            service != null && service.hasNamespace() -> dataType.namespace()!!
            else -> javaClass.`package`.name
        }
    }
}
