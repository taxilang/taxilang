package lang.taxi.generators.typescript

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import lang.taxi.Compiler
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.packages.TaxiPackageProject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Path


class TypeScriptGeneratorTest {

   @Test
   fun generatesMultipleNamespaces() {
      val taxi = """
         namespace people {
            type LastName inherits String
            model Person {
                firstName : FirstName inherits String
                age : Age inherits Int
                isLiving : IsLiving inherits Boolean
            }
         }
         namespace animals {
            model Cow {
                name : Name inherits String
                age : people.Age
            }
         }
      """.trimIndent()

      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
        export namespace people {
           export type LastNameType = string;
           export type LastName = DatatypeContainer<LastNameType>;
           export type FirstNameType = string;
           export type FirstName = DatatypeContainer<FirstNameType>;
           export type AgeType = number;
           export type Age = DatatypeContainer<AgeType>;
           export type IsLivingType = boolean;
           export type IsLiving = DatatypeContainer<IsLivingType>;
           export type Person = DatatypeContainer<{ readonly firstName: people.FirstName; readonly age: people.Age; readonly isLiving: people.IsLiving }>;
           export class Taxonomy {
             @Datatype('people.LastName')
             readonly LastName: LastName = buildDatatypeContainer('people.LastName', '');
             @Datatype('people.FirstName')
             readonly FirstName: FirstName = buildDatatypeContainer('people.FirstName', '');
             @Datatype('people.Age')
             readonly Age: Age = buildDatatypeContainer('people.Age', 0);
             @Datatype('people.IsLiving')
             readonly IsLiving: IsLiving = buildDatatypeContainer('people.IsLiving', false);
             @Datatype('people.Person')
             readonly Person: Person = buildDatatypeContainer('people.Person', {
               firstName: this.FirstName,
               age: this.Age,
               isLiving: this.IsLiving
             });
           }
        }

        export namespace animals {
          export type NameType = string;
          export type Name = DatatypeContainer<NameType>;
          export type Cow = DatatypeContainer<{ readonly name: animals.Name; readonly age: people.Age }>;
          export class Taxonomy {
            @Datatype('animals.Name')
            readonly Name: Name = buildDatatypeContainer('animals.Name', '');
            @Datatype('animals.Cow')
            readonly Cow: Cow = buildDatatypeContainer('animals.Cow', {
              name: this.Name,
              age: people.Taxonomy.Age
            });
          }
        }
        export const taxonomy = { people: new people.Taxonomy(), animals: new animals.Taxonomy() };
         """.removeWhitespace()
      expect(output).to.equal(expected)
   }

   @Test
   fun givenTypeHasTypeAlias_then_itIsGenerated() {
      val taxi = """
         namespace vyne {
             model Person {
                 firstName : FirstName inherits String
                 lastName : LastName inherits String
                 age : Age inherits Int
                 living : IsAlive inherits Boolean
             }
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
         export namespace vyne {
           export type FirstNameType = string;
           export type FirstName = DatatypeContainer<FirstNameType>;
           export type LastNameType = string;
           export type LastName = DatatypeContainer<LastNameType>;
           export type AgeType = number;
           export type Age = DatatypeContainer<AgeType>;
           export type IsAliveType = boolean;
           export type IsAlive = DatatypeContainer<IsAliveType>;
           export type Person = DatatypeContainer<{ readonly firstName: vyne.FirstName; readonly lastName: vyne.LastName; readonly age: vyne.Age; readonly living: vyne.IsAlive }>;
           export class Taxonomy {
             @Datatype('vyne.FirstName')
             readonly FirstName: FirstName = buildDatatypeContainer('vyne.FirstName', '');
             @Datatype('vyne.LastName')
             readonly LastName: LastName = buildDatatypeContainer('vyne.LastName', '');
             @Datatype('vyne.Age')
             readonly Age: Age = buildDatatypeContainer('vyne.Age', 0);
             @Datatype('vyne.IsAlive')
             readonly IsAlive: IsAlive = buildDatatypeContainer('vyne.IsAlive', false);
             @Datatype('vyne.Person')
             readonly Person: Person = buildDatatypeContainer('vyne.Person', {
               firstName: this.FirstName,
               lastName: this.LastName,
               age: this.Age,
               living: this.IsAlive
             });
           }
         }
         export const taxonomy = { vyne: new vyne.Taxonomy() };
         """.removeWhitespace()
      expect(output).to.equal(expected)
   }

   @Test
   fun generatesArraysAsLists() {
      val taxi = """
         type Person {
              friends : Person[]
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
         export type Person = DatatypeContainer<{ readonly friends: Person[] }>;
         export class Taxonomy {

           @Datatype('Person')
           readonly Person: Person = buildDatatypeContainer('Person', {
             friends: [this.Person]
           });
         }
         export const taxonomy = { ...(new Taxonomy()) };
      """.removeWhitespace()
      expect(output).to.equal(expected)
   }

   @Test
   fun nullableTypesAreGeneratedCorrectly() {
      val taxi = """
         type MiddleName inherits String
         type Person {
            middleName : MiddleName?
         }
      """.trimMargin()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
         export type MiddleNameType = string;
         export type MiddleName = DatatypeContainer<MiddleNameType>;
         export type Person = DatatypeContainer<{ readonly middleName: MiddleName }>;
         export class Taxonomy {
           @Datatype('MiddleName')
           readonly MiddleName: MiddleName = buildDatatypeContainer('MiddleName', '');
           @Datatype('Person')
           readonly Person: Person = buildDatatypeContainer('Person', {
             middleName?: this.MiddleName
           });
         }
         export const taxonomy = { ...(new Taxonomy()) };
         """.removeWhitespace()
      output.should.equal(expected)
   }

   @Disabled("Not supported yet")
   @Test
   fun enumTypes() {
      val taxi = """
         type Person {
             gender : Gender
         }
         enum Gender {
             MALE,
             FEMALE
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """

      """.removeWhitespace()
      expect(output).to.equal(expected)
   }

   @Disabled("Not supported yet")
   @Test
   fun enumTypesThatInherit() {
      val taxi = """
         enum Direction { Buy, Sell }
         // Note - when we fix enum generation, this should stop compiling
         enum BankDirection inherits Direction
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """

      """.removeWhitespace()

      output.should.equal(expected)
   }

   @Test
   fun scalarTypes() {
      val taxi = """
         type Name inherits String
         type FirstName inherits Name
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
         export type NameType = string;
         export type Name = DatatypeContainer<NameType>;
         export type FirstNameType = string;
         export type FirstName = DatatypeContainer<FirstNameType>;

         export class Taxonomy {
           @Datatype('Name')
           readonly Name: Name = buildDatatypeContainer('Name', '');
           @Datatype('FirstName')
           readonly FirstName: FirstName = buildDatatypeContainer('FirstName', '');

         }
         export const taxonomy = { ...(new Taxonomy()) };
         """.removeWhitespace()

      output.should.equal(expected)
   }

   @Disabled("Not supported yet")
   @Test
   fun emptyTypesShouldBeInterface() {
      val taxi = """
         type Person
      """.trimIndent()
      val output = compileAndGenerate(taxi).removeWhitespace()
      val expected = """
         import lang.taxi.annotations.DataType
         import taxi.generated.TypeNames.Person

         @DataType(
           value = Person,
           imported = true
         )
         interface Person

         package taxi.generated

         import kotlin.String

         object TypeNames {
           const val Person: String = "Person"
         }
      """.removeWhitespace()

      output.should.equal(expected)
   }

   @Disabled("Not supported yet")
   @Test
   fun objectTypesThatInheritEmptyTypes() {
      val taxi = """
         type Instrument
         type Money inherits Instrument {
            currency : CurrencySymbol inherits String
            amount : MoneyAmount inherits Decimal
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
      """.removeWhitespace()
      output.should.equal(expected)
   }

   @Disabled("Not supported yet")
   @Test
   fun objectTypesThatInherit() {
      val taxi = """
         type Money {
            currency : CurrencySymbol inherits String
            amount : MoneyAmount inherits Decimal
         }
         type Notional inherits Money
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
      """.removeWhitespace()

      output.should.equal(expected)
   }
}

private fun String.removeWhitespace(): String {
   return this.filter { !it.isWhitespace() }
}

fun compileAndGenerate(taxi: String): String {
   val taxiDoc = Compiler.forStrings(taxi).compile()
   val output = TypeScriptGenerator().generate(taxiDoc, emptyList(), MockEnvironment)
   return output.joinToString("\n") { it.content }
}

fun String.trimNewLines(): String {
   return this.removePrefix("\n").removeSuffix("\n").trim()
}

object MockEnvironment : TaxiProjectEnvironment {
   override val projectRoot: Path
      get() = TODO("Not yet implemented")
   override val outputPath: Path
      get() = TODO("Not yet implemented")
   override val project: TaxiPackageProject
      get() = TODO("Not yet implemented")
}
