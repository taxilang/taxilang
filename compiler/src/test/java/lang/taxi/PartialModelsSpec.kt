package lang.taxi

import io.kotest.assertions.fail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import lang.taxi.compiler.PartialModelCompiler
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName

class PartialModelsSpec :  DescribeSpec({
   describe("partial models") {
      it("is valid to declare a partial model") {
         val model = """
            type SpouseName inherits String
            model Person {
               name : Name inherits String
               spouseName : SpouseName?
            }
            partial model PartialPerson from Person
         """.compiled()
            .model("PartialPerson")

         model.field("name").nullable.shouldBeTrue()
         model.field("spouseName").nullable.shouldBeTrue()
      }
      it("all attributes of nested objects are also nullable") {
         val schema = """
            namespace com.foo.pets

            type SpouseName inherits String
            model Pet {
               name : PetName inherits String
               parent : Pet // self-referential type should also be updated
            }
            model Person {
               name : Name inherits String
               pet : Pet
               deadPets: Pet[]
            }
            partial model PartialPerson from Person
         """.compiled()

         val model = schema.model("com.foo.pets.PartialPerson")

         model.isPartialType.shouldBeTrue()
         model.partialOfType.shouldBe(schema.model("com.foo.pets.Person"))

         model.field("name").nullable.shouldBeTrue()
         model.field("pet").nullable.shouldBeTrue()
         model.field("deadPets").nullable.shouldBeTrue()
         val partialPet = model.field("pet").type
            .asA<ObjectType>()
         partialPet.isPartialType.shouldBeTrue()
         partialPet.partialOfType.shouldBe(schema.model("com.foo.pets.Pet"))
         partialPet.field("name").nullable.shouldBeTrue()
         partialPet.field("parent").nullable.shouldBeTrue()
         partialPet.field("parent").type.shouldBe(partialPet)

         model.field("deadPets").type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<com.foo.pets.Pet\$Partial>")
      }
      it("updates self-references of model to partial model") {
         val schema = """
            type SpouseName inherits String
            model Person {
               name : Name inherits String
               spouse : Person
               friends : Person[]
            }
            partial model PartialPerson from Person
         """.compiled()
         val model = schema.model("PartialPerson")
         model.field("name").nullable.shouldBeTrue()
         model.field("name").type.qualifiedName.shouldBe("Name")
         model.field("spouse").type.toQualifiedName().parameterizedName.shouldBe("Person\$Partial")
         model.field("friends").type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<Person\$Partial>")
      }
      it("is invalid to declare a partial model against a non-existent type") {
         """
            partial model PartialPerson from Person
         """.validated()
            .errors()
            .shouldContainMessage("Person is not defined")
      }

      // I'm not sure this is the right behaviour. I think it probably is.
      it("merges annotations from reference type") {
         """
             @Validated
             model Person {
               name : Name inherits String
            }
            @OmitNulls
            partial model PartialPerson from Person
         """.compiled()
            .model("PartialPerson")
            .annotations.shouldHaveSize(2)
      }

      // I'm not sure this is the right behaviour. I think it probably is.
      it("inherits modifiers from reference type") {
         """
             closed model Person {
               name : Name inherits String
            }
            partial parameter model PartialPerson from Person
         """.compiled()
            .model("PartialPerson")
            .modifiers.shouldHaveSize(2)
      }
   }
})
