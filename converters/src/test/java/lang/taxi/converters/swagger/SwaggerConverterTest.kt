package lang.taxi.converters.swagger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.winterbe.expekt.expect
import org.junit.Before
import org.junit.Test
import java.io.InputStream

class SwaggerConverterTest {
   lateinit var converter:SwaggerConverter

   @Before
   fun setup() {
      converter = SwaggerConverter()
   }

   @Test
   fun producesTaxiFileFromSwaggerJson() {
      val api = apiJson()
      val taxi = SwaggerConverter().toTaxiTypes(api)
   }

   @Test
   fun producesTypeDefForString() {
      val json = """{
"name" : {
   "type" : "string"
}}
""".fromJson()
      expect(converter.convertProperties(json).first()).to.equal("name : String")
   }

   @Test
   fun producesFieldForLocalDate() {
      val json = """{
"name" : {
   "type" : "string",
   "format": "date",
   "example": "YYYY-MM-DD"
}}
""".fromJson()
      expect(converter.convertProperties(json).first()).to.equal("name : LocalDate")
   }

   @Test
   fun producesEnumType() {
      val json = """{
"gender" : { "type" : "string", "enum" : ["Male","Female"]}
}""".fromJson()
      expect(converter.convertProperties(json).first()).to.equal("gender : Gender")
      expect(converter.additionalTypes["Gender"]).to.be.not.`null`
      val expectedEnum = """enum Gender {
   Male
   Female
}"""
      expect(converter.additionalTypes["Gender"]).to.equal(expectedEnum)
   }

   @Test
   fun producesTypeFromSwagger() {
   val json = """
{
  "Person": {
      "type" : "object",
      "properties" : {
        "name" : { "type" : "string" },
        "age" : { "type" : "int32"}
      }
  }
}"""
      val type = converter.mapDefinitionsToTypes(json.fromJson())
      val expectedType = """type Person {
   name : String
   age : Int
}"""
      expect(type).to.equal(expectedType)
   }


   fun apiJson(): InputStream {
      return this.javaClass.classLoader.getResourceAsStream("api.json")!!
   }
}


fun String.fromJson(): Map<String, Any> {
   return jacksonObjectMapper().readValue(this, Map::class.java) as Map<String, Any>
}
