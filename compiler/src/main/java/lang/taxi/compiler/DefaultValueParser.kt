package lang.taxi.compiler

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.source
import lang.taxi.types.EnumType
import lang.taxi.types.EnumValue
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.value
import org.antlr.v4.runtime.ParserRuleContext

class DefaultValueParser {
   fun parseDefaultValue(defaultDefinitionContext: TaxiParser.DefaultDefinitionContext, targetType: Type): Either<CompilationError, Any?> {
      return when {
         defaultDefinitionContext.literal() != null -> {
            assertLiteralDefaultValue(targetType, defaultDefinitionContext.literal().value(), defaultDefinitionContext)
         }
         defaultDefinitionContext.qualifiedName() != null -> {
            if (targetType !is EnumType) {
               CompilationError(defaultDefinitionContext.qualifiedName().start, "Cannot use an enum as a reference here, as ${targetType.qualifiedName} is not an enum").left()
            } else {
               val (enumTypeName, enumValue) = EnumValue.qualifiedNameFrom(defaultDefinitionContext.qualifiedName().text)
               val enumValueQualifiedName = "$enumTypeName.$enumValue"
               if (targetType.qualifiedName != enumTypeName.fullyQualifiedName) {
                  CompilationError(defaultDefinitionContext.qualifiedName().start, "Cannot assign a default of $enumValueQualifiedName to an enum with a type of ${targetType.qualifiedName} because the types are different").left()
               } else {
                  assertEnumDefaultValueCompatibility(targetType, enumValueQualifiedName, defaultDefinitionContext.qualifiedName())
               }
            }
         }
         else -> error("Unexpected branch of parseDefaultValue didn't match any conditions")
      }
   }

   private fun assertLiteralDefaultValue(targetType: Type, defaultValue: Any, typeRule: ParserRuleContext): Either<CompilationError, Any> {
      val valid = when {
         targetType.basePrimitive == PrimitiveType.STRING && defaultValue is String -> true
         targetType.basePrimitive == PrimitiveType.DECIMAL && defaultValue is Number -> true
         targetType.basePrimitive == PrimitiveType.INTEGER && defaultValue is Number -> true
         targetType.basePrimitive == PrimitiveType.BOOLEAN && defaultValue is Boolean -> true
         else -> false
      }
      return if (!valid) {
         CompilationError(typeRule.start, "Default value $defaultValue is not compatible with ${targetType.basePrimitive?.qualifiedName}", typeRule.source().sourceName).left()
      } else {
         defaultValue.right()
      }
   }

   private fun assertEnumDefaultValueCompatibility(enumType: EnumType, defaultValue: String, typeRule: ParserRuleContext): Either<CompilationError, EnumValue> {
      return enumType.values.firstOrNull { enumValue -> enumValue.qualifiedName == defaultValue }?.right()
         ?: CompilationError(typeRule.start, "${enumType.toQualifiedName().fullyQualifiedName} has no value of $defaultValue", typeRule.source().sourceName).left()
   }
}
