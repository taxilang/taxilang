package lang.taxi.query.jpa

import com.winterbe.expekt.should
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.generators.java.TaxiGenerator
import lang.taxi.query.TaxiQlQuery
import org.junit.jupiter.api.Test

class JpqlQueryBuilderTest {

   @Test
   fun `simple find all on type`() {
      val (taxi, query) = schemaForClasses(Person::class.java)
         .query("findAll { Person[] }")
      val jpql = JpqlQueryBuilder().convert(taxi, query.first(), JpaQueryType.from(query.first(), listOf(Person::class.java)))
      jpql.withoutWhitespace().should.equal("select t0 from Person t0".withoutWhitespace())

   }
   @Test
   fun `find in date range`() {
      val (taxi, query) = schemaForClasses(Person::class.java)
         .query("findAll { Person[]( DateOfBirth > '1980-01-01', DateOfBirth < '1990-01-01') }")
      val jpql = JpqlQueryBuilder().convert(taxi, query.first(), JpaQueryType.from(query.first(), listOf(Person::class.java)))
      jpql.withoutWhitespace().should.equal("""select t0 from
Person t0
WHERE t0.birthDate > '1980-01-01' AND t0.birthDate < '1990-01-01'""".withoutWhitespace())

   }
}

fun schemaForClasses(vararg types: Class<*>): TaxiDocument {
   return Compiler.forStrings(TaxiGenerator().forClasses(*types).generateAsStrings())
      .compile()
}

fun TaxiDocument.query(query: String): Pair<TaxiDocument, List<TaxiQlQuery>> {
   return this to Compiler(query, importSources = listOf(this))
      .queries()
}
