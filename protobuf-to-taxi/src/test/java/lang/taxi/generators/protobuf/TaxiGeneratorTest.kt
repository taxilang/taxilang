package lang.taxi.generators.protobuf

import com.squareup.wire.schema.RepoBuilder
import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.testing.TestHelpers
import okio.FileSystem
import org.junit.jupiter.api.Test

class TaxiGeneratorTest {
   @Test
   fun `can convert simple schema`() {
      val generator = TaxiGenerator(fileSystem = FileSystem.RESOURCES)
      generator.addSchemaRoot("/simple/src/proto")
      val generated = generator.generate(packagesToInclude = listOf("simple"))
      generated.shouldCompileTheSameAs(
         """
   namespace simple {
   [[ [START messages] ]]
   @lang.taxi.formats.ProtobufMessage(packageName = "simple" , messageName = "Person")
   model Person {
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") name : simple.person.Name?
      [[ Unique ID number for this person. ]]
      @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "int32") id : simple.person.PersonId?
      @lang.taxi.formats.ProtobufField(tag = 3 , protoType = "string") email : simple.person.Email?
      @lang.taxi.formats.ProtobufField(tag = 4 , protoType = "simple.Person.PhoneNumber") phones : simple.Person.PhoneNumber[]?
      @lang.taxi.formats.ProtobufField(tag = 5 , protoType = "google.protobuf.Timestamp") last_updated : Instant?
   }

   [[ Our address book file is just one of these. ]]
   @lang.taxi.formats.ProtobufMessage(packageName = "simple" , messageName = "AddressBook")
   model AddressBook {
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "simple.Person") people : Person[]?
   }
}
namespace simple.person {
   type Name inherits String
   type PersonId inherits Int
   type Email inherits String
}
namespace simple.Person {
   @lang.taxi.formats.ProtobufMessage(packageName = "simple.Person" , messageName = "PhoneNumber")
   model PhoneNumber {
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") number : simple.person.phonenumber.Number?
      @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "simple.Person.PhoneType") `type` : PhoneType?
   }

   @lang.taxi.formats.ProtobufMessage
   enum PhoneType {
      MOBILE(0),
      HOME(1),
      WORK(2)
   }
}
namespace simple.person.phonenumber {
   type Number inherits String
}"""
      )
   }

   @Test
   fun `when protobuf includes type hints then correct types are used`() {
      val protoSchema = RepoBuilder()
         .add(
            "org/taxilang/dataType.proto", """
            import "google/protobuf/descriptor.proto";

            package taxi;

            extend google.protobuf.FieldOptions {
              optional string dataType = 50002;
            }
         """.trimIndent()
         )
         .add(
            "coffee.proto",
            """
          |import "org/taxilang/dataType.proto";
          |
          |message CafeDrink {
          |  optional string customer_name = 1 [(taxi.dataType)="foo.CustomerName"];
          |  optional int32 customer_id = 2 [(taxi.dataType)="foo.CustomerId"];
          |
          |  enum Foam {
          |    NOT_FOAMY_AND_QUITE_BORING = 1;
          |    ZOMG_SO_FOAMY = 3;
          |  }
          |}
          |
          """.trimMargin()
         )
         .schema()

      val generated = TaxiGenerator()
         .generate(protobufSchema = protoSchema)
      val existingSchema = """namespace foo
            |type CustomerName inherits String
            |type CustomerId inherits Int
            |
         """.trimMargin()
      generated
         .with(existingSchema)
         .shouldCompileTheSameAs(
            listOf(
               existingSchema,
               """@lang.taxi.formats.ProtobufMessage(packageName = "" , messageName = "CafeDrink")
model CafeDrink {
   @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") customer_name : foo.CustomerName?
   @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "int32") customer_id : foo.CustomerId?
}
""",
               """namespace CafeDrink {
   @lang.taxi.formats.ProtobufMessage enum Foam {
      NOT_FOAMY_AND_QUITE_BORING(1),
      ZOMG_SO_FOAMY(3)
   }
}"""
            )
         )
   }

   @Test
   fun `generates with nested enum type`() {
      val coffeeSchema = RepoBuilder()
         .add(
            "coffee.proto",
            """
          |package cafe;
          |
          |message CafeDrink {
          |  optional string customer_name = 1;
          |  optional Foam foam = 3;
          |
          |  enum Foam {
          |    NOT_FOAMY_AND_QUITE_BORING = 1;
          |    ZOMG_SO_FOAMY = 3;
          |  }
          |}
          |
          """.trimMargin()
         )
         .schema()

      val generated = TaxiGenerator()
         .generate(protobufSchema = coffeeSchema)
      generated.shouldCompileTheSameAs(
         listOf(
            """
namespace cafe {
   @lang.taxi.formats.ProtobufMessage(packageName = "cafe" , messageName = "CafeDrink")
   model CafeDrink {
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") customer_name : cafe.cafedrink.CustomerName?
      @lang.taxi.formats.ProtobufField(tag = 3 , protoType = "cafe.CafeDrink.Foam") foam : cafe.CafeDrink.Foam?
   }
}
namespace cafe.cafedrink {
   type CustomerName inherits String
}
namespace cafe.CafeDrink {
   @lang.taxi.formats.ProtobufMessage enum Foam {
      NOT_FOAMY_AND_QUITE_BORING(1),
      ZOMG_SO_FOAMY(3)
   }
}
      """
         )
      )
   }

