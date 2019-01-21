package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.services.AttributeConstantValueConstraint
import lang.taxi.types.*
import org.antlr.v4.runtime.CharStreams
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File

class GrammarTest {

    @Rule
    @JvmField
    val expectedException = ExpectedException.none()

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
   amount : Money(currency = 'GBP')
   clientId : ClientId as String
}
"""
        val doc = Compiler(source).compile()
        val request = doc.objectType("SomeServiceRequest")

        val amountField = request.field("amount")
        expect(amountField.constraints).to.have.size(1)
        expect(amountField.constraints[0]).to.be.instanceof(AttributeConstantValueConstraint::class.java)
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

        val schemaB = Compiler(sourceB, listOf(schemaA)).compile()
        val customer = schemaB.type("foo.Customer") as ObjectType
        expect(customer.field("name").type.qualifiedName).to.equal("test.FirstName")
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
        expect(errors).to.have.size(1)
        expect(errors.first().detailMessage).to.equal("Cannot import test.FirstName as it is not defined")

    }
}
