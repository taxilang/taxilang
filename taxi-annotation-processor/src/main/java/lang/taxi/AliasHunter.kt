package lang.taxi

import lang.taxi.kapt.KotlinTypeAlias
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.jvm.internal.impl.types.AbbreviatedType

object AliasHunter {

    fun findTypeAlias(kotlinType: KProperty<Any?>): KotlinTypeAlias? {
        val ktype = kotlinType.returnType
        // Note - we have to access the class this way, as the Impl class is hidden from us
        return findTypeAlias(ktype)
    }

    fun findTypeAlias(parameter:KParameter):KotlinTypeAlias? {
        return findTypeAlias(parameter.type)
    }

    fun findTypeAlias(ktype: KType): KotlinTypeAlias? {
        val kTypeClass = ktype::class.java
        val ktypeTypeField = kTypeClass.getDeclaredField("type")
        ktypeTypeField.isAccessible = true
        val abbreviatedType = ktypeTypeField.get(ktype) as? AbbreviatedType
        val abbreviatedTypeName = abbreviatedType?.abbreviation?.toString()
        return if (abbreviatedTypeName != null) TypeAliasRegistry.getAlias(abbreviatedTypeName) else null
    }


}