package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.ArrayAccessExpression
import lang.taxi.expressions.FieldReferenceExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.ArgumentSelector

class ArraysSpec : DescribeSpec({
   describe("arrays") {
      it("should allow defining an array using short hand and long hand") {
         val model = """
            model Foo {
               a : String[]
               b: lang.taxi.Array<String>
            }
         """.compiled()
            .model("Foo")
         model.field("a").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<lang.taxi.String>")
         model.field("b").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<lang.taxi.String>")
      }

   }
   describe("array access") {
      it("should allow an expression which is an array access using an index") {
         val field = """
            model Foo {
               a : String[]
               something: String = this.a[0]
            }
         """.compiled()
            .model("Foo")
            .field("something")
         val arrayAccessExpression = field.accessor.shouldBeInstanceOf<ArrayAccessExpression>()
         arrayAccessExpression.arrayInstanceExpression.shouldBeInstanceOf<FieldReferenceExpression>()
         arrayAccessExpression.arrayIndexExpression.shouldBeInstanceOf<LiteralExpression>()
      }
      it("should allow array access against type expression") {
         val (_,query) = """
            type Name inherits String
            model Foo {
               name : Name[]
            }
         """.compiledWithQuery("""
            find { Foo } as {
               something : Name = Name[][3]
             }
         """.trimIndent())
         val arrayAccessExpression = query.projectedObjectType!!
            .field("something").accessor.shouldBeInstanceOf<ArrayAccessExpression>()
         arrayAccessExpression.arrayInstanceExpression.shouldBeInstanceOf<TypeExpression>()
         arrayAccessExpression.arrayIndexExpression.shouldBeInstanceOf<LiteralExpression>()
      }
      it("should allow array access against type expression using variable for index") {
         val (_,query) = """
            type Name inherits String
            model Foo {
               name : Name[]
            }
         """.compiledWithQuery("""
            given { idx: Int = 3 }
            find { Foo } as {
               something : Name = Name[][idx]
             }
         """.trimIndent())
         val arrayAccessExpression = query.projectedObjectType!!
            .field("something").accessor.shouldBeInstanceOf<ArrayAccessExpression>()
         arrayAccessExpression.arrayInstanceExpression.shouldBeInstanceOf<TypeExpression>()
         arrayAccessExpression.arrayIndexExpression.shouldBeInstanceOf<ArgumentSelector>()
      }
      it("should raise error if the variable for array index is not a number") {
         val compilationException = """
            type Name inherits String
            model Foo {
               name : Name[]
            }
         """.compiledWithQueryProducingCompilationException("""
            given { idx: String = "3" }
            find { Foo } as {
               something : Name = Name[][idx]
             }
         """.trimIndent())
         compilationException.errors.shouldContainMessage("Array access requires a numeric type for the array index, but found type lang.taxi.String")
      }
      it("should raise error if the literal for array index is not a number") {
         val compilationException = """
            type Name inherits String
            model Foo {
               name : Name[]
            }
         """.compiledWithQueryProducingCompilationException("""
            find { Foo } as {
               something : Name = Name[]["3"]
             }
         """.trimIndent())
         compilationException.errors.shouldContainMessage("Array access requires a numeric type for the array index, but found type lang.taxi.String")
      }
      it("should provide a compilation error when array access uses an undefined variable") {
         val compilationException = """
            type Name inherits String
            model Foo {
               name : Name[]
            }
         """.compiledWithQueryProducingCompilationException("""
            find { Foo } as {
               something : Name = Name[][idx]
             }
         """.trimIndent())
         compilationException.errors.shouldContainMessage("idx is not defined")
      }
      it("should provide a compilation error when array access is used against a reference that is not an array") {
         val compilationException = """
            type Name inherits String
            model Foo {
               name : Name
            }
         """.compiledWithQueryProducingCompilationException(
            """
            find { Foo } as {
               something : Name = Name[idx]
             }
         """.trimIndent()
         )
         compilationException.errors.shouldContainMessage("Array access not supported on type Name")
      }
   }
})
