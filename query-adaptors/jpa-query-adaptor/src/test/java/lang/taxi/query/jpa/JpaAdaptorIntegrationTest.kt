package lang.taxi.query.jpa

import com.winterbe.expekt.should
import lang.taxi.annotations.DataType
import lang.taxi.types.QualifiedName
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.junit4.SpringRunner
import java.time.Instant
import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.Id

@DataJpaTest
@RunWith(SpringRunner::class)
class JpaAdaptorIntegrationTest {

   @Autowired
   lateinit var personRepository: PersonRepository

   @Autowired
   lateinit var queryAdaptor: JpaQueryAdaptor

   @Test
   fun `fetches managed taxi types from entity manager`() {
      queryAdaptor.managedTaxiTypes.should.have.size(1)
      queryAdaptor.managedTaxiTypes.keys.should.contain(QualifiedName.from("demo.Person"))
   }

   @Test
   fun `can submit a TaxiQL through to JPA`() {
      personRepository.saveAll(
         listOf(
            Person("1", "Jimmy", "Schmitt", LocalDate.parse("1989-10-05"), Instant.parse("2021-03-20T08:30:00Z")),
            Person("2", "Pete", "Schmitt", LocalDate.parse("1989-10-05"), Instant.parse("2021-01-01T08:30:00Z"))
         )
      )

      queryAdaptor.execute("findAll { Person[] }")
         .should.have.size(2)
   }

   @Test
   fun `can query on a date range`() {
      personRepository.saveAll(
         listOf(
            Person("1", "Jimmy", "Schmitt", LocalDate.parse("1989-10-05"), Instant.parse("2021-03-20T08:30:00Z")),
            Person("2", "Pete", "Schmitt", LocalDate.parse("1950-01-05"), Instant.parse("2021-01-01T08:30:00Z"))
         )
      )

      queryAdaptor.execute("findAll { Person[]( DateOfBirth > '1980-01-01', DateOfBirth < '1990-01-01') }")
         .should.have.size(1)
   }
   @Test
   fun `can query on an instant range`() {
      personRepository.saveAll(
         listOf(
            Person("1", "Jimmy", "Schmitt", LocalDate.parse("1989-10-05"), Instant.parse("2021-03-20T08:30:00Z")),
            Person("2", "Pete", "Schmitt", LocalDate.parse("1989-10-05"), Instant.parse("2021-01-01T08:30:00Z"))
         )
      )

      queryAdaptor.execute("findAll { Person[]( PersonLastUpdated >= '2021-03-01T00:00:00', PersonLastUpdated <= '2021-04-01T00:00:00') }")
         .should.have.size(1)
   }
}

interface PersonRepository : JpaRepository<Person, String>

@Entity
@DataType("demo.Person")
data class Person(
   @Id @field:DataType("demo.PersonId")
   val personId: String,
   @field:DataType("demo.FirstName")
   val firstName: String,
   @field:DataType("demo.LastName")
   val lastName: String,
   @field:DataType("demo.DateOfBirth")
   val birthDate: LocalDate,
   @field:DataType("demo.PersonLastUpdated")
   val lastUpdated: Instant,
   // Add a random field to prove that not every field needs an annotation.
   val favouriteFood:String = "Pizza"
)

// This type intentionally does not have a DataType annotation,
// so should be present in the entity manager, but not be selected
// as a managed type by our adaptor
@Entity
data class Actor(
   @Id
   val id: String
)

@SpringBootConfiguration
@EnableJpaRepositories
@EntityScan
@Import(JpaQueryAdaptor::class)
class TestConfig {

}
