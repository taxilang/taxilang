package lang.taxi.generators.typescript

import com.winterbe.expekt.expect
import lang.taxi.Compiler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class GeneratorProcessorTest {
   @Test
   fun typeProcessorInInvoked() {
      val taxi = """
model Person {
   firstName : FirstName inherits String
   lastName : LastName inherits String
   email : Email inherits String
   age : Age inherits Int
   address : Address inherits String
}
        """.trimIndent()

      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """type FirstNameType = string;
type LastNameType = string;
type AgeType = number;
type EmailType = string;
type AddressType = string;

type FirstName = DatatypeContainer<FirstNameType>;
type LastName = DatatypeContainer<LastNameType>;
type Age = DatatypeContainer<AgeType>;
type Email = DatatypeContainer<EmailType>;
type Address = DatatypeContainer<AddressType>;

type Person = DatatypeContainer<{ readonly FirstName: FirstName; readonly LastName: LastName, readonly Age: Age }>;

export type TaxonomyStructure = { readonly FirstName: FirstName; readonly LastName: LastName; readonly Email: Email, readonly Address: Address, readonly Age: Age };

export class Taxonomy implements TaxonomyType<TaxonomyStructure> {
  @Datatype('FirstName')
  readonly FirstName: FirstName = buildDatatypeContainer('FirstName', '');

  @Datatype('LastName')
  readonly LastName: LastName = buildDatatypeContainer('LastName', '');

  @Datatype('Email')
  readonly Email: Email = buildDatatypeContainer('Email', '');

  @Datatype('Address')
  readonly Address: Address = buildDatatypeContainer('Address', '');

  @Datatype('Age')
  readonly Age: Age = buildDatatypeContainer('Age', 0);

  @Datatype('Person')
  readonly Person: Person = buildDatatypeContainer('Person', {
    FirstName: this.FirstName,
    LastName: this.LastName,
    Age: this.Age
  });
}""".trimNewLines()

      expect(output).to.equal(expected)

   }

   private fun compileAndGenerate(taxi: String): String {
      val taxiDoc = Compiler.forStrings(taxi).compile()
      val output = TypeScriptGenerator().generate(taxiDoc, emptyList(), mock { })
      return output.joinToString("\n") { it.content }
   }
}
