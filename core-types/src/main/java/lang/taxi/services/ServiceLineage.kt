package lang.taxi.services

import lang.taxi.types.*
import lang.taxi.types.Annotation

data class ServiceLineage(
   val consumes: List<ConsumedOperation>,
   val stores: List<QualifiedName>,
   override val annotations: List<Annotation>,
   override val compilationUnits: List<CompilationUnit>,
   override val typeDoc: String? = null
) : Annotatable, Compiled, Documented
