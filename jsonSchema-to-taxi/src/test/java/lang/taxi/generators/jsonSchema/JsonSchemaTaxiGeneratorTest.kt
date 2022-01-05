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
      tags : TagsMember[]?
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
      warehouse : Warehouse?
      retail : Retail?
   }

   type Warehouse inherits Decimal

   type Retail inherits Decimal

   type TagsMember inherits String
}

         """.trimIndent()
      )
   }

   @Test
   fun `can parse a typed array`() {
      val schemaJson = Resources.getResource("samples/typed-array.json")
      TaxiGenerator(schemaLoader = SchemaLoader.builder().draftV6Support()).generateAsStrings(schemaJson)
         .shouldCompileTheSameAs(
            """namespace com.example {
   [[ A representation of a person, company, organization, or place ]]
   model Arrays {
      fruits : FruitsMember[]?
      vegetables : VegetablesMember[]?
   }

   type FruitsMember inherits String

   model VegetablesMember {
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
//      val url = URL("https://api.ultumus.com/VYNE/apps/v1/docs/schemas/index-edge-api-doc.json")
//      val generated = TaxiGenerator(
//         schemaLoader = SchemaLoader
//            .builder()
//            .resolutionScope("https://api.ultumus.com/VYNE/apps/v1/docs/schemas/")
//      )
//         .generateAsStrings(url)
//      TestHelpers.compile(generated.taxi)
//   }
}


fun GeneratedTaxiCode.shouldCompileTheSameAs(expected: String): TaxiDocument {
   return TestHelpers.expectToCompileTheSame(this.taxi, expected)
}
