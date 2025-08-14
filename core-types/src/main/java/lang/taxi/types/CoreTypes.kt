package lang.taxi.types

import arrow.core.Either
import arrow.core.sequence

interface TypeProvider {
   fun getType(qualifiedName: String): Type
}

data class InvalidNumberOfParametersError(val message: String) {
   companion object {
      fun forTypeAndCount(type: QualifiedName, expectedCount: Int) =
         InvalidNumberOfParametersError("Type $type expects $expectedCount arguments")
   }
}

interface GenericType : Type {
   val parameters: List<Type>

   fun withParameters(parameters: List<Type>): Either<InvalidNumberOfParametersError, GenericType>

   fun resolveTypes(typeSystem: TypeProvider): GenericType

   override fun toQualifiedName(): QualifiedName {
      val qualifiedName = QualifiedName.from(this.qualifiedName)
      return qualifiedName.copy(parameters = this.parameters.map { it.toQualifiedName() })
   }

}

interface Annotatable {
   val annotations: List<Annotation>
}

fun Annotatable.annotation(name: String): Annotation? {
   return this.annotations.singleOrNull { it.name == name }
}

fun Annotatable.annotationOrInheritedAnnotation(annotationType: AnnotationType): List<Annotation> {
   val foundOnTarget = this.annotations.filter {
      it.type != null && it.type.isAssignableTo(annotationType)
   }
   return if (this is ObjectType) {
      foundOnTarget + this.inheritsFrom
         .flatMap {  it.annotationOrInheritedAnnotation(annotationType) }
   } else foundOnTarget
}

fun List<Annotatable>.annotations(): List<Annotation> {
   return this.flatMap { it.annotations }
}
