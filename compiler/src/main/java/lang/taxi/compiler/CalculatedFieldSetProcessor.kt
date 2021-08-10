package lang.taxi.compiler

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.TypeSystem
import lang.taxi.types.*

class CalculatedFieldSetProcessor internal constructor(private val compiler: FieldCompiler) {
   fun compileCalculatedField(calculatedExpressionContext: TaxiParser.CalculatedMemberDeclarationContext,
                              namespace: Namespace): Either<List<CompilationError>, Field> {
      val typeMemberDeclaration = calculatedExpressionContext.typeMemberDeclaration()
      val operatorExpression = calculatedExpressionContext.operatorExpression()
      if (operatorExpression != null) {
         val operands = operatorExpression.typeType().map {
            QualifiedName.from(compiler.lookupTypeByName(it))
         }
         val operator = FormulaOperator.forSymbol(operatorExpression.arithmaticOperator().text)
         val formula = OperatorFormula(operands, operator)
         return compiler.compileCalculatedField(typeMemberDeclaration, formula, namespace).map { f ->
            f.copy(formula = formula)
         }
      }
      return Either.left(listOf(CompilationError(calculatedExpressionContext.start, "Invalid Calculated Field Definition")))
   }

   companion object {
      fun validate(field: Field, typeSystem: TypeSystem, objectType: ObjectType): List<CompilationError> {
         val formula = field.type.calculation!!
         if (!formula.operator.validArgumentSize(formula.operandFields.size)) {
            return listOf(CompilationError(objectType, "A formula must have exactly ${formula.operator.cardinality} fields"))
         }

         fun noPrimitiveTypeFoundError(name: QualifiedName): CompilationError {
            return CompilationError(objectType, "Type ${name.fullyQualifiedName} does not contain a base primitive type")
         }

         val compilationErrors = mutableListOf<CompilationError>()
         val operands = formula.operandFields.mapNotNull {
            val basePrimitive = typeSystem.getType(it.fullyQualifiedName).basePrimitive
            if (basePrimitive == null) {
               compilationErrors.add(noPrimitiveTypeFoundError(it))
            }
            basePrimitive
         }

         if (compilationErrors.isNotEmpty()) {
            return compilationErrors.toList()
         }
         val fieldBaseType = typeSystem.getType(field.type.qualifiedName).basePrimitive
         if (fieldBaseType == null) {
            compilationErrors.add(noPrimitiveTypeFoundError(field.type.toQualifiedName()))
         }
         return if (!formula.operator.validateArguments(operands, fieldBaseType!!)) {
            listOf(CompilationError(objectType, "Cannot perform operation ${formula.operator} on types ${operands.map { it.qualifiedName }.joinToString()}"))
         } else {
            emptyList()
         }
      }
   }
}
