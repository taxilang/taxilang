package lang.taxi.services

import lang.taxi.ImmutableEquality
import lang.taxi.Operator
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ConstraintTarget
import lang.taxi.types.Annotatable
import lang.taxi.types.Annotation
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.Documented
import lang.taxi.types.ImportableToken
import lang.taxi.types.NameTypePair
import lang.taxi.types.Named
import lang.taxi.types.QualifiedName
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type

data class Parameter(
   override val annotations: List<Annotation>,
   override val type: Type,
   override val name: String?,
   override val constraints: List<Constraint>,
   val isVarArg: Boolean = false
) : Annotatable, ConstraintTarget, NameTypePair, TaxiStatementGenerator {
   override val description: String = "param $name"
   override fun asTaxi(): String {
      val annotationTaxi = annotations.joinToString(" ") { it.asTaxi() }
      val namePrefix = if (name.isNullOrBlank()) "" else "$name:"
      return "$annotationTaxi $namePrefix ${type.qualifiedName}".trim()
   }
}

interface ServiceMember : Annotatable, Compiled, Documented {
   val name: String
   val parameters: List<Parameter>
   val returnType: Type

   val referencedTypes: List<Type>
      get() {
         return this.parameters.map { it.type } + returnType
      }
}

data class QueryOperation(
   override val name: String,
   override val annotations: List<Annotation>,
   override val parameters: List<Parameter>,
   val grammar: String,
   override val returnType: Type,
   override val compilationUnits: List<CompilationUnit>,
   val capabilities: List<QueryOperationCapability>,
   override val typeDoc: String? = null
) : ServiceMember, Annotatable, Compiled, Documented, TaxiStatementGenerator {
   private val equality =
      ImmutableEquality(this, QueryOperation::name, QueryOperation::annotations, QueryOperation::returnType)

   override fun asTaxi(): String {
      val parameterTaxi = parameters.joinToString(",") { it.asTaxi() }
      val annotations = this.annotations.joinToString { it.asTaxi() }
      return """$annotations
         |$grammar query $name($parameterTaxi):${returnType.toQualifiedName().parameterizedName} with capabilities {
         |${this.capabilities.joinToString(", \n") { it.asTaxi() }}
         |}
      """.trimMargin()
         .trim()
   }

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

interface QueryOperationCapability : TaxiStatementGenerator {
   companion object {
      val ALL: List<QueryOperationCapability> = SimpleQueryCapability.values().toList() +
         listOf(
            FilterCapability(Operator.values().toList())
         )
   }
}

data class FilterCapability(val supportedOperations: List<Operator>) : QueryOperationCapability {

   override fun asTaxi(): String {
      return "filter(${this.supportedOperations.joinToString(",") { it.symbol }})"
   }
}

enum class SimpleQueryCapability(val symbol: String) : QueryOperationCapability {
   SUM("sum"),
   COUNT("count"),
   AVG("avg"),
   MIN("min"),
   MAX("max");

   override fun asTaxi(): String {
      return this.symbol
   }

   companion object {
      private val symbols = SimpleQueryCapability.values().associateBy { it.symbol }
      fun parse(value: String): SimpleQueryCapability {
         return symbols[value] ?: error("No capability matches symbol $value")
      }
   }
}

data class Operation(
   override val name: String,
   val scope: String? = null,
   override val annotations: List<Annotation>,
   override val parameters: List<Parameter>,
   override val returnType: Type,
   override val compilationUnits: List<CompilationUnit>,
   val contract: OperationContract? = null,
   override val typeDoc: String? = null
) : ServiceMember, Annotatable, Compiled, Documented {
   private val equality = ImmutableEquality(
      this,
      Operation::name,
      Operation::annotations,
      Operation::parameters,
      Operation::returnType,
      Operation::contract
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

}

data class ServiceLineage(
   val consumes: List<ConsumedOperation>,
   val stores: List<QualifiedName>,
   override val annotations: List<Annotation>,
   override val compilationUnits: List<CompilationUnit>,
   override val typeDoc: String? = null
) : Annotatable, Compiled, Documented

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
   private val equality = ImmutableEquality(this, Service::qualifiedName, Service::operations, Service::annotations)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

   val operations: List<Operation> = this.members.filterIsInstance<Operation>()
   val queryOperations: List<QueryOperation> = this.members.filterIsInstance<QueryOperation>()

   fun operation(name: String): Operation {
      return this.operations.first { it.name == name }
   }

   fun queryOperation(name: String): QueryOperation {
      return this.queryOperations.first { it.name == name }
   }

   fun containsOperation(name: String) = operations.any { it.name == name }

   val referencedTypes: List<Type> = this.members.flatMap { it.referencedTypes }
}

typealias FieldName = String
typealias ParamName = String

data class OperationContract(
   val returnType: Type,
   val returnTypeConstraints: List<Constraint>
) : ConstraintTarget {
   override val description: String = "Operation returning ${returnType.qualifiedName}"
   override val constraints: List<Constraint> = returnTypeConstraints
}
