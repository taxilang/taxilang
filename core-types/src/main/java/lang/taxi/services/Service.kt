package lang.taxi.services

import lang.taxi.ImmutableEquality
import lang.taxi.accessors.Argument
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ConstraintTarget
import lang.taxi.types.*
import lang.taxi.types.Annotation

data class Parameter(
   override val annotations: List<Annotation>,
   override val type: Type,
   override val name: String,
   override val constraints: List<Constraint>,
   val isVarArg: Boolean = false,
   override val typeDoc: String? = null,
   val nullable: Boolean = false
) : Annotatable, ConstraintTarget, NameTypePair, TaxiStatementGenerator, Documented, Argument {
   override val description: String = "param $name type ${type.qualifiedName}"
   override fun asTaxi(): String {
      val annotationTaxi = annotations.joinToString(" ") { it.asTaxi() }
      val namePrefix = if (name.isNullOrBlank()) "" else "$name:"
      return "$annotationTaxi $namePrefix ${type.qualifiedName}".trim()
   }
}

interface ServiceMember : Annotatable, Compiled, Documented, Named {
   val name: String
   val parameters: List<Parameter>
   val returnType: Type

   override val qualifiedName: String
      get() {
         return name
      }

   val referencedTypes: List<Type>
      get() {
         return this.parameters.map { it.type } + returnType
      }
}

data class ServiceDefinition(
   val qualifiedName: String,
   val operations: List<String>
)

data class ConsumedOperation(val serviceName: String, val operationName: String)
data class Service(
   override val qualifiedName: String,
   val members: List<ServiceMember>,
   override val annotations: List<Annotation>,
   override val compilationUnits: List<CompilationUnit>,
   override val typeDoc: String? = null,
   val lineage: ServiceLineage? = null
) : Annotatable, Named, ImportableToken, Compiled, Documented {
   private val equality = ImmutableEquality(this, Service::qualifiedName, Service::members, Service::annotations)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

   val operations: List<Operation> = this.members.filterIsInstance<Operation>()
   val queryOperations: List<QueryOperation> = this.members.filterIsInstance<QueryOperation>()

   val tables: List<Table> = this.members.filterIsInstance<Table>()
   val streams: List<Stream> = this.members.filterIsInstance<Stream>()

   fun operation(name: String): Operation {
      return this.operations.first { it.name == name }
   }

   fun queryOperation(name: String): QueryOperation {
      return this.queryOperations.first { it.name == name }
   }

   fun table(name: String): Table {
      return this.tables.first { it.name == name }
   }

   fun stream(name: String): Stream {
      return this.streams.first { it.name == name }
   }

   fun containsOperation(name: String) = operations.any { it.name == name }

   val referencedTypes: List<Type> = this.members.flatMap { it.referencedTypes }
}

typealias FieldName = String

data class OperationContract(
   val returnType: Type,
   val returnTypeConstraints: List<Constraint>
) : ConstraintTarget {
   override val description: String = "Operation returning ${returnType.qualifiedName}"
   override val constraints: List<Constraint> = returnTypeConstraints
}
