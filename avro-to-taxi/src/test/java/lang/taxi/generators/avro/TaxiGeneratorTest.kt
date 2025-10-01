package lang.taxi.generators.avro

import com.google.common.io.Resources
import com.winterbe.expekt.should
import io.kotest.matchers.shouldBe
import lang.taxi.TaxiDocument
import lang.taxi.testing.TestHelpers
import lang.taxi.testing.shouldCompileTheSameAs
import lang.taxi.withoutWhitespace
import org.junit.jupiter.api.Test
import kotlin.io.path.toPath

class TaxiGeneratorTest {

   @Test
   fun `generates taxi schema`() {
      val avroFile = Resources.getResource("addressBook.avsc")
         .toURI().toPath()
      val generated = TaxiGenerator()
         .generate(avroFile)
val concatenatedSource = generated.concatenatedSource.removeEmptyLines()
   concatenatedSource.shouldBe("""
namespace simple {
   [[ A full addressbook of people ]]
   @lang.taxi.formats.AvroMessage
   closed model AddressBook {
      @lang.taxi.formats.AvroField(ordinal = 0) people : simple.addressbook.People[]
   }
}
namespace simple.addressbook {
   @lang.taxi.formats.AvroMessage
   closed model People {
      @lang.taxi.formats.AvroField(ordinal = 0) name : simple.addressbook.people.Name
      @lang.taxi.formats.AvroField(ordinal = 1) id : simple.addressbook.people.PeopleId
      @lang.taxi.formats.AvroField(ordinal = 2) email : simple.addressbook.people.Email?
      @lang.taxi.formats.AvroField(ordinal = 3) phones : simple.addressbook.people.Phones[]
      @lang.taxi.formats.AvroField(ordinal = 4) last_updated : simple.addressbook.people.LastUpdated
   }
}
namespace simple.addressbook.people {
   type Name inherits String
   type PeopleId inherits Int
   type Email inherits String

   @lang.taxi.formats.AvroMessage
   closed model Phones {
      @lang.taxi.formats.AvroField(ordinal = 0) number : simple.addressbook.people.phones.Number
      @lang.taxi.formats.AvroField(ordinal = 1) `type` : simple.addressbook.people.phones.Type
   }
   type LastUpdated inherits Long
}
namespace simple.addressbook.people.phones {
   type Number inherits String

   enum Type {
      MOBILE(0),
      HOME(1),
      WORK(2)
   }
}""".trimIndent().removeEmptyLines()
         )
   }


   @Test
   fun `source map contains a reference to the generated source`() {
      val avroFile = Resources.getResource("addressBookWithTaxiAnnotations.avsc")
         .toURI().toPath()
      val generated = TaxiGenerator()
         .generate(avroFile)

      generated.sourceMap.containsType("foo.AddressBook")
         .should.be.`true`
   }

   @Test
   fun `generates taxi schema using taxi type names`() {
      val avroFile = Resources.getResource("addressBookWithTaxiAnnotations.avsc")
         .toURI().toPath()
      val generated = TaxiGenerator()
         .generate(avroFile)

      val concatenatedSource = generated.concatenatedSource

      // ensure types were declared / not declared as expected
      // As a result, the source can't compile (yet), so we have to do string searches
      concatenatedSource.should.contain("type EmailAddress inherits String")
      concatenatedSource.should.not.contain("type PersonId")
      concatenatedSource.should.not.contain("type PersonName")

      val schemaWithBaseSchema = """
         $concatenatedSource
         namespace foo {
            type PersonId inherits Int
            type PersonName inherits String

         }
      """.trimIndent()


      val expected = """namespace foo {
   @lang.taxi.formats.AvroMessage
   closed model AddressBook {
      @lang.taxi.formats.AvroField(ordinal = 0) people : foo.addressbook.People[]
   }

   type PersonName inherits String
   type PersonId inherits Int
   type EmailAddress inherits String

   enum PhoneTypeEnum {
      MOBILE(0),
      HOME(1),
      WORK(2)
   }
}
namespace foo.addressbook {
   @lang.taxi.formats.AvroMessage
   closed model People {
      @lang.taxi.formats.AvroField(ordinal = 0) name : foo.PersonName
      @lang.taxi.formats.AvroField(ordinal = 1) id : foo.PersonId
      @lang.taxi.formats.AvroField(ordinal = 2) email : foo.EmailAddress
      @lang.taxi.formats.AvroField(ordinal = 3) phones : foo.addressbook.people.Phones[]
      @lang.taxi.formats.AvroField(ordinal = 4) last_updated : foo.addressbook.people.LastUpdated
   }
}
namespace foo.addressbook.people {
   @lang.taxi.formats.AvroMessage
   closed model Phones {
      @lang.taxi.formats.AvroField(ordinal = 0) number : foo.addressbook.people.phones.Number
      @lang.taxi.formats.AvroField(ordinal = 1) `type` : foo.PhoneTypeEnum
   }
   type LastUpdated inherits Long
}
namespace foo.addressbook.people.phones {
   type Number inherits String
}"""
      schemaWithBaseSchema.shouldCompileTheSameAs(expected)
   }

