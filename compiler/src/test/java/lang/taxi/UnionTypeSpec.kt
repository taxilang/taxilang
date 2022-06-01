package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.types.JoinType
import lang.taxi.types.UnionType

class UnionTypeSpec : DescribeSpec({

   it("is possible to declare union type A | B | C") {
      val type = """
         type A
         type B
         type C
         type All = A | B | C
      """.compiled()
         .type("All")
         .typeExpression().type
         .shouldBeInstanceOf<UnionType>()
      type.types.shouldHaveSize(3)
      type.types.map { it.qualifiedName }
         .shouldContainInOrder("A", "B", "C")
   }

   it("is possible to declare join on rhs of union type") {
      val unionType = """
         type A
         type B
         type C
         type All = A | B joinTo ( C )
      """.compiled()
         .type("All")
         .typeExpression().type
         .shouldBeInstanceOf<UnionType>()
      unionType.types.shouldHaveSize(2)
      unionType.types[0].qualifiedName.shouldBe("A")
      val joinType = unionType.types[1].shouldBeInstanceOf<JoinType>()
      joinType.leftType.qualifiedName.shouldBe("B")
      joinType.rightTypes.map { it.qualifiedName }.shouldContainExactly("C")
   }
   it("is possible to declare a union on the rhs of a join type") {
      val unionType = """
         type A
         type B
         type C
         type All = A joinTo ( B ) | C
      """.compiled()
         .type("All")
         .typeExpression().type
         .shouldBeInstanceOf<UnionType>()
      unionType.types.shouldHaveSize(2)
      val joinType = unionType.types[0].shouldBeInstanceOf<JoinType>()
      joinType.leftType.qualifiedName.shouldBe("A")
      joinType.rightTypes.map { it.qualifiedName }.shouldContainExactly("B")

      unionType.types[1].qualifiedName.shouldBe("C")
   }

   it("is possible to declare a union type inside a join type" ) {
      val joinType = """
         type A
         type B
         type C
         type All = A joinTo ( B | C )
      """.compiled()
         .type("All")
         .typeExpression().type
         .shouldBeInstanceOf<JoinType>()
      joinType.leftType.qualifiedName.shouldBe("A")
      joinType.rightTypes.shouldHaveSize(1)
      val unionType = joinType.rightTypes.single().shouldBeInstanceOf<UnionType>()
      unionType.types.map { it.qualifiedName }
         .shouldContainInOrder("B", "C")
   }


})
