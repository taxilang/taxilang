package lang.taxi.cli.plugins.internal

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.TypeSpec
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.generators.kotlin.AnnotationFactories
import lang.taxi.generators.kotlin.FieldAnnotationInjector
import lang.taxi.generators.kotlin.JpaEnumFieldInjector
import lang.taxi.generators.kotlin.TypeAnnotationInjector
import lang.taxi.generators.kotlin.className
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.ArtifactId
import lang.taxi.plugins.ComponentProviderPlugin
import org.springframework.stereotype.Component
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.IdClass
import kotlin.reflect.full.createInstance

@Component
class JpaPlugin : InternalPlugin, ComponentProviderPlugin {
   override val artifact = Artifact.parse("jpa")
   override val comoponents: List<Any>
      get() = KotlinJpaProcessors.processors
   override val id: ArtifactId
      get() = ArtifactId(this.artifact.group, this.artifact.name)
}

object KotlinJpaProcessors {
   val processors = listOf(
      FieldAnnotationInjector("Id", AnnotationFactories.forType<Id>()),
      JpaEnumFieldInjector("Enumerated", AnnotationFactories.forType<Enumerated>(), EnumType.STRING),
      TypeAnnotationInjector("Entity", AnnotationFactories.forType<Entity>()),
      TypeAnnotationInjector("IdClass") { annotation ->
         val value = annotation.parameters["value"] as String
         AnnotationSpec.get(IdClass(value = Class.forName(value).kotlin))
      }
   )
}
