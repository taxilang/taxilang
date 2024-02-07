package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import io.kotest.matchers.shouldBe
import lang.taxi.accessors.*
import lang.taxi.expressions.LiteralExpression
import lang.taxi.messages.Severity
import lang.taxi.types.*
import org.antlr.v4.runtime.CharStreams
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class GrammarTest {

   @Test
   fun canFindNamespaces() {
      val src = """namespace foo.bar.baz

type FooType {
   thing : SomeThing as String
}
      """
      Compiler(src).contextAt(3, 14)!!.findNamespace().should.equal("foo.bar.baz")
   }

   @Test
   fun `declaring the same field twice in a type should cause an error`() {
      val errors = """type Person {
         firstName : String
         lastName : String
         firstName : String
         }
      """.validated()
      errors.filter { it.severity == Severity.ERROR }.should.have.size(2)
      // 2 errors - an error is captured for both the fields
      errors[0].detailMessage.should.equal("Field firstName is declared multiple times")
      errors[1].detailMessage.should.equal("Field firstName is declared multiple times")
   }

   @Test
   fun callingFindNamespaceWithoutANamespaceReturnsDefaultNamespace() {
      val src = """type FooType {
   thing : SomeThing as String
}
      """
      Compiler(src).contextAt(1, 14)!!.findNamespace().should.equal(Namespaces.DEFAULT_NAMESPACE)
   }


   @Test
   fun canFindNamespaceInMultiNamespaceDocument() {
      val src = """namespace foo.bar {
   type FooType
}

namespace foo.baz {
   type BazType
}
      """.trimIndent()
      val compiler = Compiler(src)
      compiler.contextAt(1, 11)!!.findNamespace().should.equal("foo.bar")
      compiler.contextAt(5, 15)!!.findNamespace().should.equal("foo.baz")
   }

   @Test
   fun returnsEmptyListWhenNoImportsInFile() {
      val src = """type FooType {
   thing : SomeThing as String
}
      """
      val compiler = Compiler(src)
      compiler.contextAt(0, 28)!!.importsInFile().should.be.empty
   }

   @Test
   fun itIsInvalidToDeclareTwoNamespaceElementsWithoutUsingBlocks() {
      val source = """
namespace foo
type FooType {}

namespace bar
type BarType {}
"""
      assertThrows<CompilationException> {
         Compiler(source).compile()
      }
   }

   @Test
   fun canLeverageNamespaceBlocksWithinDocument() {
      val source = """
namespace foo {
    type FooType {}
}
namespace bar {
    type BarType {}
}"""
      val doc = Compiler(source).compile()
      expect(doc.objectType("foo.FooType")).not.to.be.`null`
      expect(doc.objectType("bar.BarType")).not.to.be.`null`
   }

   @Test
   fun canParseSimpleDocument() {
      val doc = Compiler(testResource("simpleType.taxi")).compile()
//        expect(doc.namespace).to.equal("lang.taxi")
      val personType = doc.objectType("lang.taxi.Person")
      expect(personType.field("firstName").type).to.equal(PrimitiveType.STRING)
      expect(personType.field("firstName").nullable).to.be.`false`
      expect(personType.field("title").nullable).to.be.`true`
      expect(personType.field("friends").type).to.be.instanceof(ArrayType::class.java)

      // Arrays
      val friendArray = personType.field("friends").type as ArrayType
      expect(friendArray.type).to.equal(personType)

      // Type annotations
      expect(personType.annotations).to.have.size(2)
      expect(personType.annotation("MyTypeAnnotation")).not.to.be.`null`
      val annotationWithParams = personType.annotation("MyTypeWithParams")
      expect(annotationWithParams).not.to.be.`null`
      expect(annotationWithParams.parameters).to.have.size(1)

      // Enums
      val enumType = doc.enumType("lang.taxi.Gender")
      expect(enumType.annotations).to.have.size(1)
      expect(enumType.values).to.have.size(2)
      expect(enumType.value("Male").annotations).to.have.size(1)
   }

   @Test
   fun arraysAreParsedInBothShorthandAndLonghand() {
      val foo = """
         type Name inherits String
         model Foo {
            nickNames: Name[]
            petNames : Array<Name>
            hatedNames: lang.taxi.Array<Name>
         }
      """.compiled()
         .model("Foo")
      foo.field("nickNames").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Name>")
      foo.field("petNames").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Name>")
      foo.field("hatedNames").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Name>")
   }

   @Test
   fun arraysAReParsedWhenImported() {
      val foo = """
         import lang.taxi.Array

         type Name inherits String
         model Foo {
            nickNames: Name[]
            petNames : Array<Name>
            hatedNames: lang.taxi.Array<Name>
         }
      """.compiled()
         .model("Foo")
      foo.field("nickNames").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Name>")
      foo.field("petNames").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Name>")
      foo.field("hatedNames").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Name>")
   }

   @Disabled("Need to make this work consistently. See TokenCollator:collectDuplicateTypes for detail")
   @Test
   fun given_typeIsRedeclaredWithSemanticallyEquivalentDefinition_then_itIsInValid() {
      val source1 = """
namespace foo {
    type Person {
        firstName : FirstName as String
        lastName : LastName as String
    }
}"""
      val source2 = """
namespace foo {
    // type alias FirstName as String
    // type alias LastName as String
    type Person {
        lastName : LastName as String
        firstName : FirstName as String
    }
}
            """
      assertThrows<CompilationException> {
         Compiler(listOf(CharStreams.fromString(source1, "source1"), CharStreams.fromString(source2, "source2"))).compile()
      }
   }

   @Test
   @Disabled("Need to make this work consistently. See TokenCollator:collectDuplicateTypes for detail")
   fun given_typeIsRedeclaredWithDifferentDefinition_then_exceptionIsThrown() {
      val source1 = """
namespace foo {
    type Person {
        firstName : FirstName as String
        lastName : LastName as String
    }
}"""
      val source2 = """
namespace foo {
    type Person {
        age : Int
        firstName : FirstName as String
        lastName : LastName as String

    }
}
            """

      assertThrows<CompilationException> {
         Compiler(listOf(CharStreams.fromString(source1, "source1"), CharStreams.fromString(source2, "source2"))).compile()
      }
   }

   @Test
   fun canHaveMultipleAnnotationsOnType() {
      val source = """
@StringAnnotation(value = "foo")
@SomeAnnotation(value = "bar")
type Test {
}
"""
      val doc = Compiler(source).compile()
      val type = doc.objectType("Test")
      expect(type.annotation("StringAnnotation").parameter("value")).to.equal("foo")
      expect(type.annotation("SomeAnnotation").parameter("value")).to.equal("bar")
   }

   @Test
   @Disabled("https://gitlab.com/vyne/taxi-lang/issues/7")
   fun annotationCanHaveBooleanArgument() {
      val doc = Compiler("@Bool(value = false) type Test {}").compile()
      val type = doc.objectType("Test")
      expect(type.annotation("Bool").parameter("value")).to.equal(false)
   }

   @Test
   fun annotationCanHaveNumericArgument() {
      val doc = Compiler("@Numeric(value = 96000) type Test {}").compile()
      val type = doc.objectType("Test")
      expect(type.annotation("Numeric").parameter("value")).to.equal(96000)
   }

   @Test
   fun canDeclareAnnotationWithoutParenthesis() {
      val source = """
            @Hello
            type Test {}
        """.trimIndent()
      val doc = Compiler(source).compile()
      val type = doc.objectType("Test")
      expect(type.annotation("Hello")).to.be.not.`null`
      expect(type.annotation("Hello").parameters).to.be.empty
   }

   @Test
   fun canReferenceTypeBeforeItIsDeclared() {
      val source = """
type Person {
   email : Email
}
type Email {
   value : String
}
"""
      val doc = Compiler(source).compile()
      val email = doc.objectType("Person").field("email")
      expect(email.type).to.equal(doc.objectType("Email"))
   }

   @Test
   fun canDeclareTypeWithoutBody() {
      val src = """
type Person
      """.trimIndent()
      val doc = Compiler(src).compile()
      doc.objectType("Person").fields.should.have.size(0)
   }

   @Test
   fun canDeclareAnnotationsOnFields() {
      val source = """
type Person {
   @SomeAnnotation(foo = "bar")
   @Test
   email : String
}
"""
      val doc = Compiler(source).compile()
      expect(doc.objectType("Person").field("email").annotations).size.to.equal(2)
   }

   @Test
   fun canDeclareTypeAlias() {
      val source = """
         type GivenName inherits String
@SomeAnnotation
type alias PersonName as GivenName
type Person {
    name : PersonName
}"""
      val doc = Compiler(source).compile()
      expect(doc.type("PersonName")).to.be.instanceof(TypeAlias::class.java)
      val personName = doc.objectType("Person").field("name").type as TypeAlias
      expect(personName).to.be.instanceof(TypeAlias::class.java)
      expect(personName.aliasType!!.qualifiedName).to.equal("GivenName")
      expect(personName.annotations).to.have.size(1)
   }

   @Test
   fun throwsExceptionOnUnresolvedType() {
      val source = """
type Foo {
   bar : Bar
}
"""
      val exception = assertThrows<CompilationException> {
         Compiler(source).compile()
      }
      expect(exception.message).to.contain(ErrorMessages.unresolvedType("Bar"))
   }

   @Test
   fun canGetTokensAtPositions() {
      val source = """namespace taxi.example

type Person {
   name : PersonName as String
}
      """.trim()
      val compiler = Compiler(source)
      compiler.contextAt(0, 2)?.start?.text.should.equal("namespace")
      compiler.contextAt(3, 6)?.start?.text.should.equal("name")
   }


   @Test
   fun canDetectParameterTypes() {
      val source = """
parameter type ClientRiskRequest {
   amount : Amount inherits Decimal
}"""
      val doc = Compiler(source).compile()
      val money = doc.objectType("ClientRiskRequest")
      expect(money.modifiers).to.contain(Modifier.PARAMETER_TYPE)
   }

   @Test
   fun errorsIncludeTheSourceWithSpaces() {
      val source = """import foo.internal.Blah"""
      val errors = Compiler(source).validate()
      errors.should.have.size(1)
   }

   @Test
   fun canExtendAnotherType() {
      val source = """
type TypeA {
    fieldA : String
}
type TypeB inherits TypeA {
    fieldB : String
}
type TypeC inherits TypeB {
    fieldC : String
}

// TypeD inherits TypeA and TypeB.  TypeB also inherits TypeA.
// this is intentional for this test
type TypeD inherits TypeA, TypeB {}
        """.trimIndent()

      val doc = Compiler(source).compile()
      val typeB = doc.objectType("TypeB")

      expect(typeB.inheritsFromNames).to.contain("TypeA")
      expect(typeB.fields).to.have.size(1)
      expect(typeB.allFields).to.have.size(2)
      expect(typeB.field("fieldA")).to.be.not.`null`
      expect(typeB.field("fieldB")).to.be.not.`null`

      val typeD = doc.objectType("TypeD")
      expect(typeD.allFields).to.have.size(2)
      expect(typeD.field("fieldA")).to.be.not.`null`
      expect(typeD.field("fieldB")).to.be.not.`null`

   }

   @Test
   fun whenExtendingAnotherType_itIsInvalidToRedeclareTheSameProperty() {
      // TODO
   }

   @Test
   fun canDeclareSomethingAsAnyType() {
      val source = """
            type Foo {
                something : Any
            }
        """.trimIndent()
      val taxi = Compiler(source).compile()
      val somethingField = taxi.objectType("Foo").field("something")
      expect(somethingField).to.be.not.`null`
      expect(somethingField.type).to.equal(PrimitiveType.ANY)
   }

   @Test
   fun canDeclareAPropertyWithAReservedWord() {
      val source = """
type ApiResponse {
  `type` : String
  message : String
}
        """.trimIndent()
      val taxi = Compiler(source).compile()
      expect(taxi.objectType("ApiResponse").field("type")).to.be.not.`null`
      expect(taxi.objectType("ApiResponse").field("message")).to.be.not.`null`
   }


   private fun testResource(s: String): File {
      return File(this.javaClass.classLoader.getResource(s).toURI())
   }

   @Test
   fun canHaveCircularReferencesInTypes() {
      val source = """
type Person {
   home : Home
}
type Home {
   owner : Person
 }
      """.trimIndent()
      val doc = Compiler(source).compile()
      doc.objectType("Person").field("home").type.qualifiedName.should.equal("Home")
      doc.objectType("Home").field("owner").type.qualifiedName.should.equal("Person")
   }

   @Test
   fun canListDeclaredTypeNamesInSrcFile() {
      val sourceA = """
namespace test {
    type alias FirstName as String
    type Person {
        firstName : FirstName
        lastName : LastName inherits String
        nickname : String
    }

    type Book {
        author : Person
    }
}
        """.trimIndent()

      val typeNames = Compiler(sourceA).declaredTypeNames()
      expect(typeNames).to.have.size(4)
      expect(typeNames).to.contain(QualifiedName.from("test.FirstName"))
      expect(typeNames).to.contain(QualifiedName.from("test.LastName"))
      expect(typeNames).to.contain(QualifiedName.from("test.Book"))
      expect(typeNames).to.contain(QualifiedName.from("test.Person"))
   }

   @Test
   fun canListImports() {
      val sourceB = """
import test.FirstName

namespace foo {
    type Customer {
        name : test.FirstName
    }
}
        """.trimIndent()
      val imports = Compiler(sourceB).declaredImports()
      expect(imports).to.have.size(1)
      expect(imports).to.contain(QualifiedName.from("test.FirstName"))
   }

   @Test
   fun canHaveImportsAcrossSources() {
      val sourceA = """
import acme.places.Home

namespace acme.people

type Person {
   home : Home
}
      """.trimIndent()
      val sourceB = """
import acme.people.Person

namespace acme.places

type Home {
   owner : Person
}
      """.trimIndent()
      val doc = Compiler(listOf(
         CharStreams.fromString(sourceA, "sourceA"),
         CharStreams.fromString(sourceB, "sourceB")
      )).compile()
      doc.objectType("acme.people.Person").field("home").type.qualifiedName.should.equal("acme.places.Home")
      doc.objectType("acme.places.Home").field("owner").type.qualifiedName.should.equal("acme.people.Person")
   }

   @Test
   fun given_aTypeDoesNotDeclareClosed_then_itDoesNotHaveTheClosedModifier() {
      val src = """
type Money {
    amount : MoneyAmount inherits Decimal
    currency : Currency inherits String
}
        """.trimIndent()
      val taxi = Compiler(src).compile()
      expect(taxi.objectType("Money").modifiers).to.be.empty
   }

   @Test
   fun canDeclareATypeAsClosed() {
      val src = """
closed type Money {
    amount : MoneyAmount inherits Decimal
    currency : Currency inherits String
}
        """.trimIndent()
      val taxi = Compiler(src).compile()
      val money = taxi.objectType("Money")
      expect(money.modifiers).to.have.size(1)
      expect(money.modifiers).to.contain(Modifier.CLOSED)
   }

   @Test
   fun typeCanHaveMultipleModifiers() {
      val src = """
   parameter closed type Foo {
      name : String
   }

        """.trimIndent()
      val taxi = Compiler(src).compile()

      val type = taxi.objectType("Foo")
      expect(type.modifiers).to.have.size(2)
      expect(type.modifiers).to.contain.elements(Modifier.PARAMETER_TYPE, Modifier.CLOSED)

   }

   @Test
   fun canDeclareAPropertyAsClosed() {
      val src = """
type Trade {
   trader : UserId inherits String
   closed settlementCurrency : Currency inherits String
}
        """.trimIndent()
      val taxi = Compiler(src).compile()
      val trade = taxi.objectType("Trade")
      expect(trade.field("settlementCurrency").modifiers).to.contain(FieldModifier.CLOSED)
   }

   @Test
   fun canDeclareAnXpathAccessor() {
      val src = """
type Instrument inherits String
type LegacyTradeNotification {
   instrument : Instrument by xpath("/some/xpath")
}
        """.trimIndent()
      val taxi = Compiler(src).compile()
      val notification = taxi.objectType("LegacyTradeNotification")
      val field = notification.field("instrument")
      val accessor = field.accessor as XpathAccessor
      expect(accessor.expression).to.equal("/some/xpath")
   }

   @Test
   fun `can list service names from source`() {
      val src = """
         namespace foo.test

         service MyService {
         // Person doesn't exist, so this can't compile.
         // But we should still be able to list the names of the service
            operation findPeople():Person[]
         }
      """.trimIndent()
      val compiler = Compiler(src)
      compiler.declaredServiceNames().should.have.size(1)
      compiler.declaredServiceNames().single().fullyQualifiedName.should.equal("foo.test.MyService")
   }

   @Test
   fun canDeclareAJsonPAthAccessor() {
      val src = """
type Instrument inherits String
type LegacyTradeNotification {
   instrument : Instrument by jsonPath("$.foo[bar]")
}
        """.trimIndent()
      val taxi = Compiler(src).compile()
      val notification = taxi.objectType("LegacyTradeNotification")
      val field = notification.field("instrument")
      val accessor = field.accessor as JsonPathAccessor
      expect(accessor.expression).to.equal("$.foo[bar]")
   }

   @Test
   fun canDeclareTypeWithColumnMappingsAndThenUseAsASource() {
      val src = """
declare function concat(String...):String
declare function leftAndUpperCase(String,String):String
declare function midAndUpperCase(String,String):String
type FirstName inherits String
type LastName inherits String
type Person {
   firstName : FirstName by column(0)
   lastName : LastName by column(1)
   title: String = "Mr."
   age: Int  = 18
}

      """.trimIndent()
      val taxi = Compiler(src).compile()
      val person = taxi.objectType("Person")
      val firstName = person.field("firstName")
      val title = person.field("title")
      val age = person.field("age")
      firstName.accessor.should.be.instanceof(ColumnAccessor::class.java)
      title.accessor.should.be.instanceof(LiteralExpression::class.java)
      age.accessor.should.be.instanceof(LiteralExpression::class.java)
   }

   @Test
   fun when_unresolvedTypeExistsInFileWithNamespace_then_namespaceIsNotPrefixedInError() {

   }

   @Test
   fun reportsMultipleUnresolvedTypes() {
      val src = """
type Person {
   firstName : FirstName
   lastName : LastName
}
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.filter { it.severity == Severity.ERROR }.should.have.size(2)
   }

   @Test
   fun `explicitly qualified function return parameter is resolved`() {
      val sourceA = """
namespace test {
    type FirstName inherits String
}
        """.trimIndent()
      val schemaA = Compiler(sourceA).compile()
      val sourceB = """

namespace foo {
    service CustomerService {
        operation getName() : test.FirstName
    }
}
        """.trimIndent()

      val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
      expect(schemaB.containsService("foo.CustomerService")).to.be.`true`
   }

   @Test
   fun `not explicitly qualified function return param with an import is resolved`() {
      val sourceA = """
namespace test {
    type FirstName inherits String
}
        """.trimIndent()
      val schemaA = Compiler(sourceA).compile()
      val sourceB = """
import test.FirstName

namespace foo {
    service CustomerService {
        operation getName() : FirstName
    }
}
        """.trimIndent()

      val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
      expect(schemaB.containsService("foo.CustomerService")).to.be.`true`
   }

   @Test
   fun `not explicitly qualified function return parameter is resolved as it is`() {
      val sourceA = """
    type FirstName inherits String
        """.trimIndent()
      val schemaA = Compiler(sourceA).compile()
      val sourceB = """
namespace foo {
    service CustomerService {
        operation getName() : FirstName
    }
}
        """.trimIndent()

      val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
      expect(schemaB.containsService("foo.CustomerService")).to.be.`true`
   }


   @Test
   fun `a type cannot inherit itself`() {
      val (messages,_) = Compiler("""model Person inherits Person""").compileWithMessages()
      messages.should.have.size(1)
      messages.first().detailMessage.should.equal("Person cannot inherit from itself")
   }

   @Test
   fun `a type cannot create an inheritence loop`() {
      val (messages,_) = Compiler("""
         model Human inherits Person

         model Person inherits Human
         """.trimIndent()).compileWithMessages()
      messages.map { it.detailMessage }
         .should.contain.elements("Person contains a loop in it's inheritance.  Check the inheritance of the following types: Human")
   }

   @Test
   fun `types declared inline are reported as declared type names`() {
      val compiler = lang.taxi.Compiler("""model Person {
         | personId : PersonId inherits String
         |}
      """.trimMargin())
      val declaredTypeNames  = compiler.declaredTypeNames()
      declaredTypeNames.should.have.size(2)
      declaredTypeNames.map { it.typeName }.should.contain.elements("Person", "PersonId")
   }

   @Test
   fun `a type that inherits normally does not trigger an inheritance loop error`() {
      val (messages,_) = Compiler("""
      type Name inherits String
      type FirstName inherits Name
      """).compileWithMessages()
      messages.should.be.empty
   }

   @Test
   fun `compilation units with namespacs and imports are correct`() {
      val srcA = """
         import foo.bar.Person
         namespace baz {
            model Film {}
            query MyQuery { find { Person[] } }
         }
      """
      val srcB = """namespace foo.bar
         |
         |model Person {}
      """.trimMargin()
      val sources = listOf(srcA,srcB).map { CharStreams.fromString(it) }
      val query = Compiler(sources)
         .compile()
         .query("baz.MyQuery")


      query.compilationUnits.single().source.content.shouldBe("""import foo.bar.Person
namespace baz {
   query MyQuery { find { Person[] } }
}""")
   }
}








