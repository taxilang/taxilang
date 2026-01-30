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
   fun generatesSchemaWithBackTickVariables() {
      val taxi = """
        namespace animals {
            model Cow {
                name : Name inherits String
                `weight lbs` : WeightPounds inherits Decimal
                `weight:kgs` : WeightKilograms inherits Decimal
            }
        }
      """.trimIndent()

      val output = compileAndGenerate(taxi)
      val expected = """
        export namespace animals {
            export type NameType = string;
            export type Name = DatatypeContainer<NameType>;
            export type WeightPoundsType = number;
            export type WeightPounds = DatatypeContainer<WeightPoundsType>;
            export type WeightKilogramsType = number;
            export type WeightKilograms = DatatypeContainer<WeightKilogramsType>;
            export type Cow = DatatypeContainer<{
               readonly name: animals.NameType;
               readonly 'weight lbs': animals.WeightPoundsType;
               readonly 'weight:kgs': animals.WeightKilogramsType
            }>;
            export class Taxonomy {
                readonly Name: Name = buildDatatypeContainer('animals.Name', '');
                readonly WeightPounds: WeightPounds = buildDatatypeContainer('animals.WeightPounds', 0.0);
                readonly WeightKilograms: WeightKilograms = buildDatatypeContainer('animals.WeightKilograms', 0.0);
                readonly Cow: Cow = buildDatatypeContainer('animals.Cow', {
                    name: '',
                    'weight lbs': 0.0,
                    'weight:kgs': 0.0
                });
            }
        }
        export const taxonomy = { animals: { ...(new animals.Taxonomy()) } };
        """

      output.shouldEqualIgnoringHeaderAndWhitespace(expected)
   }

   @Test
   fun `generates'Any'TypedVariables`() {
      val taxi = """
        namespace animals {
            model Cow {
                `any type` : AnyType inherits Any
            }
        }
      """.trimIndent()

      val output = compileAndGenerate(taxi)
      val expected = """
        export namespace animals {
            export type AnyTypeType = any;
            export type AnyType = DatatypeContainer<AnyTypeType>;
            export type Cow = DatatypeContainer<{
               readonly 'any type': animals.AnyTypeType
            }>;
            export class Taxonomy {
                readonly AnyType: AnyType = buildDatatypeContainer('animals.AnyType', '');
                readonly Cow: Cow = buildDatatypeContainer('animals.Cow', {
                    'any type': ''
                });
            }
        }
        export const taxonomy = { animals: { ...(new animals.Taxonomy()) } };
        """
      output.shouldEqualIgnoringHeaderAndWhitespace(expected)
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
   fun modelsWithinSameNamespaceAreGeneratedCorrectly(){
      val taxi ="""
         namespace demos.esg {
             type EnvironmentalScore inherits Decimal
         }
         namespace refinitiv {
             model EsgScore {
                 e_score: demos.esg.EnvironmentalScore
             }
             model AssetInfo {
                 esgScores: EsgScore
             }
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi)
      val expected = """
export namespace demos.esg {
  export type EnvironmentalScoreType = number;
  export type EnvironmentalScore = DatatypeContainer<EnvironmentalScoreType>;

  export class Taxonomy {
    readonly EnvironmentalScore: EnvironmentalScore = buildDatatypeContainer(
      'demos.esg.EnvironmentalScore',
      0.0
    );
  }
}
export namespace refinitiv {
  export type EsgScore = DatatypeContainer<{
    readonly e_score: demos.esg.EnvironmentalScoreType
  }>;
  export type AssetInfo = DatatypeContainer<{
    readonly esgScores: refinitiv.EsgScoreType
  }>;
  export class Taxonomy {
    readonly EsgScore: EsgScore = buildDatatypeContainer('refinitiv.EsgScore', {
      e_score: 0.0
    });
    readonly AssetInfo: AssetInfo = buildDatatypeContainer(
      'refinitiv.AssetInfo',
      {
        esgScores: ''
      }
    );
  }
}
export const taxonomy = {
  demos: {
      esg: {
           ...(new demos.esg.Taxonomy())
      },
  },
   refinitiv: {
     ...(new refinitiv.Taxonomy())
  }
};
      """.removeWhitespace()
      output.shouldEqualIgnoringHeaderAndWhitespace(expected)
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

   @Test
   fun enumTypes() {
      val taxi = """
         namespace demos.Foo {
            type Person {
                gender : Gender
            }
            enum Gender {
                MALE,
                FEMALE
            }
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
         export namespace demos.Foo {
            export type Person = DatatypeContainer<{ readonly gender: demos.Foo.GenderType | null }>;
            export type GenderType = 'MALE' | 'FEMALE';
            export type Gender = DatatypeContainer<GenderType | null>;
            export class Taxonomy {
              readonly Person: Person = buildDatatypeContainer('demos.Foo.Person', {
                gender: null
              });
              readonly Gender: Gender = buildDatatypeContainer('demos.Foo.Gender', null);
            }
         }
         export const taxonomy = {
            demos: {
               Foo: {
                 ...(new demos.Foo.Taxonomy())
               },
            }
         };
      """.removeWhitespace()
      expect(output).to.equal(expected)
   }

   @Disabled("Not supported yet")
   @Test
   fun complexEnumTypes() {
      val taxi = """
         namespace demos.Foo {
            enum Country {
               NEW_ZEALAND("NZ"),
               AUSTRALIA("AUS"),
               UNITED_KINGDOM("UK")
            }
            enum Numbers {
               One(1),
               Two(2)
            }
            enum Mixed {
               One("One"),
               Two(2)    // Will be converted to string
            }
            enum Selected {
               Yes(true),
               No(false)
            }
            model ErrorDetails {
               code : ErrorCode inherits Int
               message : ErrorMessage inherits String
            }

            enum Errors<ErrorDetails> {
               BadRequest({ code : 400, message : 'Bad Request' }),
               Unauthorized({ code : 401, message : 'Unauthorized' })
            }
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader)//.removeWhitespace()
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
      val output = compileAndGenerate(taxi).substringAfter(staticHeader)
      val expected = """

      """.removeWhitespace()

      output.should.equal(expected)
   }

   @Test
   fun dateTypes() {
      val taxi = """
         type Date_1 inherits Time
         type Date_2 inherits Instant
         type Date_3 inherits Date
         @Format("dd-MM-yyyy")
         type InheritedDate inherits Date_3
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
         export type Date_1Type = Date;
         export type Date_1 = DatatypeContainer<Date_1Type>;
         export type Date_2Type = Date;
         export type Date_2 = DatatypeContainer<Date_2Type>;
         export type Date_3Type = Date;
         export type Date_3 = DatatypeContainer<Date_3Type>;
         export type InheritedDateType = Date;
         export type InheritedDate = DatatypeContainer<InheritedDateType>;

         export class Taxonomy {
           readonly Date_1: Date_1 = buildDatatypeContainer('Date_1', new Date());
           readonly Date_2: Date_2 = buildDatatypeContainer('Date_2', new Date());
           readonly Date_3: Date_3 = buildDatatypeContainer('Date_3', new Date());
           readonly InheritedDate: InheritedDate = buildDatatypeContainer('InheritedDate', new Date());
         }
         export const taxonomy = {
         ...(new Taxonomy())
         };
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

   @Test
   fun generateCommentsFromTypedocs() {
      val taxi = """
         namespace demos.Foo {
            [[how old in years the person is]]
            type Age inherits Int
            [[
            A person, a real live person!
            This is a multiline doc
            ]]
            model Person {
                [[field documentation]]
                gender : Gender
            }
            [[Gender enum doc]]
            enum Gender {
                [[an enum specific doc]]
                MALE,
                FEMALE
            }
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).substringAfter(staticHeader).removeWhitespace()
      val expected = """
         export namespace demos.Foo {
           // how old in years the person is
           export type AgeType = number;
           export type Age = DatatypeContainer<AgeType>;
           // A person, a real live person!
           // This is a multiline doc
           export type Person = DatatypeContainer<{
             // field documentation
             readonly gender: demos.Foo.GenderType | null
           }>;
           // Gender enum doc
           // an enum specific doc
           export type GenderType = 'MALE' | 'FEMALE';
           export type Gender = DatatypeContainer<GenderType | null>;
           export class Taxonomy {
             readonly Age: Age = buildDatatypeContainer('demos.Foo.Age', 0);
             readonly Person: Person = buildDatatypeContainer('demos.Foo.Person', {
               gender: null
             });
             readonly Gender: Gender = buildDatatypeContainer('demos.Foo.Gender', null);
           }
         }
         export const taxonomy = {
           demos: {
               Foo: {
                    ...(new demos.Foo.Taxonomy())
               },
           }
         };
      """.removeWhitespace()
      expect(output).to.equal(expected)
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
