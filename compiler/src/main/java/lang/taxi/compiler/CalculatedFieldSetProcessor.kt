package lang.taxi.compiler

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.TypeSystem
import lang.taxi.types.Field
import lang.taxi.types.Formula
import lang.taxi.types.FormulaOperator
import lang.taxi.types.MultiplicationFormula
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName

class CalculatedFieldSetProcessor internal constructor(private val compiler: TokenProcessor) {
   fun compileCalculatedField(calculatedExpressionContext: TaxiParser.CalculatedMemberDeclarationContext,
                              namespace: Namespace): Either<List<CompilationError>, Field> {
      val typeMemberDeclaration = calculatedExpressionContext.typeMemberDeclaration()
      val calculationExpression = calculatedExpressionContext.calculationExpression()
      val operands = calculationExpression.multiplicationExpression().typeType().map {
         QualifiedName.from(compiler.lookupTypeByName(it))
      }
      val formula = MultiplicationFormula(operands)
     return compiler.compileCalculatedField(typeMemberDeclaration, formula, namespace).map { f ->
         f.copy(formula = formula)
      }
   }
   companion object {
      fun validate(field: Field, typeSystem: TypeSystem, objectType: ObjectType): List<CompilationError> {
         val calculation = field.type.calculation!!
         return when (calculation.operator) {
            FormulaOperator.Multiply -> validateMultiply(calculation, typeSystem, objectType, field)
            else -> listOf()
         }
      }

      private fun validateMultiply(formula: Formula, typeSystem: TypeSystem, objectType: ObjectType, field: Field): List<CompilationError> {
        return formula.operandFields.mapNotNull { operandFullyQualifiedName ->
           val operandType = typeSystem.getType(operandFullyQualifiedName.fullyQualifiedName)
           when (operandType.basePrimitive) {
              PrimitiveType.ANY,
              PrimitiveType.ARRAY,
              PrimitiveType.BOOLEAN,
              PrimitiveType.INSTANT,
              PrimitiveType.LOCAL_DATE,
              PrimitiveType.DATE_TIME,
              PrimitiveType.STRING,
              PrimitiveType.TIME,
              PrimitiveType.VOID ->
                 CompilationError(objectType, "Operator $operandFullyQualifiedName has invalid Type in calculated field definition ${field.name}")
              else -> null
           }
        }
      }
   }
}
