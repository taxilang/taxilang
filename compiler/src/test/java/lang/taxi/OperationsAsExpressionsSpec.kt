package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.MemberAccessExpression
import lang.taxi.expressions.OperationInvocationExpression
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType

class OperationsAsExpressionsSpec : DescribeSpec({
   describe("calling operations as expressions") {
      val taxiSrc = """
            model Person {
               name : PersonName inherits String
            }
            service PersonService {
               operation getName(emailAddress: String):Person
            }
      """.trimIndent()
      it("should let me call an operation as an expression") {
         val (schema,query) = taxiSrc.compiledWithQuery("""
            find {
               name : Person = PersonService::getName("marty")
            }
         """.trimIndent())
         val field = query.returnType.asA<ObjectType>()
            .field("name")
         val expression = field.accessor.shouldBeInstanceOf<OperationInvocationExpression>()
         expression.inputs.shouldHaveSize(1)
         expression.inputs.single()
            .shouldBeInstanceOf<LiteralAccessor>()
      }

      it("should raise a compilation error if the number of parameters is incorrect") {
         val exception = taxiSrc.compiledWithQueryProducingCompilationException("""
            find {
               name : Person = PersonService::getName()
            }
         """.trimIndent())
         exception.errors.shouldContainMessage("No value provided for parameter emailAddress on function getName, and no default value is defined")
      }
      it("should raise a compilation error if one of the parameters is of the wrong type") {
         val exception = taxiSrc.compiledWithQueryProducingCompilationException("""
            find {
               name : Person = PersonService::getName(23)
            }
         """.trimIndent())
         exception.errors.shouldContainMessage("Type mismatch. Type of lang.taxi.Int is not assignable to type lang.taxi.String")
      }

      // Disabled: ORB-1046
      xit("should raise a compilation error if the return type isn't assignable") {
         val exception = taxiSrc.compiledWithQueryProducingCompilationException("""
            find {
               name : String = PersonService::getName("marty")
            }
         """.trimIndent())
         exception.errors.shouldContainMessage("Type mismatch. Type of Person is not assignable to type lang.taxi.String")
      }

      it("should allow chaining an operation invocation with a field reference") {
         val (schema,query) = taxiSrc.compiledWithQuery("""
            find {
               name : String = PersonService::getName("marty").name
            }
         """.trimIndent())
         val field = query.returnType.asA<ObjectType>()
            .field("name")
         val expression = field.accessor.shouldBeInstanceOf<MemberAccessExpression>()
         expression.lhs.shouldBeInstanceOf<OperationInvocationExpression>()
         expression.rhs.shouldBeInstanceOf<FieldReferenceSelector>()
            .fieldName.shouldBe("name")
         expression.returnType.qualifiedName.shouldBe("PersonName")
      }
   }
})
