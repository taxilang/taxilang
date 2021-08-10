package lang.taxi.types

import lang.taxi.services.operations.constraints.Constraint

data class DiscoveryType(
   val type: QualifiedName,
   val constraints: List<Constraint>,
   /**
    * Starting facts aren't the same as a constraint, in that they don't
    * constraint the output type.  However, they do inform query strategies,
    * so we pop them here for query operations to consider.
    */
   val startingFacts: Map<String, TypedValue>
)


enum class QueryMode(val directive: String) {
   FIND_ONE("findOne"),
   FIND_ALL("findAll"),
   STREAM("stream");
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
   override val compilationUnit: CompilationUnit
) : TypeDefinition {

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
