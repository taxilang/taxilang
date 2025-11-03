package lang.taxi.services

import lang.taxi.ImmutableEquality
import lang.taxi.accessors.Argument
import lang.taxi.expressions.Expression
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
   val nullable: Boolean = false,
   val defaultValue: Expression? = null
) : Annotatable, ConstraintTarget, NameTypePair, TaxiStatementGenerator, Documented, Argument {
   override val description: String = "param $name type ${type.qualifiedName}"
   override fun asTaxi(): String {
      val annotationTaxi = annotations.joinToString(" ") { it.asTaxi() }
      val namePrefix = if (name.isNullOrBlank()) "" else "$name:"
      return "$annotationTaxi $namePrefix ${type.qualifiedName}".trim()
   }
   override fun pruneFieldPath(path: List<String>): List<String> {
      require(path.first() == this.name) { "Expected that the provided path was the name of this parameter"}
      return path.drop(1)
   }

   override fun pruneFieldSelectors(fieldSelectors: List<FieldReferenceSelector>): List<FieldReferenceSelector> {
      require(fieldSelectors.first().fieldName == this.name) { "Expected that the provided selectors started with the name of this parameter"}
      return fieldSelectors.drop(1)
   }
}

interface Callable : Compiled, Named {
   val parameters: List<Parameter>
   //only nullable because some impls. need to supprt
   // late-bound definitions (ie., isDefined(...))
   // Should not be nullable once the thing is defined.
   val returnType: Type?

   fun getParameterType(parameterIndex: Int): Type {
      return when {
         parameterIndex < this.parameters.size -> {
            this.parameters[parameterIndex].type
         }

         this.parameters.last().isVarArg -> {
            return this.parameters.last().type
         }

         else -> {
            error("Parameter index $parameterIndex is out of bounds - function $qualifiedName only takes ${this.parameters.size} parameters")
         }
      }
   }
}
interface ServiceMember : Annotatable, Compiled, Documented, Named, Callable {
   val name: String
   override val returnType: Type

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
      return this.operations.firstOrNull { it.name == name }  ?: error("No operation named $name exists on ${this.qualifiedName}")
   }

   fun queryOperation(name: String): QueryOperation {
      return this.queryOperations.firstOrNull { it.name == name }  ?: error("No queryOperation named $name exists on ${this.qualifiedName}")
   }

   fun table(name: String): Table {
      return this.tables.firstOrNull { it.name == name }  ?: error("No table named $name exists on ${this.qualifiedName}")
   }

   fun stream(name: String): Stream {
      return this.streams.firstOrNull { it.name == name }  ?: error("No stream named $name exists on ${this.qualifiedName}")
   }


   fun containsOperation(name: String) = operations.any { it.name == name }

   fun containsMember(name: String) = members.any { it.name == name }

   fun member(name: String):ServiceMember {
      return this.members.firstOrNull { it.name == name } ?: error("No member named $name exists on ${this.qualifiedName}")
   }

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
