package lang.taxi.annotations

import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.internal.impl.types.AbbreviatedType

class ReflectionTest {

    @Test
    fun canGetReflection() {
        val p = Adult::class
        val annotations = p.annotations

        val f = Thing::class
        val c = f.primaryConstructor!!
        val constructorAnn = c.parameters[0].annotations
        val paramType = c.parameters[0].type
        val ktypeClass = paramType::class.java
        val typeTypeField = ktypeClass.getDeclaredField("type")
        typeTypeField.isAccessible = true
        val typeAliasType = typeTypeField.get(paramType) as AbbreviatedType
        val typeAlias = typeAliasType.abbreviation
        val typeAliasAnnotations = typeAlias.annotations
        val type2 = lang.taxi.annotations.Adult::class
        TODO()
    }
}

@Target(
        AnnotationTarget.TYPEALIAS,
        AnnotationTarget.VALUE_PARAMETER
)
annotation class TypeAlias(val value: KClass<*>)

@DataType("Adult")
typealias Adult = Person

data class Person(
        val name: String
)

data class Thing(val adult: Adult)

data class Car(
        val driver: Adult
)