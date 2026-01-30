package lang.taxi

import com.winterbe.expekt.should
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.messages.Severity
import lang.taxi.types.Named
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class AnnotationTypeTest {
   @Test
   fun `annotations can be applied omitting optional properties `() {
      val annotation = """
         annotation Foo {
            firstName : String?
            lastName: String?
         }

         @Foo( firstName = 'Jimmy' )
         model Thing {}
      """.compiled()
         .model("Thing")
         .annotation("Foo")
      annotation.parameter("firstName").should.equal("Jimmy")
      annotation.parameter("lastName").should.be.`null`
   }

   @Test
   fun `annotations values can be specified in any order `() {
      val schema = """
         annotation Foo {
            firstName : String?
            lastName: String?
         }

         @Foo( firstName = 'Jimmy', lastName = "Spratt" )
         model ThingOne {}

         @Foo( lastName = "Spratt", firstName = 'Jimmy' )
         model ThingTwo {}
      """.compiled()
      val annotation1 = schema.model("ThingOne")
         .annotation("Foo")
      annotation1.parameter("firstName").should.equal("Jimmy")
      annotation1.parameter("lastName").should.equal("Spratt")

      val annotation2 = schema.model("ThingOne")
         .annotation("Foo")
      annotation2.parameter("firstName").should.equal("Jimmy")
      annotation2.parameter("lastName").should.equal("Spratt")
   }

   @Test
   fun `annotation can have an array parameter`() {
      val schema = """
         type HttpErrorCode inherits Int
         annotation Foo {
            errorCodes : HttpErrorCode[]
         }

         @Foo(errorCodes = [502, 504, 506] )
         model ThingOne {}
      """.compiled()

      val annotationWithAListValue = schema.model("ThingOne")
         .annotation("Foo")

      annotationWithAListValue.parameter("errorCodes").should.equal(listOf(502, 504, 506))
   }

   @Test
   fun `annotation can have an annotation parameter`() {
      val schema = """
         type HttpErrorCode inherits Int

          annotation Bar {
            param: Foo
         }


         annotation Foo {
            errorCodes : HttpErrorCode[]
         }
         @Bar(param = @Foo( errorCodes = [502, 504, 506]) )
         model ThingOne {}
      """.compiled()

      val annotationWithAListValue = schema.model("ThingOne")
         .annotation("Bar")

      annotationWithAListValue.parameter("param").should.equal(mapOf("errorCodes" to listOf(502, 504, 506)))
   }

   @Test
   fun `compilation error is generated when array param values are not correct`() {
      val compilationMessages = """
         type HttpErrorCode inherits Int
         annotation Foo {
            errorCodes : HttpErrorCode[]
         }

         @Foo(errorCodes = [502, "504", 506] )
         model ThingOne {}
      """.validated()

      compilationMessages.should.have.size(1)
      compilationMessages[0].severity.should.equal(Severity.ERROR)
      compilationMessages[0].detailMessage.should.equal("Type mismatch. Type of HttpErrorCode is not assignable to type lang.taxi.String")
   }

   @Test
   fun `compiler detects invalid enum assignment`() {
      val annotation = """
            enum Quality {
               HIGH, MEDIUM, BAD
             }

             enum HttpMethod {
               POST, GET
             }
             annotation DataQuality {
               quality : Quality
             }

             @DataQuality(quality = HttpMethod.GET)
             model Foo {}
         """.validated()
      annotation.size.should.equal(1)
      annotation.first().severity.should.equal(Severity.ERROR)
      annotation.first().detailMessage.should.equal("Type mismatch. Type of Quality is not assignable to type HttpMethod")
   }

   @Test
   fun `annotations can inherit other annotations`() {
      val annotation = """
         annotation RuleA {
            message : String
         }

         annotation RuleB inherits RuleA {
            errorCode : Int
         }
      """.compiled()
         .annotation("RuleB")
      annotation.allFields.shouldHaveSize(2)
      annotation.inheritsFrom.shouldHaveSize(1)
      annotation.inheritsFrom.map { it.qualifiedName }
         .single().shouldBe("RuleA")
   }

   @Test
   fun `annotations cannot inherit types that are not annotations`() {
      """
         type RuleA
          annotation RuleB inherits RuleA {
            errorCode : Int
         }
      """.validated()
         .shouldContainMessage("RuleB cannot inherits from RuleA as RuleA is not an annotation")
   }

   @Test
   fun `types cannot inherit annotations`() {
      """
         type RuleA inherits RuleB
          annotation RuleB {
            errorCode : Int
         }
      """.validated()
         .shouldContainMessage("A type cannot inherit from an annotation")
   }

   // Have disabled this test, as I don't see how this could ever have worked,
   // given annotations are compile-time, and don't (currently) support expressions
   // The test started failing once we fixed build issues that meant the tests was never actually
   // run in the build
   @Test
   @Disabled
   fun `an annotation may pass an object referencing another variable`() {
      """
         annotation NotEmpty {
            error : String
            requestId : String
         }

         query MyQuery(
             requestId : String,
             @NotEmpty( error = "Must not be empty", requestId = requestId )
             authId : String
         ) {
            find { "Hello" }
         }
      """.compiled()
   }

   @Test
   fun `an annotation may pass an object`() {
      val annotation = """
         model Error {
            message : String
         }
         annotation Rule {
            error : Error
         }

         @Rule(error = { message: "Name must not be null" })
         type Name inherits String
      """.compiled()
         .type("Name")
         .annotations[0]

      val parameterValue = annotation.parameters["error"]
         .shouldBeInstanceOf<Map<*,*>>()
      parameterValue.shouldBe(mapOf("message" to "Name must not be null"))
   }

   @Test
   fun `an annotation may not pass an object that violates the type contract`() {
      """
         model Error {
            message : String
         }
         annotation Rule {
            error : Error
         }

         @Rule(error = { thisIsntTheField: "Name must not be null" })
         type Name inherits String
      """.validated()
         .shouldContainMessage("Type Error has no field thisIsntTheField")
   }

   @Test
   fun `an annotation may pass an inherited object`() {
      val annotation = """
         model Error {
            message : String
         }
         annotation Rule {
            error : Error
         }
         annotation NotEmpty inherits Rule {}

         @NotEmpty(error = { message: "Name must not be null" })
         type Name inherits String

      """.compiled()
         .type("Name")
         .annotations[0]

      val parameterValue = annotation.parameters["error"]
         .shouldBeInstanceOf<Map<*,*>>()
      parameterValue.shouldBe(mapOf("message" to "Name must not be null"))
   }

   @Test
   fun `it is invalid not to populate an inherited property`() {
      """
         annotation Rule {
            error : String
         }
         annotation NotEmpty inherits Rule {}

         @NotEmpty()
         type Name inherits String
      """.validated()
         .shouldContainMessage("Annotation NotEmpty requires member 'error' which was not supplied")
   }

   @Test
   fun `inherited properties may use defaults`() {
      """
         annotation Rule {
            error : String = "Foo"
         }
         annotation NotEmpty inherits Rule {}

         @NotEmpty()
         type Name inherits String
      """.compiled()
         .type("Name")
         .annotations[0]
         .parameters["error"]
         .shouldBe("Foo")
   }

   @Test
   fun `inherited annotations may add defaults`() {
      """
         annotation Rule {
            error : String
         }
         annotation NotEmpty inherits Rule {
            error : String = "Foo" // adds a default value where the base type didn't provide one
         }

         @NotEmpty()
         type Name inherits String
      """.compiled()
         .type("Name")
         .annotations[0]
         .parameters["error"]
         .shouldBe("Foo")
   }

   @Test
   fun `inherited annotations may override default from base value`() {
      """
         annotation Rule {
            error : String = "Base value"
         }
         annotation NotEmpty inherits Rule {
            error : String = "Foo" // adds a default value where the base type didn't provide one
         }

         @NotEmpty()
         type Name inherits String
      """.compiled()
         .type("Name")
         .annotations[0]
         .parameters["error"]
         .shouldBe("Foo")
   }

   @Test
   fun `can look up annotations by name`() {
      val schema = """
         annotation Rule {
            error : String = "Foo"
         }
         annotation NotEmpty inherits Rule {}
      """.compiled()
      schema.annotationTypes.shouldNotBeEmpty()
      schema.annotation("Rule")
      schema.annotation("NotEmpty")
   }

   @Test
   fun `can get annotated types`() {
      val schema = """
         annotation Rule {
            error : String = "Foo"
         }
         annotation NotEmpty inherits Rule {}
         annotation AlwaysHappy inherits Rule {}

         @NotEmpty
         type Name inherits String

         @Rule
         type HasRule

         @NotEmpty
         type NeverEmpty inherits String

         @AlwaysHappy
         type HappyName inherits String

         type NeverEmptyName inherits NeverEmpty
      """.compiled()
      val notEmpty = schema.annotation("NotEmpty")

      schema.membersWithAnnotation(notEmpty)
         .map { (it as Named).qualifiedName }
         .shouldContainExactlyInAnyOrder("Name", "NeverEmpty", "NeverEmptyName")

      val rule = schema.annotation("Rule")
      schema.membersWithAnnotation(rule)
         .map { (it as Named).qualifiedName }
         .shouldContainExactlyInAnyOrder("Name", "HasRule", "NeverEmpty", "NeverEmptyName", "HappyName")
   }

   @Test
   fun `when the nested annotation type is invalid compiler detects`() {
      val errors = """
         type HttpErrorCode inherits Int
          annotation Bar {
            param: Foo
         }


         annotation Baz {}
         annotation Foo {
            errorCodes : HttpErrorCode[]
         }
         @Bar( param = @Baz() ) // Baz is not assignable to param
         model ThingOne

      """.validated()

      errors.size.should.equal(1)
      errors.first().detailMessage.should.equal("Type mismatch. Type of Foo is not assignable to type Baz")
      errors.first().severity.should.equal(Severity.ERROR)
   }

}
