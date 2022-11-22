package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.PrimitiveType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// NOte : I'm trying to split tests out from GrammarTest
// to more specific areas.
// There's also a bunch of tests in GrammarTest that cover type Inheritence
class InheritenceTest {

   @Test
   fun canInheritFromCollection() {
      val src = """
type Person {
   firstName : FirstName as String
}
type ListOfPerson inherits Person[]
      """.trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.type("ListOfPerson")
      type.inheritsFrom.map { it.toQualifiedName().parameterizedName }.should.contain("lang.taxi.Array<Person>")
   }

   @Test
   fun `can define inline inheritence`() {
      val doc = """model Person {
         |firstName : FirstName inherits String
         |}
      """.trimMargin()
         .compiled()
      doc.type("FirstName")
         .basePrimitive.should.equal(PrimitiveType.STRING)
      doc.model("Person")
         .field("firstName")
         .type.qualifiedName.should.equal("FirstName")
   }

   @Test
   fun `can define inline inheritence in namespace`() {
      val doc = """
         |namespace foo
         |
         |model Person {
         |firstName : FirstName inherits String
         |}
      """.trimMargin()
         .compiled()
      doc.type("foo.FirstName")
         .basePrimitive.should.equal(PrimitiveType.STRING)
      doc.model("foo.Person")
         .field("firstName")
         .type.qualifiedName.should.equal("foo.FirstName")
   }

   @Test
   fun canInheritFromAlias() {
      val src = """
   type alias CcySymbol as String
   type BaseCurrency inherits CcySymbol
""".trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.type("BaseCurrency")
      type.inheritsFrom.should.have.size(1)
   }

   @Test
   fun detectsUnderlyingPrimitivesCorrectly() {
      val src = """
   type alias CcySymbol as String
   type BaseCurrency inherits CcySymbol

   type Person {
      firstName : String
   }
""".trimIndent()
      val doc = Compiler(src).compile()
      doc.type("CcySymbol").basePrimitive!!.should.equal(PrimitiveType.STRING)
      doc.type("BaseCurrency").basePrimitive!!.should.equal(PrimitiveType.STRING)
      doc.type("Person").basePrimitive.should.be.`null`
      PrimitiveType.STRING.basePrimitive.should.equal(PrimitiveType.STRING)
   }

   @Test
   fun detectsUnderlyingEnum() {
      val src = """
         enum Country {
            NZ,
            AUS
         }
         enum BetterCountry inherits Country
         enum BetterCountryBis inherits BetterCountry
         type NotAnEnum {}
      """.trimIndent()
      val doc = Compiler(src).compile()
      doc.enumType("BetterCountry").baseEnum!!.qualifiedName.should.equal("Country")
      doc.enumType("BetterCountryBis").baseEnum!!.qualifiedName.should.equal("Country")
   }

   @Test
   fun basePrimitiveOfInheritedEnumReturnsValueFromBaseEnum() {
      val src = """
         enum Country {
            NZ,
            AUS
         }
         enum BetterCountry inherits Country
         enum BetterCountryBis inherits BetterCountry
      """.trimIndent()
      val doc = Compiler(src).compile()
      doc.type("Country").inheritsFromPrimitive.should.be.`true`
      doc.type("BetterCountry").inheritsFromPrimitive.should.be.`true`
      doc.type("BetterCountryBis").inheritsFromPrimitive.should.be.`true`
      doc.enumType("BetterCountry").basePrimitive.should.equal(PrimitiveType.STRING)
      doc.enumType("BetterCountryBis").basePrimitive.should.equal(PrimitiveType.STRING)
   }

   @Test
   fun canInstantiateAnInheritedEnum() {
      val src = """
         enum Country {
            NZ,
            AUS
         }
         enum BetterCountry inherits Country
         enum BetterCountryBis inherits BetterCountry
      """.trimIndent()
      val doc = Compiler(src).compile()
      doc.enumType("Country").of("NZ").should.not.be.`null`
      doc.enumType("BetterCountry").of("NZ").should.not.be.`null`
      doc.enumType("BetterCountryBis").of("NZ").should.not.be.`null`
   }

   @Test
   fun typeCantInheritsFromEnum() {
      val src = """
         enum Country {
            NZ,
            AUS
         }
         type BetterCountry inherits Country
      """.trimIndent()

      val exception = assertThrows<CompilationException> {
         Compiler(src).compile()
      }
      exception.message.should.contain("UnknownSource(5,28) A Type cannot inherit from an Enum")
   }

   @Test
   fun enumCantInheritsFromType() {
      val src = """
         type Country
         enum BetterCountry inherits Country
      """.trimIndent()

      val exception = assertThrows<CompilationException> {
         Compiler(src).compile()
      }
      exception.message.should.contain("UnknownSource(2,28) An Enum can only inherit from an Enum")
   }

   @Test
   fun enumCantRedefineInheritedEnum() {
      val src = """
         enum BestCountry {
            AUS,
            NZ

         }
         enum Country inherits BestCountry {
           BR
         }
      """.trimIndent()

      val (error, _) = Compiler(src).compileWithMessages()
      error.should.have.size(1)
   }

   @Test
   fun enumCantInheritsFromMultipleEnums() {
      val src = """
         enum BestCountry {
            AUS
         }
         enum BetterCountry {
            NZ
         }
         enum AllCountries inherits BestCountry, BetterCountry
      """.trimIndent()

      val (error, _) = Compiler(src).compileWithMessages()
      error.should.have.size(1)
   }

   @Test
   fun cacheAliasTypeProperties() {
      val src = """
         type alias CcySymbol as String""".trimIndent()
      val doc = Compiler(src).compile()
      doc.type("CcySymbol").basePrimitive.should.be.equal(PrimitiveType.STRING)
      doc.type("CcySymbol").basePrimitive.should.be.equal(PrimitiveType.STRING)
      doc.type("CcySymbol").allInheritedTypes.should.be.equal(setOf(PrimitiveType.STRING))
      doc.type("CcySymbol").allInheritedTypes.should.be.equal(setOf(PrimitiveType.STRING))
      doc.type("CcySymbol").inheritsFromPrimitive.should.be.`true`
      doc.type("CcySymbol").inheritsFromPrimitive.should.be.`true`
   }

   @Test
   fun cachePrimitiveTypeProperties() {
      PrimitiveType.INTEGER.basePrimitive.should.be.equal(PrimitiveType.INTEGER)
      PrimitiveType.INTEGER.basePrimitive.should.be.equal(PrimitiveType.INTEGER)
      PrimitiveType.INTEGER.allInheritedTypes.should.be.equal(emptySet())
      PrimitiveType.INTEGER.allInheritedTypes.should.be.equal(emptySet())
      PrimitiveType.INTEGER.inheritsFromPrimitive.should.be.`true`
      PrimitiveType.INTEGER.inheritsFromPrimitive.should.be.`true`
   }
}
