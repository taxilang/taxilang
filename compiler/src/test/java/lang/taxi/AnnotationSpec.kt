package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.EnumMember
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AnnotationSpec : Spek({
   describe("annotations") {
      it("should compile annotation with no fields") {
         """
            annotation Foo
         """.compiled()
            .annotation("Foo")
            .fields.should.be.empty
      }
      it("should compile annotation in namespace") {
         """
            namespace acme {
               annotation Foo
            }
         """.compiled()
            .annotation("acme.Foo")
            .should.not.be.`null`
      }
      it("should compile annotation with empty body") {
         """
            annotation Foo {}
         """.compiled()
            .annotation("Foo")
            .fields.should.be.empty
      }

      it("should be possible to define annotations with string fields") {
         """
            type Name inherits String
             annotation OwnedBy {
               name : Name
             }
         """.compiled()
            .annotation("OwnedBy")
            .field("name")
            .type.qualifiedName.should.equal("Name")
      }
      it("should be possible to define annotations with enum fields") {
         """
            enum Quality {
               HIGH, MEDIUM, BAD
             }
             annotation DataQuality {
               quality : Quality
             }
         """.compiled()
            .annotation("DataQuality")
            .field("quality")
            .type.qualifiedName.should.equal("Quality")
      }
      it("is an error to define annotations with object type fields") {
         val errors = """
            model Person {
               name : String
            }
            annotation Owner {
               person: Person
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("It is invalid to declare an annotation property of type Person. Only Strings, Numbers, Booleans or Enums are supported for annotation properties")
      }
      it("is an error to define annotations with types that cannot be resolved") {
         val errors = """
            annotation Owner {
               person: Person
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("Person is not defined")
      }


      it("should be possible to use an annotation that has not been compiled, to support backwards compatability") {
         """
            @DataQuality(quality = "MEDIUM")
             model Foo {}
         """.compiled()
            .objectType("Foo")
            .annotation("DataQuality")
            .should.not.be.`null`
      }
      it("should resolve compiled annotations within the same namespace") {}
      it("should be possible to declare enums within annotation usage") {
         val annotation = """
            enum Quality {
               HIGH, MEDIUM, BAD
             }
             annotation DataQuality {
               quality : Quality
             }
             @DataQuality(quality = Quality.MEDIUM)
             model Foo {}
         """.compiled()
            .objectType("Foo")
            .annotation("DataQuality")
         val enumMember = annotation.parameters["quality"] as EnumMember
         enumMember.enum.qualifiedName.should.equal("Quality")
         enumMember.value.name.should.equal("MEDIUM")
      }

      describe("compilation errors in using annotations") {
         val schema = """
            enum Quality {
               HIGH, MEDIUM, BAD
             }
             annotation DataQuality {
               quality : Quality
             }
         """.trimIndent()
         it("should raise an error if an annotation usage does not include all args") {
            val errors = """
            $schema

             @DataQuality
             model Foo {}
         """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("Annotation DataQuality requires member 'quality' which was not supplied")
         }
         it("should raise an error if an annotation usage includes an arg that isn't delcared") {
            val errors = """
            $schema

             @DataQuality(quality = Quality.MEDIUM, foo = "bar")
             model Foo {}
         """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("Unexpected property - 'foo' is not a member of DataQuality")
         }
         it("should raise an error if an annotation references an invalid member of an enum") {
            val errors = """
            $schema

             @DataQuality(quality = Quality.Foo)
             model Foo {}
         """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("Quality does not have a member Foo")
         }
         // Waiting for type checking branch to merge
         xit("should raise an error if an annotation usage includes incorrect types") {
            val errors = """
            enum Quality {
               HIGH, MEDIUM, BAD
             }
             annotation DataQuality {
               quality : Quality
             }
             @DataQuality(foo = "bar")
             model Foo {}
         """.validated()
            errors.should.have.size(1)
            TODO()
         }
      }


      it("should resolve compiled annotations using imports") {
         val srcA = """
            namespace foo {
               enum Quality {
               HIGH, MEDIUM, BAD
             }
             annotation DataQuality {
               quality : Quality
             }
            }
         """.trimIndent()
         val srcB = """
import foo.Quality
import foo.DataQuality
namespace bar

@DataQuality(quality = Quality.HIGH)
model Thing {}
         """.trimIndent()
         val objectType = Compiler.forStrings(srcA,srcB)
            .compile()
            .objectType("bar.Thing")
         val annotation = objectType.annotation("foo.DataQuality")
         annotation.type!!.qualifiedName.should.equal("foo.DataQuality")
         val enumParam = annotation.parameters["quality"] as EnumMember
         enumParam.enum.qualifiedName.should.equal("foo.Quality")
         enumParam.value.name.should.equal("HIGH")
      }
      it("should resolve compiled annotations that are used before they're declared") {
         val annotation = """
@DataQuality(quality = Quality.HIGH)
model Foo {}
annotation DataQuality {
// Just for fun, use an enum before it's declared too.
   quality : Quality
}
enum Quality {
 HIGH, MEDIUM, BAD
}
         """.compiled()
            .objectType("Foo")
            .annotation("DataQuality")
         annotation.type?.qualifiedName?.should?.equal("DataQuality")
         val qualityParam = annotation.parameter("quality") as EnumMember
         qualityParam.value.name.should.equal("HIGH")

      }
   }
})
