package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.PrimitiveType
import org.hamcrest.CoreMatchers.startsWith
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

// NOte : I'm trying to split tests out from GrammarTest
// to more specific areas.
// There's also a bunch of tests in GrammarTest that cover type Inheritence
class InheritenceTest {

   @Rule
   @JvmField
   val expectedException = ExpectedException.none()

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

      expectedException.expect(CompilationException::class.java)
      expectedException.expectMessage("UnknownSource(5,28) A Type cannot inherit from an Enum")

      Compiler(src).compile()
   }

   @Test
   fun enumCantInheritsFromType() {
      val src = """
         type Country
         enum BetterCountry inherits Country
      """.trimIndent()

      expectedException.expect(CompilationException::class.java)
      expectedException.expectMessage("UnknownSource(2,28) An Enum can only inherit from an Enum")

      Compiler(src).compile()
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

      expectedException.expect(CompilationException::class.java)
      expectedException.expectMessage(startsWith("Compilation Error: UnknownSource(6,34) extraneous input '{'"))

      Compiler(src).compile()
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

      expectedException.expect(CompilationException::class.java)
      expectedException.expectMessage(startsWith("Compilation Error: UnknownSource(7,38) extraneous input ','"))

      Compiler(src).compile()
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
