package lang.taxi

import lang.taxi.kapt.TypeAlias
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

interface TypeAliasRegistrar {
    fun register()
}

object TypeAliasRegistry {
    private val _aliases = mutableListOf<TypeAlias>()

    @JvmStatic
    fun register(typeAlias: TypeAlias) {
        System.out.println("Registering type alias ${typeAlias.qualifiedName}")
        _aliases.add(typeAlias)
    }

    @JvmStatic
    fun register(registrars: List<KClass<out TypeAliasRegistrar>>) {
        registrars.forEach {
            val instance = it.createInstance()
            instance.register()
        }
    }

    @JvmStatic
    val typeAliases: List<TypeAlias>
        get() {
            return _aliases.toList()
        }
}