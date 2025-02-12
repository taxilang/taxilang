package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.findNamespace
import lang.taxi.toCompilationUnit
import lang.taxi.types.ArrayType
import lang.taxi.types.Modifier
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.utils.createCompilationError
import lang.taxi.utils.flatMapLeft
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList

class PartialModelCompiler(private val tokenProcessor: TokenProcessor) {
   companion object {
      fun partialTypeName(name: QualifiedName): QualifiedName {
         return QualifiedName(name.namespace, "${name.typeName}\$Partial")
      }
   }

   fun compilePartialModel(
      partialTypeName: QualifiedName,
      tokenRule: TaxiParser.PartialModelDeclarationContext
   ): Either<List<CompilationError>, ObjectType> {
      return tokenProcessor.parseType(tokenRule.findNamespace(), tokenRule.typeReference()).flatMap { baseType ->
         compilePartialModel(baseType, partialTypeName, tokenRule)
      }
   }

   private fun compilePartialModel(
      baseType: Type,
      partialTypeName: QualifiedName,
      tokenRule: TaxiParser.PartialModelDeclarationContext
   ): Either<List<CompilationError>, ObjectType> {
      return when (baseType) {
         is ObjectType -> compilePartialModel(baseType, partialTypeName, tokenRule)
         is ArrayType -> compilePartialModel(baseType, partialTypeName, tokenRule)
         else -> tokenRule.createCompilationError("Partial models are only supported on object types or arrays of object types")
      }
   }

   private fun compilePartialModel(
      baseType: ObjectType,
      partialTypeName: QualifiedName,
      tokenRule: TaxiParser.PartialModelDeclarationContext
   ): Either<List<CompilationError>, ObjectType> {
      val emptyType = ObjectType(
         partialTypeName.fullyQualifiedName,
         definition = null
      )
      tokenProcessor.typeSystem.register(emptyType)
      val newFields = baseType.allFields.map { field ->
         convertToPartialTypeIfRequired(field.name, field.type, tokenRule).map { fieldTypeAsPartial ->
            field.copy(
               type = fieldTypeAsPartial,
               nullable = true,
               compilationUnit = tokenRule.toCompilationUnit()
            )
         }
      }.invertEitherList().flattenErrors()

      return newFields.map { fields ->
         val modifiers = tokenRule.typeModifier().map { modifier -> Modifier.fromToken(modifier.text) }
         val partialModelAnnotations = tokenProcessor.collateAnnotations(tokenRule.annotation())
         val definition = baseType.definition!!.copy(
            fields = fields.toSet(),
            partialOfType = baseType,
            annotations = (baseType.definition!!.annotations + partialModelAnnotations),
            modifiers = (baseType.modifiers + modifiers).distinct()
         )
         emptyType.definition = definition
         emptyType
      }

   }

   private fun convertToPartialTypeIfRequired(
      // Only needed for helpful errors
      fieldName: String,
      type: Type,
      tokenRule: TaxiParser.PartialModelDeclarationContext
   ): Either<List<CompilationError>, Type> {
      return when {
         type.isScalar -> type.right()
         type is ObjectType -> convertObjectTypeToPartialTypeIfRequired(type as ObjectType, tokenRule)
         type is ArrayType -> convertArrayTypeToPartialType(type as ArrayType, fieldName, tokenRule)
         else -> tokenRule.createCompilationError("Creating partial type for field ${fieldName} with type of ${type.qualifiedName} is not supported")
      }
   }

   private fun convertArrayTypeToPartialType(
      arrayType: ArrayType,
      fieldName: String,
      tokenRule: TaxiParser.PartialModelDeclarationContext
   ): Either<List<CompilationError>, Type> {
      return convertToPartialTypeIfRequired(fieldName, arrayType.memberType, tokenRule)
         .map { partialMemberType ->
            ArrayType(partialMemberType, tokenRule.toCompilationUnit())
         }
   }

   private fun convertObjectTypeToPartialTypeIfRequired(
      type: ObjectType,
      tokenRule: TaxiParser.PartialModelDeclarationContext,
   ): Either<List<CompilationError>, Type> {
      val partialTypeName = partialTypeName(type.toQualifiedName())
      return tokenProcessor.typeSystem.getTypeOrError(partialTypeName.fullyQualifiedName, tokenRule)
         .flatMapLeft {
            compilePartialModel(type, partialTypeName, tokenRule)
         }


   }
}
