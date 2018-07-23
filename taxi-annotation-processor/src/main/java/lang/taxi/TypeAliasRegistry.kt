package lang.taxi

import lang.taxi.kapt.KotlinTypeAlias
import kotlin.reflect.*
import kotlin.reflect.full.createInstance

interface TypeAliasRegistrar {
    fun register()
}

object TypeAliasRegistry {
    private val _aliases = mutableMapOf<String, KotlinTypeAlias>()

    @JvmStatic
    fun register(typeAlias: KotlinTypeAlias) {
        System.out.println("Registering type alias ${typeAlias.qualifiedName}")
        _aliases.put(typeAlias.qualifiedName, typeAlias)
    }

    @JvmStatic
    fun register(vararg registrars: KClass<out TypeAliasRegistrar>) {
        register(registrars.toList())
    }

    @JvmStatic
    fun register(registrars: List<KClass<out TypeAliasRegistrar>>) {
        registrars.forEach {
            val instance = it.createInstance()
            instance.register()
        }
    }

    fun findTypeAlias(kotlinType: KCallable<*>?): KotlinTypeAlias? {
        if (kotlinType == null) return null
        return when (kotlinType) {
            is KProperty -> AliasHunter.findTypeAlias(kotlinType)
            else -> null
        }
    }

    fun findTypeAlias(parameter:KParameter):KotlinTypeAlias? {
        return AliasHunter.findTypeAlias(parameter)
    }

    fun findTypeAlias(type:KType):KotlinTypeAlias? {
        return AliasHunter.findTypeAlias(type)
    }

    fun getAlias(qualifiedTypeAliasName: String): KotlinTypeAlias {
        return findAlias(qualifiedTypeAliasName) ?: error("No type alias registered with name $qualifiedTypeAliasName")
    }
    fun findAlias(qualifiedTypeAliasName: String): KotlinTypeAlias? {
        return _aliases[qualifiedTypeAliasName]
    }

    fun containsAlias(qualifiedTypeAliasName: String): Boolean {
        return _aliases.containsKey(qualifiedTypeAliasName)
    }

    @JvmStatic
    val typeAliases: List<KotlinTypeAlias>
        get() {
            return _aliases.values.toList()
        }
}