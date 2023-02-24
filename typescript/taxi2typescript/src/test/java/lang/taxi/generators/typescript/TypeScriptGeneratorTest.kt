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
         namespace animals.mammals {
            model Whale {
              weight : Weight inherits Int
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
           export type Person = DatatypeContainer<{ readonly firstName: people.FirstNameType; readonly age: people.AgeType; readonly isLiving: people.IsLivingType }>;
           export class Taxonomy {
             readonly LastName: LastName = buildDatatypeContainer('people.LastName', '');
             readonly FirstName: FirstName = buildDatatypeContainer('people.FirstName', '');
             readonly Age: Age = buildDatatypeContainer('people.Age', 0);
             readonly IsLiving: IsLiving = buildDatatypeContainer('people.IsLiving', false);
             readonly Person: Person = buildDatatypeContainer('people.Person', {
               firstName: '',
               age: 0,
               isLiving: false
             });
           }
         }

         export namespace animals {
           export type NameType = string;
           export type Name = DatatypeContainer<NameType>;
           export type Cow = DatatypeContainer<{ readonly name: animals.NameType; readonly age: people.AgeType }>;
           export class Taxonomy {
             readonly Name: Name = buildDatatypeContainer('animals.Name', '');
             readonly Cow: Cow = buildDatatypeContainer('animals.Cow', {
               name: '',
               age: 0
             });
           }
         }

         export namespace animals.mammals {
           export type WeightType = number;
           export type Weight = DatatypeContainer<WeightType>;
           export type Whale = DatatypeContainer<{ readonly weight: animals.mammals.WeightType }>;
           export class Taxonomy {
             readonly Weight: Weight = buildDatatypeContainer('animals.mammals.Weight', 0);
             readonly Whale: Whale = buildDatatypeContainer('animals.mammals.Whale', {
               weight: 0
             });
           }
         }
         export const taxonomy = { people: { ...(new people.Taxonomy()) }, animals: { mammals: { ...(new animals.mammals.Taxonomy()) }, ...(new animals.Taxonomy()) } };
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
      val output = compileAndGenerate(taxi)
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
           export type Person = DatatypeContainer<{ readonly firstName: vyne.FirstNameType; readonly lastName: vyne.LastNameType; readonly age: vyne.AgeType; readonly living: vyne.IsAliveType }>;
           export class Taxonomy {
             readonly FirstName: FirstName = buildDatatypeContainer('vyne.FirstName', '');
             readonly LastName: LastName = buildDatatypeContainer('vyne.LastName', '');
             readonly Age: Age = buildDatatypeContainer('vyne.Age', 0);
             readonly IsAlive: IsAlive = buildDatatypeContainer('vyne.IsAlive', false);
             readonly Person: Person = buildDatatypeContainer('vyne.Person', {
               firstName: '',
               lastName: '',
               age: 0,
               living: false
             });
           }
         }
         export const taxonomy = { vyne: { ...(new vyne.Taxonomy()) } };
         """.removeWhitespace()
      output.shouldEqualIgnoringHeaderAndWhitespace(expected)
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

  readonly Person: Person = buildDatatypeContainer('Person', {
    friends: []
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
export type Person = DatatypeContainer<{ readonly middleName?: MiddleNameType }>;
export class Taxonomy {
    readonly MiddleName: MiddleName = buildDatatypeContainer('MiddleName', '');
    readonly Person: Person = buildDatatypeContainer('Person', {
        middleName: ''
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
      val output = compileAndGenerate(taxi)
      val expected = """
export type NameType = string;
export type Name = DatatypeContainer<NameType>;
export type FirstNameType = string;
export type FirstName = DatatypeContainer<FirstNameType>;

export class Taxonomy {
  readonly Name: Name = buildDatatypeContainer('Name', '');
  readonly FirstName: FirstName = buildDatatypeContainer('FirstName', '');

}
export const taxonomy = {
...(new Taxonomy())
};
         """

      output.shouldEqualIgnoringHeaderAndWhitespace(expected)
   }

   @Test
   fun `does not generate taxonomy reference for empty namespaces`() {
      val taxi = """
namespace geography {
  type Foo inherits String
}
namespace geography.countries.codes {
   type Bar inherits String
}
      """.trimIndent()
      val output = compileAndGenerate(taxi)
      output.shouldEqualIgnoringHeaderAndWhitespace(
         """
export namespace geography {
  export type FooType = string;
  export type Foo = DatatypeContainer<FooType>;

  export class Taxonomy {
    readonly Foo: Foo = buildDatatypeContainer('geography.Foo', '');

  }
}

export namespace geography.countries.codes {
  export type BarType = string;
  export type Bar = DatatypeContainer<BarType>;

  export class Taxonomy {
    readonly Bar: Bar = buildDatatypeContainer('geography.countries.codes.Bar', '');

  }
}
export const taxonomy = {
  geography: {
      countries: {
            codes: {
                   ...(new geography.countries.codes.Taxonomy())
            },
      },    ...(new geography.Taxonomy())
  }
};""".trimIndent()
      )
   }

   @Test
   fun `handles primitive types`() {
      val taxi = """
         model Person {
            name : String
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi)
      val expected = """
export type Person = DatatypeContainer<{ readonly name: string }>;
export class Taxonomy {

  readonly Person: Person = buildDatatypeContainer('Person', {
    name: ''
  });
}
export const taxonomy = { ...(new Taxonomy()) };
         """
      output.shouldEqualIgnoringHeaderAndWhitespace(expected)
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

private fun String.shouldEqualIgnoringHeaderAndWhitespace(expected: String) {
   this.substringAfter(staticHeader).removeWhitespace()
      .should.equal(expected.removeWhitespace())
}

private fun String.removeWhitespace(): String {
   return this.filter { !it.isWhitespace() }
}

fun compileAndGenerate(taxi: String): String {
   val taxiDoc = Compiler.forStrings(taxi).compile()
   val output = TypeScriptGenerator().generate(taxiDoc, emptyList(), MockEnvironment)
   return output.joinToString("\n") { it.content }
}

object MockEnvironment : TaxiProjectEnvironment {
   override val projectRoot: Path
      get() = TODO("Not yet implemented")
   override val outputPath: Path
      get() = TODO("Not yet implemented")
   override val project: TaxiPackageProject
      get() = TODO("Not yet implemented")
}
