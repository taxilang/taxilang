package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.types.ArrayType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TypeAlias
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File

class GrammarTest {

    @Rule @JvmField
    val rule = ExpectedException.none()

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
    fun parsesAnnotationsCorrectly() {
        val source = """
@StringAnnotation(value = "foo")
@BooleanAnnotation(value = true)
//@IntegerAnnotation(value = 123)
type Test {}
"""
        val doc = Compiler(source).compile()
        val type = doc.objectType("Test")
        expect(type.annotation("StringAnnotation").parameters["value"]).to.equal("foo")
        expect(type.annotation("BooleanAnnotation").parameters["value"]).to.equal(true)
        expect(type.annotation("IntegerAnnotation").parameters["value"]).to.equal(123)
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
   email : String
}
"""
        val doc = Compiler(source).compile()
        expect(doc.objectType("Person").field("email").annotations).size.to.equal(1)
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
        rule.expect(CompilationException::class.java)
        rule.expectMessage(ErrorMessages.unresolvedType("Bar"))
        val source = """
type Foo {
   bar : Bar
}
"""
        Compiler(source).compile()
    }

    @Test
    fun canCompileExtensionType() {
        val source = """
type Person {
   name : String
}
@TypeAnnotation
type extension Person {
   @MyAnnotation(param2 = "bar")
   name
}
type extension Person {
   @AnotherAnnotation(param2 = "bar")
   name
}
"""
        val doc = Compiler(source).compile()
        val person = doc.objectType("Person")
        expect(person.field("name").annotations).size.to.equal(2)
        expect(person.annotations).size.to.equal(1)
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

    private fun testResource(s: String): File {
        return File(this.javaClass.classLoader.getResource(s).toURI())
    }
}
