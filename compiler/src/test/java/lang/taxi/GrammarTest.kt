package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.types.*
import org.antlr.v4.runtime.CharStreams
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File
import kotlin.test.fail

class GrammarTest {

   @Rule
   @JvmField
   val expectedException = ExpectedException.none()

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
   fun returnsListOfImportsInFile() {
      val src = """import foo.bar.baz
import fuzz.bizz.boz

type FooType
      """.trimIndent()
      val compiler = Compiler(src)
      compiler.contextAt(3, 9)!!.importsInFile().should.contain.elements(
         QualifiedName.from("foo.bar.baz"),
         QualifiedName.from("fuzz.bizz.boz")
      )
   }

   @Test
   fun importsInMultinamespaceDocumentAreDetectedCorrectly() {
      val src = """import foo.bar.baz
import fuzz.bizz.boz

namespace Blah {
   type FooType
}

namespace Blurg {
   type FuzzType
}
      """.trimIndent()
      val compiler = Compiler(src)
      compiler.contextAt(4, 10)!!.importsInFile().should.contain.elements(
         QualifiedName.from("foo.bar.baz"),
         QualifiedName.from("fuzz.bizz.boz")
      )
      compiler.contextAt(8,14)!!.importsInFile().should.contain.elements(
         QualifiedName.from("foo.bar.baz"),
         QualifiedName.from("fuzz.bizz.boz")
      )
   }

   @Test
   fun canParsePetstore() {
      val doc = Compiler(testResource("petstore.taxi")).compile()
   }

   @Test(expected = CompilationException::class)
   fun itIsInvalidToDeclareTwoNamespaceElementsWithoutUsingBlocks() {
      val source = """
namespace foo
type FooType {}

namespace bar
type BarType {}
"""
      val doc = Compiler(source).compile()
      expect(doc).to.be.`null`
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
   fun given_typeIsRedeclaredWithSemanticallyEquivalentDefinition_then_itIsValid() {
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
      Compiler(listOf(CharStreams.fromString(source1, "source1"), CharStreams.fromString(source2, "source2"))).compile()
   }

   @Test
   @Ignore("Detection of redecalred types is disabled, as was buggy and didn't respect imports")
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

      expectedException.expect(CompilationException::class.java)
      Compiler(listOf(CharStreams.fromString(source1, "source1"), CharStreams.fromString(source2, "source2"))).compile()
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
      expect(type.annotation("StringAnnotation").parameters["value"]).to.equal("foo")
      expect(type.annotation("SomeAnnotation").parameters["value"]).to.equal("bar")
   }

   @Test
   @Ignore("https://gitlab.com/vyne/taxi-lang/issues/7")
   fun annotationCanHaveBooleanArgument() {
      val doc = Compiler("@Bool(value = false) type Test {}").compile()
      val type = doc.objectType("Test")
      expect(type.annotation("Bool").parameters["value"]).to.equal(false)
   }

