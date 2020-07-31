package lang.taxi.compiler

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.TypeSystem
import lang.taxi.types.*

class CalculatedFieldSetProcessor internal constructor(private val compiler: TokenProcessor) {
   fun compileCalculatedField(calculatedExpressionContext: TaxiParser.CalculatedMemberDeclarationContext,
                              namespace: Namespace): Either<List<CompilationError>, Field> {
      val typeMemberDeclaration = calculatedExpressionContext.typeMemberDeclaration()
      val calculationExpression = calculatedExpressionContext.operatorExpression()
      val operands = calculationExpression.typeType().map {
         QualifiedName.from(compiler.lookupTypeByName(it))
      }
      val operator = FormulaOperator.forSymbol(calculationExpression.arithmaticOperator().text)
      val formula = OperatorFormula(operands, operator)
      return compiler.compileCalculatedField(typeMemberDeclaration, formula, namespace).map { f ->
         f.copy(formula = formula)
      }
   }

   companion object {
      fun validate(field: Field, typeSystem: TypeSystem, objectType: ObjectType): List<CompilationError> {
         val formula = field.type.calculation!!
         if (formula.operandFields.size != 2) {
            return listOf(CompilationError(objectType, "A formula must have exactly two fields"))
         }

         fun noPrimitiveTypeFoundError(name: QualifiedName): CompilationError {
            return CompilationError(objectType, "Type ${name.fullyQualifiedName} does not contain a base primitive type")
         }

         val firstOperand = typeSystem.getType(formula.operandFields[0].fullyQualifiedName).basePrimitive
            ?: return listOf(noPrimitiveTypeFoundError(formula.operandFields[0]))
         val secondOperand = typeSystem.getType(formula.operandFields[1].fullyQualifiedName).basePrimitive
            ?: return listOf(noPrimitiveTypeFoundError(formula.operandFields[1]))

         return if (!PrimitiveTypeOperations.isValidOperation(firstOperand, formula.operator, secondOperand)) {
            listOf(CompilationError(objectType, "Cannot perform operation ${formula.operator} on types ${firstOperand.basePrimitive!!.qualifiedName} and  ${secondOperand.basePrimitive!!.qualifiedName}"))
         } else {
            emptyList()
         }
      }
   }
}
