package lang.taxi.generators.kotlin

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import lang.taxi.types.Annotatable
import lang.taxi.types.Type
import lang.taxi.generators.FieldProcessor
import lang.taxi.generators.TypeProcessor
import lang.taxi.types.Annotation
import lang.taxi.types.Field
import kotlin.reflect.full.createInstance

typealias AnnotationFactory = (Annotation) -> AnnotationSpec

object AnnotationFactories {
    inline fun <reified T : kotlin.Annotation> forType(): AnnotationFactory {
        return { _ -> AnnotationSpec.get(T::class.createInstance()) }
    }
}

open class BaseAnnotationInjector(protected val annotationName: String, protected val annotationFactory: AnnotationFactory) {
    protected fun getAnnotations(target: Annotatable): Sequence<AnnotationSpec> {
        return target.annotations
                .asSequence()
                .filter { it.qualifiedName == annotationName }
                .map(annotationFactory)
    }
}

class FieldAnnotationInjector(annotationName: String, annotationFactory: AnnotationFactory) : FieldProcessor<PropertySpec.Builder>, BaseAnnotationInjector(annotationName, annotationFactory) {
    override fun process(builder: PropertySpec.Builder, field: Field): PropertySpec.Builder {
        return builder.addAnnotations(getAnnotations(field).asIterable())
    }
}

class JpaEnumFieldInjector(annotationName: String, annotationFactory: AnnotationFactory, val enumeratedValue: Any) :
   FieldProcessor<PropertySpec.Builder>, BaseAnnotationInjector(annotationName, annotationFactory) {
   override fun process(builder: PropertySpec.Builder, field: Field): PropertySpec.Builder {
      val annotationSpecs = field.annotations
         .asSequence()
         .filter { it.qualifiedName == annotationName }
         .map(annotationFactory)
         .map { it.toBuilder().addMember("%T.%N", ClassName("javax.persistence","EnumType"), "STRING" ).build() }
      return builder.addAnnotations(annotationSpecs.asIterable())
   }
}
class TypeAnnotationInjector(annotationName: String, annotationFactory: AnnotationFactory) : TypeProcessor<TypeSpec.Builder>, BaseAnnotationInjector(annotationName, annotationFactory) {
    override fun process(builder: TypeSpec.Builder, type: Type): TypeSpec.Builder {
        return when (type) {
            is Annotatable -> builder.addAnnotations(getAnnotations(type).asIterable())
            else -> builder
        }
    }

}
