package lang.taxi.services

import lang.taxi.ImmutableEquality
import lang.taxi.Operator
import lang.taxi.types.*
import lang.taxi.types.Annotation

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
