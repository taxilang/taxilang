package lang.taxi.lsp.completion

import lang.taxi.lsp.CompilationResult
import lang.taxi.types.AnnotationType
import lang.taxi.types.EnumType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import java.util.concurrent.atomic.AtomicReference

/**
 * An abstraction for type completion.
 * Sometimes we're working off low-level primitives like
 * CompilationResult. Other times, we're working off high-level
 * abstractions like Orbital's Schema
 */
interface TypeRepository {
   fun getTypeNames(): List<Pair<QualifiedName, Type?>>
   fun getTypeName(text: String): QualifiedName?
   fun getEnumType(fullyQualifiedName: String): EnumType?
   fun annotation(annotationName: String): AnnotationType?
}

/**
 * A type repository based from compilation results.
 * Used in VSCode editor, where the set of types is constantly fluxing.
 */
class CompilationResultTypeRepository(
   private val lastSuccessfulCompilationResult: CompilationResult?,
   private val lastCompilationResult: CompilationResult?
) : TypeRepository {
   override fun getTypeNames(): List<Pair<QualifiedName, Type?>> {
      val compiledDoc = lastSuccessfulCompilationResult?.document
      val lastSuccessfulCompilationTypeNames = lastSuccessfulCompilationResult?.compiler?.declaredTypeNames()
         ?: emptyList()
      val lastCompilationResultTypeNames = lastCompilationResult?.compiler?.declaredTypeNames() ?: emptyList()
      val typeNames = (lastCompilationResultTypeNames + lastSuccessfulCompilationTypeNames).distinct()

      return typeNames.map { name ->
         if (compiledDoc?.containsType(name.fullyQualifiedName) == true) { // == true because of nulls
            name to compiledDoc.type(name.fullyQualifiedName)
         } else {
            name to null
         }
      }
   }

   override fun getTypeName(text: String): QualifiedName? {
      return lastCompilationResult?.compiler?.declaredTypeNames()?.firstOrNull { it ->
         it.typeName == text || it.fullyQualifiedName == text
      }
   }

   override fun getEnumType(fullyQualifiedName: String): EnumType? {
      return lastSuccessfulCompilationResult?.document?.enumType(fullyQualifiedName)
   }

   override fun annotation(annotationName: String): AnnotationType? {
      return lastSuccessfulCompilationResult?.document?.annotation(annotationName)
         ?: lastCompilationResult?.document?.annotation(annotationName)
   }

}
