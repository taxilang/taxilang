package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.FunctionExpressionAccessor
import lang.taxi.functions.stdlib.Left
import lang.taxi.types.ColumnAccessor
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.FormulaOperator
import lang.taxi.types.LiteralAccessor
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TypeReferenceSelector
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

object FunctionSpec : Spek({

   describe("declaring functions") {
      it("should allow declaration of functions") {
         val taxi = """
            declare function left(input:String):String
         """.compiled()

         val function = taxi.function("left")
         function.parameters.should.have.size(1)

         function.parameters[0].type.should.equal(PrimitiveType.STRING)
         function.parameters[0].name.should.equal("input")
         function.returnType.should.equal(PrimitiveType.STRING)
      }


      it("is valid to declare function parameters without names") {
         val function = """
            declare function concat(String,String):String
         """.compiled().function("concat")
         function.parameters[0].name.should.be.`null`
         function.parameters[1].name.should.be.`null`
         function.parameters[0].type.should.equal(PrimitiveType.STRING)
         function.parameters[1].type.should.equal(PrimitiveType.STRING)
      }
      it("should allow vararg params") {
         val function = """
            declare function concat(b:String...):String
         """.compiled().function("concat")
         function.parameters[0].isVarArg.should.be.`true`
      }

      it("is invalid to declare vararg in position other than final arg") {
         // TODO
      }
      it("should allow functions with multiple params") {
         val taxi = """
            declare function concat(a:String,b:String):String
         """.compiled()

         val function = taxi.function("concat")
         function.parameters.should.have.size(2)

         function.parameters[0].type.should.equal(PrimitiveType.STRING)
         function.parameters[0].name.should.equal("a")
         function.parameters[1].type.should.equal(PrimitiveType.STRING)
         function.parameters[1].name.should.equal("b")

         function.returnType.should.equal(PrimitiveType.STRING)
      }
   }
   describe("using read functions") {
      it("is valid to use a fully qualified reference to function inline") {
         val accessor = """
namespace pkgA {
   declare function upperCase(String):String
}
namespace pkgB {
            type Record {
               primaryKey: String by pkgA.upperCase("a")
            }
}
         """.compiled()
            .objectType("pkgB.Record")
            .field("primaryKey")
            .accessor as FunctionAccessor
         accessor.function.qualifiedName.should.equal("pkgA.upperCase")
      }
      it("should allow string literal parameters") {
         val field = """
            declare function upper(String):String
            type Record {
               primaryKey: String by upper("a")
            }
         """.compiled()
            .objectType("Record")
            .field("primaryKey")

         val accessor = field.accessor as FunctionAccessor
         accessor.function.qualifiedName.should.equal("upper")
         accessor.inputs.should.have.size(1)
         val input = accessor.inputs[0] as LiteralAccessor
         input.value.should.equal("a")
      }
      it("should allow number literal parameters") {
         val accessor = """
            declare function max(Int,Int):Int
            type Record {
               max : Int by max(2,3)
            }
         """.compiled()
            .objectType("Record")
            .field("max").accessor as FunctionAccessor

         accessor.inputs.should.have.size(2)
         val param1 = accessor.inputs[0] as LiteralAccessor
         param1.value.should.equal(2)

         val param2 = accessor.inputs[1] as LiteralAccessor
         param2.value.should.equal(3)
      }
      it("should parse nested function definitions") {
         val taxi = """
            declare function concat(String...):String
            declare function left(String,Int):String
            type Record {
               primaryKey: String by concat(left(column(0), 3), "-", column(1))
            }
         """.compiled()
         val accessor = taxi.objectType("Record").field("primaryKey").accessor as FunctionAccessor
         accessor.function.qualifiedName.should.equal("concat")
         accessor.inputs.should.have.size(3)
         (accessor.inputs[0] as FunctionAccessor).let { input ->
            input.function.qualifiedName.should.equal("left")
            input.function.parameters.should.have.size(2)
            input.inputs[0].should.equal(ColumnAccessor(0, defaultValue = null))
            input.inputs[1].should.equal(LiteralAccessor(3))
         }
         accessor.inputs[1].should.equal(LiteralAccessor("-"))
         accessor.inputs[2].should.equal(ColumnAccessor(1, defaultValue = null))
      }

      it("should allow fields to reference other fields as inputs") {
         val accessor = """type FirstName inherits String
            type FullName inherits String

            declare function left(String,Int):String

               model Person {
                  firstName: FirstName
                  leftName : FullName by left(this.firstName, 5)
               }""".compiled()
            .objectType("Person")
            .field("leftName")
            .accessor as FunctionAccessor
         accessor.inputs[0].should.equal(FieldReferenceSelector("firstName"))
      }


      it("should resolve locally declared functions even if they have a similarly named counterpart in the stdlib") {
         val accessor = """
            // left is also declared in the stdlib, but we expect this version to get resolved
            declare function left(String,Int):String

               model Person {
                  firstName: String
                  leftName : String by left(this.firstName, 5)
               }""".compiled()
            .objectType("Person")
            .field("leftName")
            .accessor as FunctionAccessor
         accessor.function.qualifiedName.should.equal("left")
         accessor.function.qualifiedName.should.not.equal(Left.name.fullyQualifiedName)
      }

      describe("backwards compatability of stdlib") {
         // a bunch of functions used to be present in the top-level library.
         // Make sure they still are.
         it("should resolve stdlib functions without imports if no other declaration is present") {
            val accessor = """
               model Person {
                  firstName: String
                  leftName : String by left(this.firstName, 5)
               }""".compiled()
               .objectType("Person")
               .field("leftName")
               .accessor as FunctionAccessor
            accessor.function.qualifiedName.should.equal(Left.name.fullyQualifiedName)
         }
      }


      // Ignored until coalesce becomes a function
      xit("should allow fields to reference other types") {
         val accessor = """
               type FirstName inherits String
               type LastName inherits String
               type FullName inherits String

               declare function coalesce(String...):String

               model Person {
                  field1: String by coalesce(FirstName, LastName, FullName)
               }

            """.compiled()
            .model("Person").field("field1").accessor as FunctionAccessor
         (accessor.inputs[0] as TypeReferenceSelector).type.qualifiedName.should.equal("FirstName")
         (accessor.inputs[1] as TypeReferenceSelector).type.qualifiedName.should.equal("LastName")
         (accessor.inputs[2] as TypeReferenceSelector).type.qualifiedName.should.equal("FullName")
      }

      // No type checking now that we've moved across to functions.
      // Need to support this.
      // This test is transplanted from when coalese function was
      // hardcoded
      xit("Can't mix types with coalesce") {
         assertFailsWith(CompilationException::class) {
            val src = """
               type IntOne inherits Int
               type DecimalOne inherits Decimal
               type IntThree inherits Int

               model Foo {
                  field1: Int by coalesce(IntOne, DecimalOne, IntThree)
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Foo")
         }
      }
   }

   describe("Function expressions are allowed for limited cases") {
      it("is valid to concatenate a string literal with a function returning string") {
         val accessor = """
namespace pkgA {
   declare function upperCase(String):String
}
namespace pkgB {
            type Record {
               primaryKey: String by pkgA.upperCase("a") + 'foo'
            }
}
         """.compiled()
            .objectType("pkgB.Record")
            .field("primaryKey")
            .accessor as FunctionExpressionAccessor
         accessor.functionAccessor.function.qualifiedName.should.equal("pkgA.upperCase")
         accessor.operator.should.equal(FormulaOperator.Add)
         accessor.operand.should.equal("foo")
      }

      it("is valid to add an int constant with a function returning int") {
         val accessor = """
namespace pkgA {
   declare function length(String):Int
}
namespace pkgB {
            type Record {
               primaryKey: String by pkgA.length("a") + 5
            }
}
         """.compiled()
            .objectType("pkgB.Record")
            .field("primaryKey")
            .accessor as FunctionExpressionAccessor
         accessor.functionAccessor.function.qualifiedName.should.equal("pkgA.length")
         accessor.operator.should.equal(FormulaOperator.Add)
         accessor.operand.should.equal(5)
      }

      it("is valid to subtract a int constant from a function returning int") {
         val accessor = """
namespace pkgA {
   declare function length(String):Int
}
namespace pkgB {
            type Record {
               primaryKey: String by pkgA.length("a") - 5
            }
}
         """.compiled()
            .objectType("pkgB.Record")
            .field("primaryKey")
            .accessor as FunctionExpressionAccessor
         accessor.functionAccessor.function.qualifiedName.should.equal("pkgA.length")
         accessor.operator.should.equal(FormulaOperator.Subtract)
         accessor.operand.should.equal(5)
      }

      it("is not valid to use subtract with a function returning string") {
         val errors = """
namespace pkgA {
   declare function upper(String):String
}
namespace pkgB {
            type Record {
               primaryKey: String by pkgA.upper("a") - 5
            }
}
         """.validated()
         errors.size.should.equal(1)
      }

      it("is  valid to multiply an int constant with a function returning int") {
         val accessor = """
namespace pkgA {
   declare function length(String):Int
}
namespace pkgB {
            type Record {
               primaryKey: String by pkgA.length("a") * 5
            }
}
         """.compiled()
            .objectType("pkgB.Record")
            .field("primaryKey")
            .accessor as FunctionExpressionAccessor
         accessor.functionAccessor.function.qualifiedName.should.equal("pkgA.length")
         accessor.operator.should.equal(FormulaOperator.Multiply)
         accessor.operand.should.equal(5)
      }
   }
})
