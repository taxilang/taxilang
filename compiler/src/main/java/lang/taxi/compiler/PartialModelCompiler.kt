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
            // Design choice: Partials cannot be closed.
            // Partials are intended to allow creation client-side of the object,
            // typically for sending to the server.
            // In some awkward scenarios, users need to be able to do things like
            // listOf(Thing$Partial) in a query - which won't construct if the
            // Thing is closed.
            // Note: This is true even if Thing is declared as a Parameter object, as object construction
            // happens in the query phase, not the parameter-building phase, so it's not treated as a parameter object.
            // eg:https://playground.taxilang.org/#pako:H4sIAAAAAAAAA51VbW/aMBD+K6do0qBidH2dFKnTiugHpK4w2MuHZh9MchBrjp3ZThlC/PedEwcS6KpqSKDk3vzcPc+ZTWDiFDMWhIFd5whDZGKUAJcpam4NzKzmchnJnfOBZfiCe4gm1jy3XMnjqEjmTFO+RQ2ZSlDAjC/leLGY4u8CjYVNJAGSfYnwsGYkt65MLJTBxNdwIVUmT0LfgHuTdFK4w/y/lSdsLRRLPDRmWZX5+NMnnJ7C1zEM7mB693n8/W4YyU/jjNuHQghz3K9L/ZZTGXwN4vKA15X7B8zK6cEeQ3W2KeaCxQgrbtN+v18eYjmN9LnD/DETH7LQKmvajyY4KWwLmSniGI0JYVY97DUyUEogq2kwqJ94XElq5p/LAipHzUp1LdE6bycpx1ePsRs28XSooa5LW9EZ2EjOi+eSezBXyTo8HitV3XfyQj0/l86+THtg3eeM1HLQC4wt5iYIHzfBrqoTAe1luzaFajS5ohGRj94wTtVIUlAQWl3g9mcvoGXSa/LyLFfatgjSkWxYR01Dexedw32daFI0CLl2wCxHAytViATmGKuM9rpSAZNwOxmBrrKdkCh5yZ9Qwqa+VW4gCs7OL6Kgd7j4NxTU2s4oaO5mAFvY1og8qh+kV/BaJUgIiZJvLaTsCYFBnHJRi3CV8jiFmBDOEQonzoXSYFMEwY0dLzpdV3LBJYn0ENgWmCHlaUd6vVg+yzX1pibWRewQxkyIpnTDsM1ho4uH/rwPIwLD5S/3axxAwko/9Ewzn7O5WIOxmvFlagn4imkn1IJaVStPBbdAAdIIkmxNmgJVuNXVVE5lucA/kBW2FFa4A3BSd9bWwG0cq0LaAym4uJrRe8XkbZ4LHpcVPbnX59eXl+fv3efq7OrDxfVlg+t20ZJylglH9b1a7Sl2p3gu3MimXuwVE50F18Z2qhu424V3H/fkHBE0UFqrFepqlYfk6jZj597t9n+we64jts2uS0I9/pLMOr4ZVBi3DC04DQ30mnZfqnXWySkt8+7Opatgs+0Fgq2V2+xNYIjtIWdL8gfhgiSPvdI2RZa4a6Jh+lKtv7sNKsPM/8WX98P2L1+GAEjzBwAA
            .filter { it !== Modifier.CLOSED }
         val partialModelAnnotations = tokenProcessor.collateAnnotations(tokenRule.annotation())
         val definition = baseType.definition!!.copy(
            fields = fields.toSet(),
            partialOfType = baseType,
            annotations = (baseType.definition!!.annotations + partialModelAnnotations),
            modifiers = (baseType.modifiers + modifiers).distinct()
               // Partials cannot be closed. See above.
               .filter { it !== Modifier.CLOSED }
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
