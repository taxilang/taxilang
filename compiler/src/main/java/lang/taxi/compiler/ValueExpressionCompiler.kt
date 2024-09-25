package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.ObjectValueContext
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.accessors.NullValue
import lang.taxi.expressions.Expression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.ObjectExpression
import lang.taxi.toCompilationUnit
import lang.taxi.toCompilationUnits
import lang.taxi.types.ArrayType
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.utils.wrapErrorsInList
import lang.taxi.values.PrimitiveValues

/**
 * This class is logic extracted from ExpressionCompiler
 * responsible for parsing object declarations (vs type declarations).
 */
internal class ValueExpressionCompiler(private val expressionCompiler: ExpressionCompiler) {
   fun objectValueAsExpression(
      objectValue: TaxiParser.ObjectValueContext,
      factType: Type?
   ): Either<List<CompilationError>, Expression> {
      val objectType = factType ?: PrimitiveType.ANY
      return readObjectValue(objectValue, objectType)
         .flatMap { parsedValue ->
            validateNoMissingFields(objectValue, parsedValue, objectType, false)
         }
         .map { parsedValue ->
            if (parsedValue == null) {
               LiteralExpression( LiteralAccessor(NullValue, objectType), objectValue.toCompilationUnits())
            } else {
               if (parsedValue is Map<*, *>) {
                  ObjectExpression(objectType, parsedValue as Map<String, Expression>,  objectValue.toCompilationUnits())
               } else {
                  LiteralExpression(LiteralAccessor(parsedValue, objectType), objectValue.toCompilationUnits())
               }
            }

         }
   }


   // This logic has been moved here as part of encapsulating this function.
   // However, validation of type contracts (including missing fields) is a more general
   // concern. We have upcoming work to fail if type contracts are violated, this should
   // be migrated as part of that work.
   private fun validateNoMissingFields(
      valueContext: ObjectValueContext,
      value: Any?,
      factType: Type,
      nullable: Boolean
   ): Either<List<CompilationError>, Any?> {
      return when (value) {
         null -> {
            if (!nullable) {
               listOf(CompilationError(valueContext.toCompilationUnit(), "null values are not supported here")).left()
            } else {
               Either.Right(null)
            }
         }

         is Collection<*> -> {
            // We don't need to verify that the inner types of the array match,
            // as that was verified whilst parsing the array.
            if (factType is ArrayType) {
               value.right()
            } else {
               listOf(
                  CompilationError(
                     valueContext.toCompilationUnit(),
                     "An array is not assignable to type ${factType.qualifiedName}"
                  )
               ).left()
            }
         }

         is Map<*, *> -> {
            if (factType == PrimitiveType.ANY) {
               return value.right()
            }
            // TODO : We should be verifying the type contract here.
            if (factType !is ObjectType) {
               return listOf(
                  CompilationError(
                     valueContext.toCompilationUnit(),
                     "Map is not assignable to type ${factType.qualifiedName}"
                  )
               ).left()
            }
            val missingRequiredFields = factType.fields
               .filter { !it.nullable }
               .filter { field -> !value.containsKey(field.name) }
            if (missingRequiredFields.isNotEmpty()) {
               return listOf(
                  CompilationError(
                     valueContext.toCompilationUnit(),
                     "Map is not assignable to type ${factType.qualifiedName} as mandatory properties ${missingRequiredFields.joinToString { it.name }} are missing"
                  )
               ).left()
            } else {
               value.right()
            }
         }

         is Expression -> {
            expressionCompiler.typeChecker.ifAssignable(value.returnType, factType, valueContext) { value }
               .wrapErrorsInList()
         }

         else -> {
            val primitiveType = PrimitiveValues.getTaxiPrimitive(value)
            expressionCompiler.typeChecker.ifAssignable(primitiveType, factType, valueContext) { value }
               .wrapErrorsInList()
         }
      }
   }

   private fun readObjectValue(
      objectValue: TaxiParser.ObjectValueContext,
      factType: Type
   ): Either<List<CompilationError>, Map<String, Any?>> {
      val errors = mutableListOf<CompilationError>()
      if (factType !is ObjectType && factType != PrimitiveType.ANY) {
         return listOf(
            CompilationError(
               objectValue.toCompilationUnit(),
               "Map is not assignable to ${factType.qualifiedName}"
            )
         ).left()
      }
      val mapResult = objectValue.objectField().map { objectField ->
         val fieldName = objectField.identifier().IdentifierToken().text
         val fieldType = if (factType is ObjectType) {
            factType.field(fieldName).type
         } else null // leave it null to let the compiler assign the right primitive type
         val fieldValue = expressionCompiler.compile(objectField.expressionGroup(), fieldType)
            .getOrElse {
               errors.addAll(it)
               null
            }
         fieldName to fieldValue
      }.toMap()

      return if (errors.isEmpty()) {
         mapResult.right()
      } else {
         errors.left()
      }
   }

}
