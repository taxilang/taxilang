package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.accessors.ColumnAccessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.*
import lang.taxi.functions.FunctionModifiers
import lang.taxi.functions.stdlib.Left
import lang.taxi.linter.LinterRules
import lang.taxi.types.FormulaOperator
import lang.taxi.types.LambdaExpressionType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TypeReferenceSelector
import java.util.*
import kotlin.test.assertFailsWith


class FunctionSpec : DescribeSpec({

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

      it("a function can be defined with query modifier") {
         val taxi = """
            declare query function concat(a:String,b:String):String
         """.compiled()

         val function = taxi.function("concat")
         function.parameters.should.have.size(2)

         function.parameters[0].type.should.equal(PrimitiveType.STRING)
         function.parameters[0].name.should.equal("a")
         function.parameters[1].type.should.equal(PrimitiveType.STRING)
         function.parameters[1].name.should.equal("b")

         function.returnType.should.equal(PrimitiveType.STRING)
         function.modifiers.should.equal(EnumSet.of(FunctionModifiers.Query))
      }

      xit("can infer the type on a model when using a function") {
         """
            declare function left(String):String

            model Foo {
               firstName : String
               initial by left(this.firstName)
            }
         """.compiled()
            .model("Foo")
            .field("initial")
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
            .accessor as FunctionExpression
         accessor.function.function.qualifiedName.should.equal("pkgA.upperCase")
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

         val accessor = field.accessor as FunctionExpression
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
            .field("max").accessor as FunctionExpression

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
         val accessor = taxi.objectType("Record").field("primaryKey").accessor as FunctionExpression
         accessor.function.qualifiedName.should.equal("concat")
         accessor.inputs.should.have.size(3)
         (accessor.inputs[0] as FunctionExpression).let { input ->
            input.function.qualifiedName.should.equal("left")
            input.function.parameters.should.have.size(2)
            input.inputs[0].should.equal(ColumnAccessor(0, defaultValue = null, returnType = PrimitiveType.STRING))
            input.inputs[1].should.equal(LiteralAccessor(3))
         }
         accessor.inputs[1].should.equal(LiteralAccessor("-"))
         accessor.inputs[2].should.equal(ColumnAccessor(1, defaultValue = null, returnType = PrimitiveType.STRING))
      }

      it("should allow fields to reference other fields as inputs") {
         val schema = """type FirstName inherits String
            type FullName inherits String

            declare function left(String,Int):String

               model Person {
                  firstName: FirstName
                  leftName : FullName by left(this.firstName, 5)
               }""".compiled()

         val accessor = schema
            .objectType("Person")
            .field("leftName")
            .accessor as FunctionExpression
         accessor.inputs[0].asA<FieldReferenceExpression>().path.should.equal("firstName")
         accessor.inputs[0].asA<FieldReferenceExpression>().returnType.qualifiedName.should.equal("FirstName")
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
            .accessor as FunctionExpression
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
               .accessor as FunctionExpression
            accessor.function.qualifiedName.should.equal(Left.name.fullyQualifiedName)
         }
      }

      it("should report an error if there is a type mismatch on input parameters") {
         val errors = """
            declare function uppercase(String):String
            model Person {
               age : Int
               name : String by uppercase(this.age)
            }
         """.validated(linterRules = LinterRules.allDisabled())
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("Type mismatch. Type of lang.taxi.Int is not assignable to type lang.taxi.String")
      }

      it("should report an error if there is a type mismatch on assigning a return value to a field") {
         val errors = """
            declare function uppercase(String):String
            model Person {
               age : Int by uppercase(this.name)
               name : String
            }
         """.validated(linterRules = LinterRules.allDisabled())
            .shouldContainMessage("Type mismatch. Type of lang.taxi.String is not assignable to type lang.taxi.Int")
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
            .model("Person").field("field1").accessor as FunctionExpression
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

      it("a query function cannot be part of anonymous type definition") {
         val taxiDocument = """
            declare query function upper(String):String
            model Record {
               primaryKey: String
            }


         """.compiled()

         val src = """
            find {
               Record[]
            } as {
              upperPrimaryKey: String by upper(Record['primaryKey'])
            }[]
         """.trimIndent()
         val (errors, _) = Compiler(source = src, importSources = listOf(taxiDocument)).queriesWithErrorMessages()
         errors.shouldContainMessage("Query functions may only be used within view definitions")
         // Currently our only query function is sumOver
         // and its implementation is pushed to the database therefore we restrict application of the query
         // function only to View definitions, but we'll relax this in the future.
      }

      // This syntax isn't valid anymore, not sure what the test was trying to prove
      xit("a  query function can be part of a field definition in a model") {
         val taxiDocument = """
            declare function upper(String):String
            model Record {
               primaryKey: String
               upperPrimaryKey: String by upper(this.primaryKey)
            }
         """.compiled()

         val src = """
            findAll {
               Record[]
            } as {
              upperPrimaryKey: String by upper(this.primaryKey)
            }[]
         """.trimIndent()

         val (errors, _) = Compiler(source = src, importSources = listOf(taxiDocument)).queriesWithErrorMessages()
         errors.should.be.empty
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
            .accessor as OperatorExpression
         accessor.lhs.asA<FunctionExpression>().function.function.qualifiedName.should.equal("pkgA.upperCase")
         accessor.lhs.asA<FunctionExpression>().returnType.should.equal(PrimitiveType.STRING)
         accessor.operator.should.equal(FormulaOperator.Add)
         accessor.rhs.asA<LiteralExpression>().value.should.equal("foo")
         accessor.rhs.asA<LiteralExpression>().returnType.should.equal(PrimitiveType.STRING)
      }

      it("is valid to add an int constant with a function returning int") {
         val expression = """
namespace pkgA {
   declare function length(String):Int
}
namespace pkgB {
            type Record {
               primaryKey: Int by pkgA.length("a") + 5
            }
}
         """.compiled()
            .objectType("pkgB.Record")
            .field("primaryKey")
            .accessor as OperatorExpression
         expression.lhs.asA<FunctionExpression>().function.function.qualifiedName.should.equal("pkgA.length")
         expression.operator.should.equal(FormulaOperator.Add)
         expression.rhs.asA<LiteralExpression>().value.should.equal(5)
         expression.rhs.asA<LiteralExpression>().returnType.should.equal(PrimitiveType.INTEGER)
      }

      it("is valid to subtract a int constant from a function returning int") {
         val accessor = """
namespace pkgA {
   declare function length(String):Int
}
namespace pkgB {
            type Record {
               primaryKey: Int by pkgA.length("a") - 5
            }
}
         """.compiled()
            .objectType("pkgB.Record")
            .field("primaryKey")
            .accessor as OperatorExpression
         accessor.lhs.asA<FunctionExpression>().function.function.qualifiedName.should.equal("pkgA.length")
         accessor.operator.should.equal(FormulaOperator.Subtract)
         accessor.rhs.asA<LiteralExpression>().value.should.equal(5)
      }

      it("is not valid to use subtract with a function returning string") {
         """
namespace pkgA {
   declare function upper(String):String
}
namespace pkgB {
            type Record {
               primaryKey: String by pkgA.upper("a") - 5
            }
}
         """.validated()
            .shouldContainMessage("Operations with symbol '-' is not supported on types String and Int")
      }

      it("is  valid to multiply an int constant with a function returning int") {
         val accessor = """
namespace pkgA {
   declare function length(String):Int
}
namespace pkgB {
            type Record {
               primaryKey: Int by pkgA.length("a") * 5
            }
}
         """.compiled()
            .objectType("pkgB.Record")
            .field("primaryKey")
            .accessor as OperatorExpression
         accessor.lhs.asA<FunctionExpression>().function.function.qualifiedName.should.equal("pkgA.length")
         accessor.operator.should.equal(FormulaOperator.Multiply)
         accessor.rhs.asA<LiteralExpression>().value.should.equal(5)
      }

      it("is possible to declare docs on functions") {
         """
            [[ Is a foo ]]
            declare function foo(String):String
         """.compiled()
            .function("foo")
            .typeDoc.should.equal("Is a foo")
      }

      it("query functions can't be referenced from model fields") {
         val compilationMessages = """
            import vyne.aggregations.sumOver
            model Foo {
                field1: Decimal
                field2: Decimal by sumOver(this.field1)
            }
         """.validated()
            .errors().shouldContainMessage("Query functions may only be used within view definitions")
      }



      it("is possible to declare functions in expression types") {
         val expression = """
            type FirstName inherits String
            type LastName inherits String
            type FullName by concat(FirstName, ' ', LastName)
         """.compiled()
            .objectType("FullName")
            .expression.shouldNotBeNull()
         expression.shouldBeInstanceOf<FunctionExpression>()
         expression.returnType.qualifiedName.shouldBe(PrimitiveType.STRING.qualifiedName)
      }

      describe("lambdas") {
         it("is possible to declare a typed lambda function") {
            val function = """
               declare function reduce(Int[], matcher:(Int,String) -> String):Int
            """.compiled()
               .function("reduce")
            val lambda = function.parameters[1].type as LambdaExpressionType
            lambda.parameterTypes.should.have.size(2)
            lambda.parameterTypes[0].should.equal(PrimitiveType.INTEGER)
            lambda.parameterTypes[1].should.equal(PrimitiveType.STRING)
            lambda.returnType.should.equal(PrimitiveType.STRING)
         }
      }

      describe("generic type params") {
         it("is valid to declare a function with generic type params") {
            val function = """
               declare function <T,A> reduce(T[], (T,A) -> A):A
            """.compiled()
               .function("reduce")
            function.typeArguments!!.should.have.size(2)
            function.typeArguments!![0].qualifiedName.should.equal("reduce\$T")
            function.typeArguments!![0].declaredName.should.equal("T")
            function.typeArguments!![1].qualifiedName.should.equal("reduce\$A")
            function.typeArguments!![1].declaredName.should.equal("A")
         }
         // Not implemented, wish it was...
         xit("is valid to declare bounds on generic type params") {
            val type = """
               declare function <T inherits Int,A inherits Int> reduce(T[], (T,A) -> A):A
            """.compiled()
               .objectType("reduce")
            TODO()
         }
         it("when calling a function with generic params then the generics are resolved") {
            val lambda = """
               model Entry {
                  weight:Weight inherits Int
                  score:Score inherits Int
               }
               declare function <T,A> reduce(T[], (T,A) -> A):A

               type WeightedAverage by (Entry[]) -> reduce(Entry[], (Entry, Int) -> Int + (Weight*Score))
            """.compiled()
               .objectType("WeightedAverage")
               .expression!! as LambdaExpression

            // it should resolve the generic parameter types
            val functionExpression = lambda.expression.asA<FunctionExpression>()
            functionExpression.returnType.qualifiedName.should.equal(PrimitiveType.INTEGER.qualifiedName)
            val resolvedInputs = functionExpression.function.inputs
            resolvedInputs[0].asA<TypeExpression>().type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Entry>")
            resolvedInputs[1].asA<LambdaExpression>().inputs[0].qualifiedName.should.equal("Entry")
            resolvedInputs[1].asA<LambdaExpression>().inputs[1].should.equal(PrimitiveType.INTEGER)
         }
      }

   }
})
