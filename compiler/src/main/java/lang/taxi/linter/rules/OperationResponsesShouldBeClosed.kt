package lang.taxi.linter.rules

import lang.taxi.CompilationMessage
import lang.taxi.linter.OperationLinterRule
import lang.taxi.messages.Severity
import lang.taxi.services.Operation
import lang.taxi.types.ArrayType
import lang.taxi.types.Modifier
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.types.toQualifiedName

object OperationResponsesShouldBeClosed : OperationLinterRule {
   override val id: String = "operation-responses-should-be-closed"
   override fun evaluate(source: Operation): List<CompilationMessage> {
      return requireIsClosed(source, source.returnType)
   }

   private fun requireIsClosed(source: Operation, type: Type): List<CompilationMessage> {
      return when (type) {
         is ObjectType -> lintReturnObjectType(source, type)
         is ArrayType -> requireIsClosed(source, type.memberType)
         else -> emptyList()
      }
   }

   private fun lintReturnObjectType(operation: Operation, returnType: ObjectType): List<CompilationMessage> {
      if (returnType.modifiers.contains(Modifier.CLOSED)) return emptyList()

      return listOf(
         CompilationMessage(
            operation.compilationUnits.first(),
            "The return type from an operation should be declared as closed. Consider adding the 'closed' modifier to ${returnType.qualifiedName.toQualifiedName().typeName} ",
            severity = Severity.WARNING
         ),
         CompilationMessage(
            operation.compilationUnits.first(),
            "${returnType.qualifiedName.toQualifiedName().typeName} is used as the return type from operation ${operation.qualifiedName}. Consider adding the 'closed' modifier to this model, to prevent TaxiQL queries attempting to construct it.",
            severity = Severity.WARNING
         ),
      )
   }


}
