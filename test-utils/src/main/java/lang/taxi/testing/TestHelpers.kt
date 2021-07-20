package lang.taxi.testing

import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.services.Service
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
      if (generatedDoc == expectedDoc) return generatedDoc

      val typeErros = expectedDoc.types.flatMap { type -> collateTypeErrors(type, generatedDoc) } + generatedDoc.types.flatMap { type -> collateTypeErrors(type, expectedDoc) }
      val serviceErrors = expectedDoc.services.flatMap { service -> collateServiceErrors(service, generatedDoc) } + generatedDoc.services.flatMap { service -> collateServiceErrors(service, expectedDoc) }
      val errors = (typeErros + serviceErrors).toSet()
      if (errors.isEmpty()) {
         return generatedDoc
      }
      throw AssertionError("Generated docs did not match expected.  Errors:\n" + errors.joinToString("\n"))
   }

   fun compile(generated: List<String>) =
      try {
         Compiler.forStrings(generated).compile()
      } catch (e: CompilationException) {
         println("Failed to compile:\n\n$generated")
         throw e
      }

   private fun collateTypeErrors(type: Type, generatedDoc: TaxiDocument): List<String> {
      if (!generatedDoc.containsType(type.qualifiedName)) {
         return listOf("Type ${type.qualifiedName} not present")
      }
      val generatedType = generatedDoc.type(type.qualifiedName)
      val errors = mutableListOf<String>()
      if (type != generatedType)
         return listOf("Type ${type.qualifiedName} is not the same as expected")
      else
         return emptyList()
   }

   private fun collateServiceErrors(service: Service, generatedDoc: TaxiDocument): List<String> {
      if (!generatedDoc.containsService(service.qualifiedName)) {
         return listOf("Service ${service.qualifiedName} not present")
      }
      val generatedService = generatedDoc.service(service.qualifiedName)
      return service.operations.flatMap { operation ->

         if (!generatedService.containsOperation(operation.name)) {
            listOf("Operation ${operation.name} is not present")
         } else {
            val errors = mutableListOf<String>()
            val generatedOperation = generatedService.operation(operation.name)
            if (generatedOperation.contract != operation.contract) errors.add("Contract on ${operation.name} differs from expected")
            if (generatedOperation.parameters != operation.parameters) errors.add("Parameters on ${operation.name} differs from expected")
            if (generatedOperation.returnType != operation.returnType) errors.add("Return type on ${operation.returnType} differs from expected")
            if (generatedOperation.annotations != operation.annotations) errors.add("Annotations on ${operation.name} differs from expected")
            errors
         }
      }
   }
}
