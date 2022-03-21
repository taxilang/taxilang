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
         @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") name : String?
         [[ Unique ID number for this person. ]]
         @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "int32") id : Int?
         @lang.taxi.formats.ProtobufField(tag = 3 , protoType = "string") email : String?
         @lang.taxi.formats.ProtobufField(tag = 4 , protoType = "simple.Person.PhoneNumber") phones : simple.Person.PhoneNumber[]?
         @lang.taxi.formats.ProtobufField(tag = 5 , protoType = "google.protobuf.Timestamp") last_updated : Instant?
      }

      [[ Our address book file is just one of these. ]]
      @lang.taxi.formats.ProtobufMessage(packageName = "simple" , messageName = "AddressBook")
      model AddressBook {
         @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "simple.Person") people : Person[]?
      }
   }
   namespace simple.Person {
      @lang.taxi.formats.ProtobufMessage(packageName = "simple.Person" , messageName = "PhoneNumber")
      model PhoneNumber {
         @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") number : String?
         @lang.taxi.formats.ProtobufField(tag = 2 , protoType = "simple.Person.PhoneType") `type` : PhoneType?
      }

      @lang.taxi.formats.ProtobufMessage enum PhoneType {
         MOBILE(0),
         HOME(1),
         WORK(2)
      }
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
         @lang.taxi.formats.ProtobufMessage(packageName = "" , messageName = "CafeDrink")
         model CafeDrink {
            @lang.taxi.formats.ProtobufField(tag = 1 , protoType = "string") customer_name : String?
            @lang.taxi.formats.ProtobufField(tag = 3 , protoType = "CafeDrink.Foam") foam : CafeDrink.Foam?
         }""",
            """namespace CafeDrink {
            @lang.taxi.formats.ProtobufMessage
            enum Foam {
               NOT_FOAMY_AND_QUITE_BORING(1),
               ZOMG_SO_FOAMY(3)
            }
}
      """
         )
      )
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
