package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import lang.taxi.types.ObjectType

class TaxiQlExpressionsSpec : DescribeSpec({
   describe("expressions constraints on types in queries") {
      val src = """
               model Person {
                  name : PersonName inherits String
               }
               model Pet {
                  name : PetName inherits String
               }
            """

      it("is possible to define a field with an expression") {
         val schemaSrc = src + """declare function buyPet():Pet"""
         val querySrc = """find { Person } as {
               | name : PersonName
               | pet : buyPet()
               |}
            """.trimMargin()
         val (schema, query) = (schemaSrc).compiledWithQuery(querySrc)
         query.projectedObjectType.field("pet").type.qualifiedName.should.equal("Pet")
      }
      it("is possible to declare an expression on a discovery type") {
         val (schema, query) = src.compiledWithQuery("find { Person(PersonName == 'Jimmy') }")
         query.typesToFind.single().constraints.shouldHaveSize(1)
      }
      it("is possible to declare an expression on a field of an anonymous projection") {
         val (schema, query) = src.compiledWithQuery(
            """find { Person } as {
            |  name: PersonName
            |  spouse: Person = 1 + 2
            |}
         """.trimMargin()
         )
         query.projectedType!!.asA<ObjectType>()
            .field("spouse").type
      }
      it("is possible to declare an expression on a projected return type") {
         val (schema, query) = src.compiledWithQuery("find { Person(true == true) }")
      }
   }
})