   @Test
   fun `generates expected schema when array is root object`() {
      val avroFile = Resources.getResource("addressBookArray.avsc")
         .toURI().toPath()
      val generated = TaxiGenerator()
         .generate(avroFile)

   }

   @Test
   fun `when taxi type is defined on model then can control type declaration`() {
      val avroFile = Resources.getResource("film.avsc").toURI().toPath()
      val generated = TaxiGenerator()
         .generate(avroFile)

      val externalSchema = """
         namespace com.example {
            model LeadActor {
               name: String
               age: Int
            }
            model Actor {
               name : String
               age : Int
            }
         }
      """.trimIndent()
      val concatenatedSource = """
         $externalSchema
         ${generated.concatenatedSource}
      """.trimIndent()
      val expected = """
$externalSchema
namespace com.example.media {
   @lang.taxi.formats.AvroMessage
   closed model Film {
      @lang.taxi.formats.AvroField(ordinal = 0) title : com.example.media.film.Title
      @lang.taxi.formats.AvroField(ordinal = 1) director : com.example.media.film.Director
      @lang.taxi.formats.AvroField(ordinal = 2) producer : com.example.media.film.Producer
      @lang.taxi.formats.AvroField(ordinal = 3) leadActor : com.example.LeadActor
      @lang.taxi.formats.AvroField(ordinal = 4) cast : com.example.Actor[]?
      @lang.taxi.formats.AvroField(ordinal = 5) crew : com.example.StageHand[]?
   }
}
namespace com.example.media.film {
   type Title inherits String

   @lang.taxi.formats.AvroMessage
   closed model Director {
      @lang.taxi.formats.AvroField(ordinal = 0) name : com.example.media.film.director.Name
      @lang.taxi.formats.AvroField(ordinal = 1) age : com.example.media.film.director.Age
   }

   @lang.taxi.formats.AvroMessage
   closed model Producer {
      @lang.taxi.formats.AvroField(ordinal = 0) name : com.example.media.film.producer.Name
      @lang.taxi.formats.AvroField(ordinal = 1) age : com.example.media.film.producer.Age
   }
}
namespace com.example.media.film.director {
   type Name inherits String
   type Age inherits Int
}
namespace com.example.media.film.producer {
   type Name inherits String
   type Age inherits Int
}
namespace com.example {
   @lang.taxi.formats.AvroMessage
   closed model StageHand {
      @lang.taxi.formats.AvroField(ordinal = 0) name : com.example.stagehand.Name
      @lang.taxi.formats.AvroField(ordinal = 1) age : com.example.stagehand.Age
   }
}
namespace com.example.stagehand {
   type Name inherits String
   type Age inherits Int
}
      """.trimIndent()
      concatenatedSource.shouldCompileTheSameAs(expected)
   }
}


fun List<String>.shouldCompileTheSameAsListOf(expected: List<String>): TaxiDocument {
   return TestHelpers.expectToCompileTheSame(this, expected)
}

private fun String.removeEmptyLines():String {
   return this.lines()
      .filter { it.isNotBlank() }
      .joinToString("\n")
}