   @Test
   fun annotationCanHaveNumericArgument() {
      val doc = Compiler("@Numeric(value = 96000) type Test {}").compile()
      val type = doc.objectType("Test")
      expect(type.annotation("Numeric").parameters["value"]).to.equal(96000)
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
@SomeAnnotation
type alias PersonName as String
type Person {
    name : PersonName
}"""
      val doc = Compiler(source).compile()
      expect(doc.type("PersonName")).to.be.instanceof(TypeAlias::class.java)
      val personName = doc.objectType("Person").field("name").type as TypeAlias
      expect(personName).to.be.instanceof(TypeAlias::class.java)
      expect(personName.aliasType).to.equal(PrimitiveType.STRING)
      expect(personName.annotations).to.have.size(1)
   }

   @Test
   fun canDeclareInlineTypeAlias() {
      val source = """
type Person {
    // Declaring an inline type alias -- same as 'type alias PersonName as String'
    name : PersonName as String
}"""
      val doc = Compiler(source).compile()
      expect(doc.type("PersonName")).to.be.instanceof(TypeAlias::class.java)
      val personName = doc.objectType("Person").field("name").type as TypeAlias
      expect(personName).to.be.instanceof(TypeAlias::class.java)
      expect(personName.aliasType).to.equal(PrimitiveType.STRING)
      expect(personName.annotations).to.have.size(0)
   }

   @Test
   fun canReferenceAnInlineTypeAliasBeforeItIsDeclared() {
      val source = """
type Friend {
   friendName : PersonName
}
type Person {
    // Declaring an inline type alias -- same as 'type alias PersonName as String'
    name : PersonName as String
}"""
      val doc = Compiler(source).compile()
      expect(doc.type("PersonName")).to.be.instanceof(TypeAlias::class.java)
      expect(doc.objectType("Friend").field("friendName").type).to.be.instanceof(TypeAlias::class.java)
   }

   @Test
   fun throwsExceptionOnUnresolvedType() {
      expectedException.expect(CompilationException::class.java)
      expectedException.expectMessage(ErrorMessages.unresolvedType("Bar"))
      val source = """
type Foo {
   bar : Bar
}
"""
      Compiler(source).compile()
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
   fun canCompileWhenUsingFullyQualifiedNames() {
      val source = """
namespace taxi.example
type Invoice {
    clientId : taxi.example.ClientId
    invoiceValue : taxi.example.InvoiceValue
    previousInvoice : taxi.example.Invoice
}

// Note : Fully qualified primitives not currently working.
// https://gitlab.com/osmosis-platform/taxi-lang/issues/5
//type alias ClientId as lang.taxi.String
//type alias InvoiceValue as lang.taxi.Decimal

type alias ClientId as String
type alias InvoiceValue as Decimal
"""
      val doc = Compiler(source).compile()
      val invoice = doc.objectType("taxi.example.Invoice")
      expect(invoice.field("clientId").type).to.be.instanceof(TypeAlias::class.java)
      val typeAlias = invoice.field("clientId").type as TypeAlias
      expect(typeAlias.aliasType).to.equal(PrimitiveType.STRING)
   }

   @Test
   fun canDeclareConstraintsOnTypes() {
      val source = """
type Money {
   amount : Amount as Decimal
   currency : Currency as String
}
type SomeServiceRequest {
   amount : Money(this.currency = 'GBP')
   clientId : ClientId as String
}
"""
      val doc = Compiler(source).compile()
      val request = doc.objectType("SomeServiceRequest")

      val amountField = request.field("amount")
      expect(amountField.constraints).to.have.size(1)
      expect(amountField.constraints[0]).to.be.instanceof(PropertyToParameterConstraint::class.java)
   }

   @Test
   fun canDetectParameterTypes() {
      val source = """
parameter type ClientRiskRequest {
   amount : Amount as Decimal
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
   fun canImportTypeFromAnotherSchema() {
      val sourceA = """
namespace test {
    type alias FirstName as String
}
        """.trimIndent()
      val schemaA = Compiler(sourceA).compile()
      val sourceB = """
import test.FirstName

namespace foo {
    type Customer {
        name : test.FirstName
    }
}
        """.trimIndent()

      val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
      val customer = schemaB.type("foo.Customer") as ObjectType
      expect(customer.field("name").type.qualifiedName).to.equal("test.FirstName")
   }

   // Not sure if this is a good idea or not, that imported types
   // become present in the final document
   // May need to split these to distinguish between declaredTypes and importedTypes.
   // However, at the moment, primitives are present in the types collection, which indicates
   // that may not be needed.
   @Test
   fun importedTypesArePresentInTheDoc() {
      val sourceA = """
namespace test {
    type alias FirstName as String
}
        """.trimIndent()
      val schemaA = Compiler(sourceA).compile()
      val sourceB = """
import test.FirstName

namespace foo {
    type Customer {
        name : test.FirstName
    }
}
        """.trimIndent()

      val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
      expect(schemaB.containsType("test.FirstName")).to.be.`true`
   }

   @Test
   fun when_importedTypeReferencesOtherTypes_then_thoseTypesAreAlsoPresentInTheDdoc() {
      val srcA = """
namespace foo

type Customer {
    name : FirstName as String
}
        """.trimIndent()
      val schemaA = Compiler(srcA).compile()

      val srcB = """
import foo.Customer

type Thing {
    customer : foo.Customer
}
        """.trimIndent()
      val schemaB = Compiler(srcB, importSources = listOf(schemaA)).compile()
      expect(schemaB.containsType("foo.Customer")).to.be.`true`
      expect(schemaB.containsType("foo.FirstName")).to.be.`true`
   }

   @Test
   fun cannotImportTypeThatDoesntExist() {
      val sourceB = """
import test.FirstName

namespace foo {
    type Customer {
        name : test.FirstName
    }
}
        """.trimIndent()
      val errors = Compiler(sourceB).validate()
      expect(errors).to.have.size(2)
      expect(errors.first().detailMessage).to.equal("Cannot import test.FirstName as it is not defined")
   }

   @Test
   fun when_twoTypesExistButOneIsExplicitlyImportedThenTypeResolutionIsUnambiguous() {
      val sourceA = """
namespace foo {
   type alias Name as String
}

namespace bar {
   type alias Name as String
}
      """.trimIndent()
      val sourceB = """
import foo.Name
namespace car {
   type Person {
      name : Name
   }
}
      """.trimIndent()
      val schemaA = Compiler(sourceA).compile()
      val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
      schemaB.objectType("car.Person").field("name").type.qualifiedName.should.equal("foo.Name")
   }

   @Test
   fun `inline type alias can have same name as type in another package`() {
      val sourceA = """
namespace foo {
   type alias Name as String
}
      """.trimIndent()
      val sourceB = """
namespace baz {
   type Person {
      name : Name as String
   }
}
      """.trimIndent()
      val schemaA = Compiler(sourceA).compile()
      val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
      schemaB.objectType("baz.Person").field("name").type.qualifiedName.should.equal("baz.Name")
   }
   @Test
   fun canListDeclaredTypeNamesInSrcFile() {
      val sourceA = """
namespace test {
    type alias FirstName as String
    type Person {
        firstName : FirstName
        lastName : LastName as String
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
    amount : MoneyAmount as Decimal
    currency : Currency as String
}
        """.trimIndent()
      val taxi = Compiler(src).compile()
      expect(taxi.objectType("Money").modifiers).to.be.empty
   }

   @Test
   fun canDeclareATypeAsClosed() {
      val src = """
closed type Money {
    amount : MoneyAmount as Decimal
    currency : Currency as String
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
   trader : UserId as String
   closed settlementCurrency : Currency as String
}
        """.trimIndent()
      val taxi = Compiler(src).compile()
      val trade = taxi.objectType("Trade")
      expect(trade.field("settlementCurrency").modifiers).to.contain(FieldModifier.CLOSED)
   }

   @Test
   fun canDeclareAnXpathAccessor() {
      val src = """
type alias Instrument as String
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
   fun canDeclareAJsonPAthAccessor() {
      val src = """
type alias Instrument as String
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
   fun canDeclareAccessorsOnObjectTypes() {
      val src = """
type Money {
   amount : MoneyAmount as Decimal
   currency : Currency as String
}
type alias Instrument as String
type NearLegNotional inherits Money {}
type FarLegNotional inherits Money {}

type LegacyTradeNotification {
   instrument : Instrument by xpath("/some/xpath")
   nearLegNotional : NearLegNotional {
       amount by xpath("/legs[0]/amount")
       currency by xpath("/legs[0]/currency")
   }
   farLegNotional : FarLegNotional {
       amount by xpath("/legs[1]/amount")
       currency by xpath("/legs[1]/currency")
   }
}
        """.trimIndent()
      val taxi = Compiler(src).compile()
      val notification = taxi.objectType("LegacyTradeNotification")
      val field = notification.field("nearLegNotional")
      val accessor = field.accessor as DestructuredAccessor
      val fieldAccessor = accessor.fields["amount"] as XpathAccessor
      expect(fieldAccessor.expression).to.equal("/legs[0]/amount")
   }

   @Test
   @Ignore("Not implemented - https://gitlab.com/taxi-lang/taxi-lang/issues/22")
   fun destructuredAccessorsCannotDeclareInvalidPropertyNames() {
      val src = """
type Money {
   amount : MoneyAmount as Decimal
   currency : Currency as String
}

type LegacyTradeNotification {
   nearLegNotional : Money {
       // value isn't valid
       value by xpath("/legs[0]/amount")
       currency by xpath("/legs[0]/currency")
   }
}
"""
      try {
         val taxi = Compiler(src).compile()
         fail("Expected compilation exception")
      } catch (e: Exception) {
         TODO()
      }
   }

   @Test
   @Ignore("Not implemented - https://gitlab.com/taxi-lang/taxi-lang/issues/22")
   fun destructuredAccessorsCanOmitOptionalProperties() {
      val src = """
type Money {
   amount : MoneyAmount? as Decimal
   currency : Currency as String
}

type LegacyTradeNotification {
   nearLegNotional : Money {
       currency by xpath("/legs[0]/currency")
   }
}
"""
      val taxi = Compiler(src).compile()
      TODO()
   }

   @Test
   @Ignore("Not implemented - https://gitlab.com/taxi-lang/taxi-lang/issues/22")
   fun destructuredAccessorsCannotOmitNonNullProperties() {
      val src = """
type Money {
   amount : MoneyAmount as Decimal
   currency : Currency as String
}

type LegacyTradeNotification {
   nearLegNotional : Money {
       currency by xpath("/legs[0]/currency")
   }
}
"""
      try {
         val taxi = Compiler(src).compile()
         fail("Expected compilation exception")
      } catch (e: Exception) {
         TODO()
      }
   }

   @Test
   fun canDeclareColumnsWithNames() {
      val src = """
type Person {
   firstName : FirstName as String
   lastName : LastName as String
   }

fileResource(path = "/some/file/location", format = "csv") DirectoryOfPerson provides rowsOf Person {
   firstName by column("firstName")
   lastName by column(2)
}
      """.trimIndent()
      val taxi = Compiler(src).compile()
      val dataSource = taxi.dataSource("DirectoryOfPerson") as FileDataSource
      dataSource.returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Person>")
      dataSource.mappings.should.have.size(2)

      dataSource.mappings[0].propertyName.should.equal("firstName")
      expect(dataSource.mappings[0].index is String)
      dataSource.mappings[0].index.should.equal("firstName")
      dataSource.mappings[1].propertyName.should.equal("lastName")
      expect(dataSource.mappings[0].index is Int)
      dataSource.mappings[1].index.should.equal(2)
   }

   @Test(expected=CompilationException::class)
   fun cannotDeclareColumnsWithoutIndex() {
      val src = """
type Person {
   firstName : FirstName as String
   lastName : LastName as String
   }

fileResource(path = "/some/file/location", format = "csv") DirectoryOfPerson provides rowsOf Person {
   firstName by column()
   lastName by column()
}
      """.trimIndent()
      val taxi = Compiler(src).compile()
      taxi.dataSource("DirectoryOfPerson") as FileDataSource
   }

   @Test
   fun canDeclareASource() {
      val src = """
 type Person {
   firstName : FirstName as String
   lastName : LastName as String
   }

fileResource(path = "/some/file/location", format = "csv") DirectoryOfPerson provides rowsOf Person {
   firstName by column(1)
   lastName by column(2)
}
      """.trimIndent()
      val taxi = Compiler(src).compile()
      val dataSource = taxi.dataSource("DirectoryOfPerson") as FileDataSource
      dataSource.returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Person>")
      dataSource.mappings.should.have.size(2)

      dataSource.mappings[0].propertyName.should.equal("firstName")
      dataSource.mappings[0].index.should.equal(1)
      dataSource.mappings[1].propertyName.should.equal("lastName")
      dataSource.mappings[1].index.should.equal(2)
   }

   @Test
   fun canDeclareTypeWithColumnMappingsAndThenUseAsASource() {
      val src = """
type alias FirstName as String
type alias LastName as String
type Person {
   firstName : FirstName by column(0)
   lastName : LastName by column(1)
   title: String by default("Mr.")
   age: Int by default(18)
   primaryKey: String by concat(column(0), "-", column(1), "-", column(2))
   leftField: String by leftAndUpperCase(column(0), "7")
   midField: String by midAndUpperCase(column(1), "5", "3")
}

fileResource(path = "/some/file/location", format = "csv") DirectoryOfPerson provides rowsOf Person {}
      """.trimIndent()
      val taxi = Compiler(src).compile()
      val person = taxi.objectType("Person")
      val firstName = person.field("firstName")
      val title = person.field("title")
      val age = person.field("age")
      val primaryKey = person.field("primaryKey")
      firstName.accessor.should.be.instanceof(ColumnAccessor::class.java)
      title.accessor.should.be.instanceof(ColumnAccessor::class.java)
      age.accessor.should.be.instanceof(ColumnAccessor::class.java)
      primaryKey.accessor.should.be.instanceof(ReadFunctionFieldAccessor::class.java)
      val readFunctionAccessor = primaryKey.accessor as ReadFunctionFieldAccessor
      readFunctionAccessor.arguments.size.should.equal(5)
      readFunctionAccessor.arguments[0].columnAccessor.should.be.not.`null`
      readFunctionAccessor.arguments[0].value.should.be.`null`
      readFunctionAccessor.arguments[1].columnAccessor.should.be.`null`
      readFunctionAccessor.arguments[1].value.should.equal("-")
      readFunctionAccessor.arguments[2].columnAccessor.should.be.not.`null`
      readFunctionAccessor.arguments[2].value.should.be.`null`
      readFunctionAccessor.arguments[3].columnAccessor.should.be.`null`
      readFunctionAccessor.arguments[3].value.should.equal("-")
      readFunctionAccessor.arguments[4].columnAccessor.should.be.not.`null`
      readFunctionAccessor.arguments[4].value.should.be.`null`
   }

   @Test
   fun dataSourcesCanHaveAnnotations() {
      val src = """

type Person {
   firstName : FirstName as String
   lastName : LastName as String
   }

@Foo
fileResource(path = "/some/file/location", format = "csv") DirectoryOfPerson provides rowsOf Person {
   firstName by column(0)
   lastName by column(1)
}
      """.trimIndent()
      val taxi = Compiler(src).compile()
      val dataSource = taxi.dataSource("DirectoryOfPerson") as FileDataSource
      dataSource.annotations.should.have.size(1)
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
      errors.should.have.size(2)
   }

   @Test
   fun `explicitly qualified function return parameter is resolved`() {
      val sourceA = """
namespace test {
    type alias FirstName as String
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
    type alias FirstName as String
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
    type alias FirstName as String
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
}








