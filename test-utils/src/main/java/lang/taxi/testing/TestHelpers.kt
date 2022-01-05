package lang.taxi.testing

import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.services.Service
import lang.taxi.types.Named
import lang.taxi.types.Type

object TestHelpers {
   /**
    * Asserts both objects compile to the same taxi object model.
    * Returns the taxi doc from the generated model, for further assertions
    */
   fun expectToCompileTheSame(generated: List<String>, expected: String): TaxiDocument {
      return expectToCompileTheSame(generated, listOf(expected))
   }

   /**
    * Asserts both objects compile to the same taxi object model.
    * Returns the taxi doc from the generated model, for further assertions
    */

   fun expectToCompileTheSame(generated: List<String>, expected: List<String>): TaxiDocument {
      val generatedDoc = compile(generated)
      val expectedDoc = compile(expected)
      return assertAreTheSame(generatedDoc, expectedDoc, generated)
   }
   fun assertAreTheSame(generatedDoc:TaxiDocument, expectedDoc:TaxiDocument, generatedSources:List<String>):TaxiDocument {
      return assertAreTheSame(generatedDoc, expectedDoc, generatedSources.joinToString("\n"))
   }
   fun assertAreTheSame(generatedDoc:TaxiDocument, expectedDoc:TaxiDocument, generatedSource:String):TaxiDocument {
      if (generatedDoc == expectedDoc) return generatedDoc

      val typeErrors = diff(generatedDoc.types, expectedDoc.types) + expectedDoc.types.flatMap { type -> collateTypeErrors(type, generatedDoc) }
      val serviceErrors = diff(generatedDoc.services, expectedDoc.services) + expectedDoc.services.flatMap { service -> collateServiceErrors(service, generatedDoc) }
      val errors = (typeErrors + serviceErrors).toSet()
      if (errors.isEmpty()) {
         return generatedDoc
      }
      throw AssertionError("Generated docs did not match expected.  Errors:\n" + errors.joinToString("\n") +"\n\nGenerated:\n${generatedSource}")
   }

   fun compile(generated: List<String>):TaxiDocument =
      try {
         Compiler.forStrings(generated).compile()
      } catch (e: CompilationException) {
         println("Failed to compile:\n\n${generated.joinToString("\n")}")
         throw e
      }

   private inline fun <reified T : Named> diff(generatedThings: Set<T>, expectedThings: Set<T>): List<String> {
      val generatedNames = generatedThings.map { it.qualifiedName }.toSet()
      val expectedNames = expectedThings.map { it.qualifiedName }.toSet()
      return (generatedNames - expectedNames).map { name ->
         "${T::class.simpleName} $name was present but not expected"
      } + (expectedNames - generatedNames).map { name ->
         "${T::class.simpleName} $name not present"
      }
   }

   private fun collateTypeErrors(type: Type, generatedDoc: TaxiDocument): List<String> {
      return if (generatedDoc.containsType(type.qualifiedName)) {
         val generatedType = generatedDoc.type(type.qualifiedName)
         return if (type != generatedType)
            listOf("Type ${type.qualifiedName} is not the same as expected")
         else
            emptyList()
      } else emptyList()
   }

   private fun collateServiceErrors(service: Service, generatedDoc: TaxiDocument): List<String> {
      return if (generatedDoc.containsService(service.qualifiedName)) {
         val generatedService = generatedDoc.service(service.qualifiedName)
         val errors = mutableListOf<String>()
         if (generatedService.annotations != service.annotations) {
            errors.add(error("Annotations on ${service.qualifiedName} differs from expected", service.annotations, generatedService.annotations))
         }
         service.operations.forEach { operation ->
            if (!generatedService.containsOperation(operation.name)) {
               errors.add("Operation ${operation.name} is not present")
            } else {
               val generatedOperation = generatedService.operation(operation.name)
               if (generatedOperation.contract != operation.contract) errors.add(error("Contract on ${operation.name} differs from expected", operation.contract, generatedOperation.contract))
               if (generatedOperation.parameters != operation.parameters) errors.add(error("Parameters on ${operation.name} differs from expected", operation.parameters, generatedOperation.parameters))
               if (generatedOperation.returnType != operation.returnType) errors.add(error("Return type on ${operation.returnType} differs from expected", operation.returnType, generatedOperation.returnType))
               if (generatedOperation.annotations != operation.annotations) errors.add(error("Annotations on ${operation.name} differs from expected", operation.annotations, generatedOperation.annotations))
            }
         }
         return errors
      } else emptyList()
   }
}

fun error(message: String, expected: Any?, actual: Any?) =
   "$message:\nExpected: $expected\nActual:   $actual"

