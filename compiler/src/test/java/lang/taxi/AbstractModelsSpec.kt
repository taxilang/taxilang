package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.services.operations.constraints.ConstantValueExpression
import lang.taxi.services.operations.constraints.EnumValueExpression
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.types.BaseTypeDiscriminatorField
import lang.taxi.types.Modifier
import lang.taxi.types.SubTypeDiscriminatorField
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AbstractModelsSpec : Spek({
   describe("abstract models") {
      describe("declaring abstract models") {
         val schema = """
            enum AnimalType {
               MAMMAL,
               REPTILE
            }
            abstract model Animal(animalType : AnimalType) {
               speciesName : SpeciesName as String
            }
            model Person inherits Animal( this.animalType = AnimalType.MAMMAL ) {
               favouriteCoffee : FavouriteCoffee as String
            }
         """.compiled()
         it("is possible to declare an abstract model") {
            val objectType = schema.objectType("Animal")
            objectType.modifiers.should.have.elements(Modifier.ABSTRACT)
         }
         it("discriminator properties are present on the base type") {
            val objectType = schema.objectType("Animal")

            val discriminator = objectType.discriminatorField as BaseTypeDiscriminatorField
            discriminator.field.name.should.equal("animalType")
            discriminator.field.type.should.equal(schema.type("AnimalType"))

            objectType.fields.should.contain(discriminator.field)
         }
         it("resolves discriminator field on subtypes with the value populated") {
            val person = schema.objectType("Person")
            val discriminator = person.discriminatorField as SubTypeDiscriminatorField

            discriminator.field.name.should.equal("animalType")
            person.allFields.should.contain(discriminator.field)

            discriminator.expression.should.not.be.`null`
         }

         it("includes base types in inherited type list") {
            schema.objectType("Person")
               .inheritsFrom
               .should.contain(schema.type("Animal"))
         }
         it("properties from base type are present on sub type") {
            val person = schema.objectType("Person")
            person.allFields.should.have.size(3)
         }

         it("subtypes have a defined discriminator") {
            val person = schema.objectType("Person")
            person.discriminatorField
         }

         it("is possible to use a string as a discriminator") {
            val schema = """
               type AnimalType inherits String
            abstract model Animal(animalType : AnimalType) {
               speciesName : SpeciesName as String
            }
            model Person inherits Animal( this.animalType = "MAMMAL" ) {
               favouriteCoffee : FavouriteCoffee as String
            }""".compiled()
            val discriminatorField = schema.objectType("Person")
               .discriminatorField as SubTypeDiscriminatorField
            val expected = (discriminatorField.expression as PropertyToParameterConstraint).expectedValue as ConstantValueExpression
            expected.value.should.equal("MAMMAL")
         }
         it("is possible to use a number as a discriminator") {
            val schema = """
               type AnimalType inherits Int
            abstract model Animal(animalType : AnimalType) {
               speciesName : SpeciesName as String
            }
            model Person inherits Animal( this.animalType =123 ) {
               favouriteCoffee : FavouriteCoffee as String
            }""".compiled()
            val discriminatorField = schema.objectType("Person")
               .discriminatorField as SubTypeDiscriminatorField
            val expected = (discriminatorField.expression as PropertyToParameterConstraint).expectedValue as ConstantValueExpression
            expected.value.should.equal(123)
         }
         it("is possible to use an enum as a discriminator") {
            // Not redefining the schema here - this test uses the shared schema above.
            val discriminatorField = schema.objectType("Person")
               .discriminatorField as SubTypeDiscriminatorField
            val expected = (discriminatorField.expression as PropertyToParameterConstraint).expectedValue as EnumValueExpression
            expected.enumValue.qualifiedName.should.equal("AnimalType.MAMMAL")
         }
      }
      describe("Error conditions") {

         it("is illegal to specify a discriminator on a non-abstract type") {
            val errors = """
               type Thing inherits String
               model Foo( thing : Thing) {}
            """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.startWith("Cannot specify a discriminator here, as the model is not abstract.")
         }
         it("is illegal to specify a discriminator on the subtype of a non-abstract type") {
            val errors = """
               type Thing inherits String
               model BaseType {
                  thing : Thing
               }
               model SubType inherits BaseType( this.thing = "" )
            """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.startWith("Cannot specify a discriminator value here, as super type BaseType does not expose a discriminator")
         }
         it("is illegal to declare a subtype of an abstract type without providing a discriminator value") {
            val errors = """
               type Thing inherits String
               abstract model BaseType( thing:Thing ) {
                  thing : Thing
               }
               model SubType inherits BaseType // This is invalid, as we must specify the discriminator
            """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.startWith("Base type BaseType is abstract and specifies a discriminator on field 'thing'.  A discriminator value must be provided here")
         }

         it("is an error if the field referenced in the subtype is not the discriminator field") {}

      }

   }
})
