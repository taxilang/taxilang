package lang.taxi.plugins.jpa.kotlin

import lang.taxi.generators.kotlin.AnnotationFactories
import lang.taxi.generators.kotlin.FieldAnnotationInjector
import lang.taxi.generators.kotlin.TypeAnnotationInjector
import javax.persistence.Entity
import javax.persistence.Id

object KotlinJpaProcessors {
    val processors = listOf(
            FieldAnnotationInjector("Id", AnnotationFactories.forType<Id>()),
            TypeAnnotationInjector("Entity", AnnotationFactories.forType<Entity>())
    )
}