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
         """namespace net.pwall {
   [[ This model has been generated.  The original source is shown below.
   ```json
   {"type":"object","title":"Product","$id":"http://pwall.net/test","$schema":"http://json-schema.org/draft-07/schema","required":["id","name","price"],"properties":{"someTime":{"type":"string","format":"time"},"price":{"type":"number","minimum":0},"someDate":{"type":"string","format":"date"},"name":{"type":"string","description":"Name of the product"},"id":{"type":"number","description":"Product identifier"},"stock":{"type":"object","properties":{"warehouse":{"type":"number"},"retail":{"type":"number"}}},"someDateTime":{"type":"string","format":"date-time"},"tags":{"type":"array","items":{"type":"string"}}}}
   ``` ]]
   model Product {
      someTime : SomeTime?
      price : Price
      someDate : SomeDate?
      name : Name
      id : Id
      stock : Stock?
      someDateTime : SomeDateTime?
      tags : Tags[]?
   }

   type SomeTime inherits Time

   type Price inherits Decimal

   type SomeDate inherits Date

   [[ Name of the product ]]
   type Name inherits String

   [[ Product identifier ]]
   type Id inherits Decimal

   [[ This model has been generated.  The original source is shown below.
   ```json
   {"type":"object","properties":{"warehouse":{"type":"number"},"retail":{"type":"number"}}}
   ``` ]]
   model Stock {
      id: StockId
      warehouse : Warehouse?
      retail : Retail?
   }

   type StockId inherits Decimal

   type Warehouse inherits Decimal

   type Retail inherits Decimal

   type SomeDateTime inherits Instant

   type Tags inherits String
}"""
      )
   }

   // Strings that Kotlin will try and interpolate
   private val defs = "\$defs"
   private val schema = "\$schema"
   private val ref = "\$ref"
   private val id = "\$id"

   @Test
   fun `can parse a typed array`() {
      val schemaJson = Resources.getResource("samples/typed-array.json")
      val generated =
         TaxiGenerator(schemaLoader = SchemaLoader.builder().draftV6Support()).generateAsStrings(schemaJson)
      generated
         .shouldCompileTheSameAs(
            """namespace com.example {
   [[ A representation of a person, company, organization, or place
   This model has been generated.  The original source is shown below.
   ```json
   {"type":"object","description":"A representation of a person, company, organization, or place","id":"https://example.com/arrays.schema.json","$defs"}:{"veggie":{"type":"object","required":["veggieName","veggieLike"],"properties":{"veggieName":{"type":"string","description":"The name of the vegetable."},"veggieLike":{"type":"boolean","description":"Do I like this vegetable?"}}}},"$schema":"https://json-schema.org/draft/2020-12/schema","properties":{"fruits":{"type":"array","items":{"type":"string"}},"vegetables":{"type":"array","items":{"$ref":"#/$defs/veggie"}}}}
   ``` ]]
   model Arrays {
      fruits : Fruits[]?
      vegetables : Vegetables[]?
   }

   type Fruits inherits String

   [[ null
   This model has been generated.  The original source is shown below.
   ```json
   {"type":"object","required":["veggieName","veggieLike"],"properties":{"veggieName":{"type":"string","description":"The name of the vegetable."},"veggieLike":{"description":"Do I like this vegetable?","type":"boolean"}}}
   ``` ]]
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

//   @Test
//   fun `can load from url`() {
//      val url = URL("https://uatapi.ultumus.com/VYNE/apps/v1/docs/schemas/index-edge-api-doc.json")
//      val generated = TaxiGenerator(
//         schemaLoader = SchemaLoader
//            .builder()
//            .resolutionScope("https://uatapi.ultumus.com/VYNE/apps/v1/docs/schemas/")
//      )
//         .generateAsStrings(url)
//      val compiled = TestHelpers.compile(generated.taxi)
//      compiled.type("com.ultumus.uatapi.PricingAnalytics")
//   }

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
                  item : org.taxilang.test.Item?
                  id : org.taxilang.test.Id?
               }
            }

            namespace org.taxilang.test {
               model Item {
                  id : org.taxilang.item.Id
               }

               type Id inherits String
            }

            namespace org.taxilang.item {
               type Id inherits String
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
                  name : org.taxilang.test.Name?
                  Name : org.taxilang.test.Name1?
                  NAME : org.taxilang.test.NAME2?
               }
            }

            namespace org.taxilang.test {
               type Name inherits String
               type Name1 inherits String
               type NAME2 inherits String
            }
         """.trimIndent()
      )
   }
}


fun GeneratedTaxiCode.shouldCompileTheSameAs(expected: String): TaxiDocument {
   return TestHelpers.expectToCompileTheSame(this.taxi, expected)
}
