package lang.taxi.accessors

import lang.taxi.expressions.Expression
import lang.taxi.types.CompilationUnit
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type

/**
 * Accessor that instructs a field should be constructed by iterating an array.
 *
 * findAll { OrderTransaction[] } as {
 *   items: Thing[] by [OrderItem[]] // Create a Thing[] by iterating OrderItem[]
 * }[]
 *
 * Callers can also define an additional scope to use for projecting
 *
 *  findAll { OrderTransaction[] } as {
 *  // Create a Thing[] by iterating OrderItem[].  Also expose CustomerName (discovered from somewhere)
 *   items: Thing[] by [OrderItem[] with { CustomerName }]
 * }[]
 *
 */
data class CollectionProjectionExpressionAccessor(val type: Type,
                                                  val projectionScope:ProjectionScopeDefinition?,
                                                  override val compilationUnits: List<CompilationUnit>) : Expression(), TaxiStatementGenerator {
   override val returnType: Type = type
   override fun asTaxi(): String {
      return "[${type.toQualifiedName().parameterizedName}]"
   }

   override val allReferencedTypes: Set<Type>
      get() {
         return (listOf(this.returnType) + (projectionScope?.accessors?.flatMap { it.allReferencedTypes }
            ?: emptyList())).toSet()
      }
}

/**
 * The scope passed into a projection statement
 * eg: in the below:
 *
 *  findAll { OrderTransaction[] } as {
 *   items: Thing[] by [OrderItem[] with { CustomerName }]
 * }[]
 *
 * this class contains the CusotmerName accessor
 */
data class ProjectionScopeDefinition(val accessors:List<Accessor>) : TaxiStatementGenerator  {
   override fun asTaxi(): String {
      val accessorTaxi = accessors.joinToString(",") { accessor ->
         when (accessor) {
            is TaxiStatementGenerator -> accessor.asTaxi()
            else -> "/* Accessor type ${accessor::class.simpleName} does not support taxi generation yet */"
         }
      }
      return "with scope { $accessorTaxi }"
   }
}
