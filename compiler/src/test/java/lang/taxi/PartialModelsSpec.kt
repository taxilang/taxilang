package lang.taxi

import io.kotest.assertions.fail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import lang.taxi.compiler.PartialModelCompiler
import lang.taxi.types.Modifier
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName

class PartialModelsSpec : DescribeSpec({
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
            .modifiers.shouldHaveSize(1)
      }

      // Design choice: Partials cannot be closed.
      // Partials are intended to allow creation client-side of the object,
      // typically for sending to the server.
      // In some awkward scenarios, users need to be able to do things like
      // listOf(Thing$Partial) in a query - which won't construct if the
      // Thing is closed.
      // Note: This is true even if Thing is declared as a Parameter object, as object construction
      // happens in the query phase, not the parameter-building phase, so it's not treated as a parameter object.
      // eg:https://playground.taxilang.org/#pako:H4sIAAAAAAAAA51VbW/aMBD+K6do0qBidH2dFKnTiugHpK4w2MuHZh9MchBrjp3ZThlC/PedEwcS6KpqSKDk3vzcPc+ZTWDiFDMWhIFd5whDZGKUAJcpam4NzKzmchnJnfOBZfiCe4gm1jy3XMnjqEjmTFO+RQ2ZSlDAjC/leLGY4u8CjYVNJAGSfYnwsGYkt65MLJTBxNdwIVUmT0LfgHuTdFK4w/y/lSdsLRRLPDRmWZX5+NMnnJ7C1zEM7mB693n8/W4YyU/jjNuHQghz3K9L/ZZTGXwN4vKA15X7B8zK6cEeQ3W2KeaCxQgrbtN+v18eYjmN9LnD/DETH7LQKmvajyY4KWwLmSniGI0JYVY97DUyUEogq2kwqJ94XElq5p/LAipHzUp1LdE6bycpx1ePsRs28XSooa5LW9EZ2EjOi+eSezBXyTo8HitV3XfyQj0/l86+THtg3eeM1HLQC4wt5iYIHzfBrqoTAe1luzaFajS5ohGRj94wTtVIUlAQWl3g9mcvoGXSa/LyLFfatgjSkWxYR01Dexedw32daFI0CLl2wCxHAytViATmGKuM9rpSAZNwOxmBrrKdkCh5yZ9Qwqa+VW4gCs7OL6Kgd7j4NxTU2s4oaO5mAFvY1og8qh+kV/BaJUgIiZJvLaTsCYFBnHJRi3CV8jiFmBDOEQonzoXSYFMEwY0dLzpdV3LBJYn0ENgWmCHlaUd6vVg+yzX1pibWRewQxkyIpnTDsM1ho4uH/rwPIwLD5S/3axxAwko/9Ewzn7O5WIOxmvFlagn4imkn1IJaVStPBbdAAdIIkmxNmgJVuNXVVE5lucA/kBW2FFa4A3BSd9bWwG0cq0LaAym4uJrRe8XkbZ4LHpcVPbnX59eXl+fv3efq7OrDxfVlg+t20ZJylglH9b1a7Sl2p3gu3MimXuwVE50F18Z2qhu424V3H/fkHBE0UFqrFepqlYfk6jZj597t9n+we64jts2uS0I9/pLMOr4ZVBi3DC04DQ30mnZfqnXWySkt8+7Opatgs+0Fgq2V2+xNYIjtIWdL8gfhgiSPvdI2RZa4a6Jh+lKtv7sNKsPM/8WX98P2L1+GAEjzBwAA
      it("partials of closed models have closed removed") {
         val person = """
            closed model Contacts {
               email : EmailAddress inherits String
            }
            closed model Person {
               contacts : Contacts
            }
            partial parameter model PartialPerson from Person
         """.compiled()
            .model("PartialPerson")
         person.modifiers.shouldNotContain(Modifier.CLOSED)
         person.modifiers.shouldContain(Modifier.PARAMETER_TYPE)
         val contacts = person.field("contacts").type.asA<ObjectType>();
         contacts.modifiers.shouldNotContain(Modifier.CLOSED)
         contacts.modifiers.shouldContainAll(Modifier.PARAMETER_TYPE)
         contacts.modifiers.shouldHaveSize(1)
      }
   }
})
