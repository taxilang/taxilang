package lang.taxi

import io.kotest.assertions.fail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.ExtensionFunctionExpression
import lang.taxi.expressions.ProjectingExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.policies.PolicyOperationScope
import lang.taxi.services.OperationScope
import lang.taxi.types.ObjectType
import lang.taxi.types.WhenExpression
import kotlin.test.fail

class PolicySpec : DescribeSpec({
   describe("access policies") {
      it("can define docs on a policy") {
         val policy = """
         model Film {}

         [[ Hello, world! ]]
         policy AllAccessFilms against Film {
            read external { Film }
         }
         """.compiled()
            .policy("AllAccessFilms")
            .typeDoc.shouldBe("Hello, world!")
      }
      it("is possible to declare a simple pass-through policy") {
         val policy = """
         model Film {}

         policy AllAccessFilms against Film {
            read external { Film }
         }
         """.compiled()
            .policy("AllAccessFilms")
         policy.targetType.qualifiedName.shouldBe("Film")
         val rule = policy.rules.single()
         rule.policyScope.shouldBe(PolicyOperationScope.EXTERNAL)
         rule.operationScope.shouldBe(OperationScope.READ_ONLY)
         rule.expression.shouldBeInstanceOf<TypeExpression>()
            .type.qualifiedName.shouldBe("Film")
      }
      it("is possible to declare policy using input args") {
         val policy = """
         model Account {
            user : UserId inherits String
         }
         model UserAccessToken {
            id : UserId
         }

         policy AccountsPolicy against Account (user:UserAccessToken) -> {
            read external {
               when {
                  // Only return the account if the account
                  // belongs to the user
                  user.id == this.user -> Account
                  else -> null
               }
             }
         }
         """.compiled()
            .policy("AccountsPolicy")
         policy.shouldNotBeNull()
         policy.inputs.shouldHaveSize(2)
         val userInput = policy.inputs.first { it.name == "user" }
            .shouldNotBeNull()
         userInput.type.qualifiedName.shouldBe("UserAccessToken")

         // should still have a "this" input
         val thisInput = policy.inputs.first { it.name == "this" }
            .shouldNotBeNull()
         thisInput.type.qualifiedName.shouldBe("Account")
      }
      it("is possible to hide a specific field") {
         val policy = """
          model Film {
            title : Title inherits String
            yearReleased : YearReleased inherits Int
         }
         policy FilmsPolicy against Film {
            read { Film as { ... except { yearReleased } } }
         }
         """.compiled()
            .policy("FilmsPolicy")
         policy.inputs.shouldHaveSize(1)
         policy.shouldNotBeNull()
         val projection = policy.rules.single()
            .expression
            .shouldBeInstanceOf<ProjectingExpression>()
         val fields = projection.projection.projectedType
            .asA<ObjectType>()
            .fields
         fields.filter { it.name == "yearReleased" }
            .shouldBeEmpty()
      }

      it("uses expressions in policies") {
         val policy = """
         declare extension function <T> containsAnyOf(source:T[], values:T[]): Boolean

         model Account {
            user : UserId inherits String
         }
         type SecurityGroup inherits String
         model UserAccessToken {
            id : UserId
            groups : SecurityGroup[]
         }

         policy AccountsPolicy against Account (user:UserAccessToken) -> {
            read external {
               when {
                  // Only return the account if the user is in the admin group
                  user.groups.containsAnyOf(["ADMIN","COMPLIANCE"]) -> Account
                  else -> null
               }
             }
         }
         """.compiled()
            .policy("AccountsPolicy")
         policy.shouldNotBeNull()

         policy.rules.single()
            .expression.shouldBeInstanceOf<WhenExpression>()
            .cases[0]
            .matchExpression
            .shouldBeInstanceOf<ExtensionFunctionExpression>()
      }

      it("infers the correct return type from when statements inside policies") {
         val policy = """
            model Film {
               title : Title inherits String
            }
            policy TestPolicy against Title {
               read {
                  when {
                     1 == 2 -> "foo"
                     else -> null
                  }
               }
            }
         """.compiled()
         .policy("TestPolicy")
         policy.rules.single().expression.shouldBeInstanceOf<WhenExpression>()
            .returnType.qualifiedName.shouldBe("Title")
      }
      it("can use parameter expressions referencing other parameter expressions") {
           """
            declare extension function uppercase(input:String):String
            model Film {
               title : Title inherits String
            }
            policy TestPolicy against Title (title:Title, uppercaseTitle:String = title.uppercase()) -> {
               read {
                  when {
                     1 == 2 -> "foo"
                     else -> null
                  }
               }
            }
           """.compiled()
      }
   }
})
