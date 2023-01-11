package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import lang.taxi.types.MapType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.getUnderlyingMapType
import lang.taxi.types.isMapType

class MapSpec : DescribeSpec({
   describe("map type") {
      it("is a compilation error not tp provide the wrong number of generic arguments") {
         val errors = """
            type MyMap inherits Map<String>
         """.validated()
         errors.shouldContainMessage("Type lang.taxi.Map expects 2 arguments")
      }
      it("a map without arguments is Map<Any,Any>") {
         val type = """
            type MyMap inherits Map
         """.compiled()
            .model("MyMap")
            .getUnderlyingMapType()
         type.keyType.shouldBe(PrimitiveType.ANY)
         type.valueType.shouldBe(PrimitiveType.ANY)
      }
      it("should allow declaration of types that inherit Map") {
         val fooType = """
            model Foo inherits Map<String,Int>
         """.compiled()
            .objectType("Foo")
         fooType.isMapType().shouldBeTrue()
         val mapType = fooType.getUnderlyingMapType()
         mapType.keyType.shouldBe(PrimitiveType.STRING)
         mapType.valueType.shouldBe(PrimitiveType.INTEGER)
      }

      it("is possible to declare a field of type Map") {
         val friendsField = """
            model Foo {
               friends : Map<String,Int>
            }
         """.compiled()
            .model("Foo")
            .field("friends")
         val mapType = friendsField.type.asA<MapType>()
         mapType.keyType.shouldBe(PrimitiveType.STRING)
         mapType.valueType.shouldBe(PrimitiveType.INTEGER)
      }

      it("is possible to declare map type with user types") {
         val schema = """
            type Name inherits String
            type Age inherits Int

            type People inherits Map<Name,Age>
         """.compiled()
         val mapType = schema.model("People").getUnderlyingMapType()
         mapType.keyType.shouldBe(schema.type("Name"))
         mapType.valueType.shouldBe(schema.type("Age"))
      }
   }
})
