package lang.taxi.testing

import com.google.common.collect.Maps
import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.services.Service
import lang.taxi.services.ServiceMember
import lang.taxi.types.Annotation
import lang.taxi.types.Named
import lang.taxi.types.QualifiedName
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

   private fun assertAreTheSame(
      generatedDoc: TaxiDocument,
      expectedDoc: TaxiDocument,
      generatedSources: List<String>
   ): TaxiDocument {
      return assertAreTheSame(generatedDoc, expectedDoc, generatedSources.joinToString("\n"))
   }

   fun assertAreTheSame(generatedDoc: TaxiDocument, expectedDoc: TaxiDocument, generatedSource: String): TaxiDocument {
      if (generatedDoc == expectedDoc) return generatedDoc

      val typeErrors = diff(generatedDoc.types, expectedDoc.types) + expectedDoc.types.flatMap { type ->
         collateTypeErrors(
            type,
            generatedDoc
         )
      }
      val serviceErrors = diffServices(
         generatedDoc.services.toList(),
         expectedDoc.services.toList()
      )
      val errors = (typeErrors + serviceErrors).toSet()
      if (errors.isEmpty()) {
         return generatedDoc
      }
      throw AssertionError("Generated docs did not match expected.  Errors:\n" + errors.joinToString("\n") + "\n\nGenerated:\n${generatedSource}")
   }

   fun compile(generated: List<String>): TaxiDocument =
      try {
         Compiler.forStrings(generated).compile()
      } catch (e: CompilationException) {
         println("Failed to compile:\n\n${generated.joinToString("\n")}")
         throw e
      }

   private fun diffAnnotations(
      generated: List<lang.taxi.types.Annotation>,
      expected: List<lang.taxi.types.Annotation>,
      annotatedElement: QualifiedName
   ): List<String> {
      val expectedByName = expected.groupBy { annotation -> annotation.qualifiedName }
      val actualByName = generated.groupBy { annotation -> annotation.qualifiedName }

      val missingFromActual = expectedByName.filter { !actualByName.containsKey(it.key) }
         .map { (name, _) -> "Annotation $name is not present on ${annotatedElement.fullyQualifiedName}" }
      val unexpected = actualByName.filter { !expectedByName.containsKey(it.key) }
         .map { (name, _) -> "Annotation $name is present on ${annotatedElement.fullyQualifiedName} but was not expected" }
      val presentButDifferent = expected.flatMap { expectedAnnotation ->
         val actualAnnotation = actualByName[expectedAnnotation.qualifiedName]?.singleOrNull()
         actualAnnotation?.let { diffAnnotation(expectedAnnotation, it, annotatedElement) } ?: emptyList()
      }

      return missingFromActual + unexpected + presentButDifferent
   }

   private fun diffAnnotation(
      generated: Annotation,
      expected: Annotation,
      annotatedElement: QualifiedName
   ): List<String> {
      val parameterDifference = Maps.difference(expected.parameters, generated.parameters)
      val expectedButNotPresentParams = parameterDifference.entriesOnlyOnLeft()
         .map { "Parameter ${it.key} is expected, but not found on annotation ${expected.qualifiedName} on element ${annotatedElement.fullyQualifiedName}" }
      val unexpectedParams = parameterDifference.entriesOnlyOnRight()
         .map { "Parameter ${it.key} is not expected, but not found on annotation ${expected.qualifiedName} on element ${annotatedElement.fullyQualifiedName}" }
      val paramsWithDifferentValue = parameterDifference.entriesDiffering()
         .map { (name, difference) -> "Parameter $name in annotation ${expected.qualifiedName} on element ${annotatedElement.fullyQualifiedName} does not have the expected value:  Expected: ${difference.leftValue()} Actual: ${difference.rightValue()}" }
      return expectedButNotPresentParams + unexpectedParams + paramsWithDifferentValue
   }

   private fun <T : Named> diffNamed(
      generated: List<T>,
      expected: List<T>,
      memberType: String,
      differ: (T, T) -> List<String>
   ): List<String> {
      val expectedMembers = expected.associateBy { it.qualifiedName }
      val actualMembers = generated.associateBy { it.qualifiedName }
      val difference = Maps.difference(expectedMembers, actualMembers)

      val expectedButNotPresent =
         difference.entriesOnlyOnLeft().map { "$memberType ${it.key} was expected, but not found" }
      val unexpected = difference.entriesOnlyOnRight().map { "$memberType ${it.key} was found, but not expected" }
      val presentButDifferent = difference.entriesDiffering().flatMap { (key, _) ->
         val expectedMember = expectedMembers[key]!!
         val generatedMember = actualMembers[key]!!
         differ(generatedMember, expectedMember)
      }
      return expectedButNotPresent + unexpected + presentButDifferent
   }

   private fun diffServices(generated: List<Service>, expected: List<Service>): List<String> {
      return diffNamed(generated, expected, "Service", ::diffService)
   }

   private fun diffService(generated: Service, expected: Service): List<String> {
      val annotationDifferences =
         diffAnnotations(generated.annotations, expected.annotations, generated.toQualifiedName())
      val memberDifferences = diffNamed(generated.members, expected.members, "Service member", ::diffServiceMember)
      return annotationDifferences + memberDifferences
   }

   private fun diffServiceMember(generated: ServiceMember, expected: ServiceMember): List<String> {
      val annotationDifferences =
         diffAnnotations(generated.annotations, expected.annotations, generated.toQualifiedName())
      val otherDifferences = mutableListOf<String>()

      if (generated.returnType != expected.returnType) {
         otherDifferences.add("${generated.name} does not return the expected type.  Expected ${expected.returnType.qualifiedName} Actual: ${generated.returnType.qualifiedName}")
      }
      if (generated.parameters != expected.parameters) {
         otherDifferences.add("${generated.name} has different parameters.  Expected: ${expected.parameters.joinToString { it.description }}  Actual: ${generated.parameters.joinToString { it.description }}")
      }
      return annotationDifferences + otherDifferences
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
            errors.add(
               error(
                  "Annotations on ${service.qualifiedName} differs from expected",
                  service.annotations,
                  generatedService.annotations
               )
            )
         }
         service.operations.forEach { operation ->
            if (!generatedService.containsOperation(operation.name)) {
               errors.add("Operation ${operation.name} is not present")
            } else {
               val generatedOperation = generatedService.operation(operation.name)
               if (generatedOperation.contract != operation.contract) errors.add(
                  error(
                     "Contract on ${operation.name} differs from expected",
                     operation.contract,
                     generatedOperation.contract
                  )
               )
               if (generatedOperation.parameters != operation.parameters) errors.add(
                  error(
                     "Parameters on ${operation.name} differs from expected",
                     operation.parameters,
                     generatedOperation.parameters
                  )
               )
               if (generatedOperation.returnType != operation.returnType) errors.add(
                  error(
                     "Return type on ${operation.returnType} differs from expected",
                     operation.returnType,
                     generatedOperation.returnType
                  )
               )
               if (generatedOperation.annotations != operation.annotations) errors.add(
                  error(
                     "Annotations on ${operation.name} differs from expected",
                     operation.annotations,
                     generatedOperation.annotations
                  )
               )
            }
         }
         return errors
      } else emptyList()
   }
}

fun error(message: String, expected: Any?, actual: Any?) =
   "$message:\nExpected: $expected\nActual:   $actual"

fun String.shouldCompileTheSameAs(expected: String): TaxiDocument {
   return TestHelpers.expectToCompileTheSame(listOf(this), expected)
}
