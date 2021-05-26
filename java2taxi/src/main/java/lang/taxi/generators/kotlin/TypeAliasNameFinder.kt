package lang.taxi.generators.kotlin

import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.jvm.internal.impl.types.AbbreviatedType

object TypeAliasNameFinder {

   fun findTypeAliasName(kotlinType: KProperty<Any?>): String? {
      val ktype = kotlinType.returnType
      // Note - we have to access the class this way, as the Impl class is hidden from us
      return findTypeAliasName(ktype)
   }

   fun findTypeAliasName(parameter: KParameter?): String? {
      return findTypeAliasName(parameter?.type)
   }

   fun findTypeAliasName(ktype: KType?): String? {
      if (ktype == null) return null
      val kTypeClass = ktype::class.java
      val ktypeTypeField = kTypeClass.getDeclaredField("type")
      ktypeTypeField.isAccessible = true
      val abbreviatedType = ktypeTypeField.get(ktype) as? AbbreviatedType
      val abbreviatedTypeName = abbreviatedType?.abbreviation?.toString()
      return abbreviatedTypeName
   }

}