   @Test
   fun `can translate more complex protobuf`() {
      val generator = TaxiGenerator(fileSystem = FileSystem.RESOURCES)
      generator.addSchemaRoot("/person/src/proto")
      val generated = generator.generate(packagesToInclude = listOf("people"))
      generated.shouldCompileTheSameAs("""
namespace people {
   [[ Example of an enum definition ]]
   @lang.taxi.formats.ProtobufMessage enum Status {
      UNKNOWN(0),
      ACTIVE(1),
      INACTIVE(2),
      SUSPENDED(3)
   }

   [[ Main message definition ]]
   @lang.taxi.formats.ProtobufMessage(packageName = "people" , messageName = "Person")
   model Person {
      [[ unique identifier ]]
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") id : people.person.PersonId?
      [[ person's name ]]
      @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "string") name : people.person.Name?
      [[ person's age ]]
      @lang.taxi.formats.ProtobufField(tag = 3 , protoType = "int32") age : people.person.Age?
      [[ list of emails ]]
      @lang.taxi.formats.ProtobufField(tag = 4 , protoType = "string") emails : people.person.Emails[]?
      [[ enum field ]]
      @lang.taxi.formats.ProtobufField(tag = 5 , protoType = "people.Status") status : Status?
      [[ nested message ]]
      @lang.taxi.formats.ProtobufField(tag = 6 , protoType = "people.Address") address : Address?
      [[ phone number ]]
      @lang.taxi.formats.ProtobufField(tag = 7 , protoType = "string") phone_number : people.person.PhoneNumber?
      [[ email address ]]
      @lang.taxi.formats.ProtobufField(tag = 8 , protoType = "string") email_address : people.person.EmailAddress?
   }

   [[ Nested message definition ]]
   @lang.taxi.formats.ProtobufMessage(packageName = "people" , messageName = "Address")
   model Address {
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") street : people.address.Street?
      @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "string") city : people.address.City?
      @lang.taxi.formats.ProtobufField(tag = 3 , protoType = "string") state : people.address.State?
      @lang.taxi.formats.ProtobufField(tag = 4 , protoType = "string") zip_code : people.address.ZipCode?
   }

   [[ Message with a map field ]]
   @lang.taxi.formats.ProtobufMessage(packageName = "people" , messageName = "PhoneBook")
   model PhoneBook {
      [[ map of people with unique id as key ]]
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "map<string, Person>") people : Map<people.phonebook.people.MapKey,people.Person>?
   }

   [[ Message for event data with separate fields for different event types ]]
   @lang.taxi.formats.ProtobufMessage(packageName = "people" , messageName = "Event")
   model Event {
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") id : people.event.EventId?
      @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "people.Birthday") birthday : Birthday?
      @lang.taxi.formats.ProtobufField(tag = 3 , protoType = "people.Meeting") meeting : Meeting?
   }

   [[ Nested message used in Event for Birthday ]]
   @lang.taxi.formats.ProtobufMessage(packageName = "people" , messageName = "Birthday")
   model Birthday {
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") person_id : people.birthday.PersonId?
      @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "string") date : people.birthday.Date?
   }

   [[ Nested message used in Event for Meeting ]]
   @lang.taxi.formats.ProtobufMessage(packageName = "people" , messageName = "Meeting")
   model Meeting {
      @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") title : people.meeting.Title?
      @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "string") location : people.meeting.Location?
      @lang.taxi.formats.ProtobufField(tag = 3 , protoType = "string") attendees : people.meeting.Attendees[]?
   }
}
namespace people.person {
   type PersonId inherits String
   type Name inherits String
   type Age inherits Int
   type Emails inherits String
   type PhoneNumber inherits String
   type EmailAddress inherits String
}
namespace people.address {
   type Street inherits String
   type City inherits String
   type State inherits String
   type ZipCode inherits String
}
namespace people.phonebook.people {
   type MapKey inherits String
}
namespace people.event {
   type EventId inherits String
}
namespace people.birthday {
   type PersonId inherits String
   type Date inherits String
}
namespace people.meeting {
   type Title inherits String
   type Location inherits String
   type Attendees inherits String
}
      """.trimIndent())
   }
}


fun GeneratedTaxiCode.shouldCompileTheSameAs(expected: String): TaxiDocument {
   return TestHelpers.expectToCompileTheSame(this.taxi, expected)
}

fun GeneratedTaxiCode.shouldCompileTheSameAs(expected: List<String>): TaxiDocument {
   return TestHelpers.expectToCompileTheSame(this.taxi, expected)
}

fun GeneratedTaxiCode.with(additionalSchema: String): GeneratedTaxiCode {
   return this.copy(this.taxi + additionalSchema, this.messages)
}
