package lang.taxi.types

import lang.taxi.services.operations.constraints.Constraint

data class Variable(
   val name: String?,
   val value: TypedValue
)

data class DiscoveryType(
   val type: QualifiedName,
   val constraints: List<Constraint>,
   /**
    * Starting facts aren't the same as a constraint, in that they don't
    * constraint the output type.  However, they do inform query strategies,
    * so we pop them here for query operations to consider.
    */
   val startingFacts: List<Variable>,
   /**
    * If the query body is an anonymoust type store the definition here,
    */
   val anonymousType: Type? = null
)


enum class QueryMode(val directive: String) {
   FIND_ONE("findOne"),
   FIND_ALL("findAll"),
   STREAM("stream");

   companion object {
      fun forToken(token: String): QueryMode {
         // Legacy support - findOne and findAll are deprcated in favour of find
         return if (token == "find") {
            FIND_ALL
         } else {
            values().first { it.directive == token }
         }
      }
   }
}

typealias TaxiQLQueryString = String

data class ProjectedType(val concreteType: Type?, val anonymousTypeDefinition: Type?) {
   companion object {
      fun fromConcreteTypeOnly(concreteType: Type) = ProjectedType(concreteType, null)
      fun fomAnonymousTypeOnly(anonymousTypeDefinition: Type) = ProjectedType(null, anonymousTypeDefinition)
   }
}


data class AnonymousTypeDefinition(
   val isList: Boolean = false,
   val fields: List<AnonymousFieldDefinition>,
   override val annotations: List<Annotation>,
   override val compilationUnit: CompilationUnit,
) : TypeDefinition, Annotatable {

}

interface AnonymousFieldDefinition {
   val fieldName: String
}

// Anonymous field definitions like:
// orderId
// productId: ProductId
data class SimpleAnonymousFieldDefinition(
   override val fieldName: String,
   val fieldType: Type
) : AnonymousFieldDefinition

// Anonymous field Definitions like:
// traderEmail : EmailAddress(from traderUtCode)
data class SelfReferencedFieldDefinition(
   override val fieldName: String,
   val fieldType: QualifiedName,
   val referenceFieldName: String,
   val referenceFieldContainingType: QualifiedName
) : AnonymousFieldDefinition

// Anonymous field Definitions like:
//    salesPerson {
//        firstName : FirstName
//        lastName : LastName
//    }(by this.salesUtCode)
data class ComplexFieldDefinition(
   override val fieldName: String,
   val referenceFieldName: String,
   val referenceFieldContainingType: QualifiedName,
   val fieldDefinitions: List<SimpleAnonymousFieldDefinition>
) : AnonymousFieldDefinition
