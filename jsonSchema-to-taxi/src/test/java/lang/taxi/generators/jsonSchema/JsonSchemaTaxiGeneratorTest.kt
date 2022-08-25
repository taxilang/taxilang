package lang.taxi.generators.jsonSchema

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.testing.TestHelpers
import org.everit.json.schema.loader.SchemaLoader
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URL

class JsonSchemaTaxiGeneratorTest {

   @Test
   fun `parses uri with fragment to type name`() {
      val uri =
         URI.create("https://api.demoStack.com/VYNE/apps/v1/docs/schemas/reference.json#/definitions/holiday/properties/type")
      val typeName = JsonSchemaTypeMapper.getTypeName(uri, Logger())
      typeName.fullyQualifiedName.should.equal("com.demoStack.api.reference.definitions.holiday.properties.Type")
   }

   @Test
   fun `parses document style uri to type name`() {
      val uri = URI.create("https://api.demoStack.com/VYNE/apps/v1/docs/schemas/index-edge-api-doc.json")
      val typeName = JsonSchemaTypeMapper.getTypeName(uri, Logger())
      typeName.fullyQualifiedName.should.equal("com.demoStack.api.IndexEdgeApiDoc")
   }

   @Test
   fun `can parse a jsonSchema definition`() {
      val schemaJson = Resources.getResource("samples/simple-json-schema.json")
      val generated = TaxiGenerator().generateAsStrings(schemaJson)
      generated.shouldCompileTheSameAs(
         """
namespace net.pwall {
   model Product {
      price : Price
      name : Name
      id : Id
      stock : Stock?
      tags : Tags[]?
      someTime : SomeTime?
      someDate : SomeDate?
      someDateTime : SomeDateTime?
   }
   type SomeDate inherits Date
   type SomeTime inherits Time
   type SomeDateTime inherits Instant

   type Price inherits Decimal

   [[ Name of the product ]]
   type Name inherits String

   [[ Product identifier ]]
   type Id inherits Decimal

   model Stock {
      id: StockId
      warehouse : Warehouse?
      retail : Retail?
   }

   type StockId inherits Decimal

   type Warehouse inherits Decimal

   type Retail inherits Decimal

   type Tags inherits String
}

         """.trimIndent()
      )
   }

   @Test
   fun `can parse a typed array`() {
      val schemaJson = Resources.getResource("samples/typed-array.json")
      val generated =
         TaxiGenerator(schemaLoader = SchemaLoader.builder().draftV6Support()).generateAsStrings(schemaJson)
      generated
         .shouldCompileTheSameAs(
            """namespace com.example {
   [[ A representation of a person, company, organization, or place ]]
   model Arrays {
      fruits : Fruits[]?
      vegetables : Vegetables[]?
   }

   type Fruits inherits String

   model Vegetables {
      veggieName : VeggieName
      veggieLike : VeggieLike
   }

   [[ The name of the vegetable. ]]
   type VeggieName inherits String

   [[ Do I like this vegetable? ]]
   type VeggieLike inherits Boolean
}"""
         )
   }


   @Test
   fun `can load from url`() {
      val url = URL("https://uatapi.ultumus.com/VYNE/apps/v1/docs/schemas/index-edge-api-doc.json")
      val generated = TaxiGenerator(
         schemaLoader = SchemaLoader
            .builder()
            .resolutionScope("https://uatapi.ultumus.com/VYNE/apps/v1/docs/schemas/")
      )
         .generateAsStrings(url)
      val compiled = TestHelpers.compile(generated.taxi)
      compiled.type("com.ultumus.uatapi.PricingAnalytics")
   }

   @Test
   fun `when multiple properties with the same name exist in different places in the same document`() {
      val testJsonSchema =
         """
            {
               "${"$"}schema": "http://json-schema.org/draft-07/schema",
               "${"$"}id": "http://taxilang.org/test",
               "title": "Test",
               "type": "object",
               "properties": {
                  "id": {
                     "type": "string",
                     "description": "Identifier"
                  },
                  "item": {
                     "type": "object",
                     "required": ["id"],
                     "properties": {
                        "id": {
                           "type": "string"
                        }
                     }
                  }
               }
            }
         """.trimIndent()
      val generated = TaxiGenerator().generateAsStrings(testJsonSchema)
      generated.shouldCompileTheSameAs(
         """
            namespace org.taxilang {
               model Test {
                  item : Item?
                  id : TestId?
               }

               model Item {
                  id : Id
               }

               type Id inherits String

               [[ Identifier ]]
               type TestId inherits String
            }
         """.trimIndent()
      )
   }

   @Test
   fun `when multiple properties with the same name exist as sibling elements in the schema`() {
      val testJsonSchema =
         """
            {
               "${"$"}schema": "http://json-schema.org/draft-07/schema",
               "${"$"}id": "http://taxilang.org/test",
               "title": "Test",
               "type": "object",
               "properties": {
                  "name": {
                     "type": "string",
                     "description": "Identifier"
                  },
                  "Name": {
                     "type": "string",
                     "description": "Identifier"
                  },
                  "NAME": {
                     "type": "string",
                     "description": "Identifier"
                  },
               }
            }
         """.trimIndent()
      val generated = TaxiGenerator().generateAsStrings(testJsonSchema)
      generated.shouldCompileTheSameAs(
         """
            namespace org.taxilang {
               model Test {
                  name : Name?
                  Name : TestName?
                  NAME : TestNAME1?
               }

               type Name inherits String

               type TestName inherits String

               type TestNAME1 inherits String
            }
         """.trimIndent()
      )
   }
}


fun GeneratedTaxiCode.shouldCompileTheSameAs(expected: String): TaxiDocument {
   return TestHelpers.expectToCompileTheSame(this.taxi, expected)
}
