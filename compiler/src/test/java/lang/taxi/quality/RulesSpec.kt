package lang.taxi.quality

import com.winterbe.expekt.should
import lang.taxi.compiled
import lang.taxi.dataQuality.DataQualityRuleScope
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RulesSpec : Spek({
   describe("data quality rules") {
      it("should be possible to compile a simple rule") {
         val doc = """namespace foo.rules
            type rule RuleAgainstType
            model rule RuleAgainstModel
            rule RuleWithoutScope
         """.compiled()
         val typeRule = doc.dataQualityRule("foo.rules.RuleAgainstType")!!
         typeRule.qualifiedName.should.equal("foo.rules.RuleAgainstType")
         typeRule.scope.should.equal(DataQualityRuleScope.TYPE)

         val modelRule = doc.dataQualityRule("foo.rules.RuleAgainstModel")!!
         modelRule.scope.should.equal(DataQualityRuleScope.MODEL)

         val ruleWithoutScope = doc.dataQualityRule("foo.rules.RuleWithoutScope")!!
         ruleWithoutScope.scope.should.be.`null`
      }

      it("should be possible to provide annotations and taxidoc to a data quality rule") {
         val rule = """
            [[ This is some docs ]]
            @Annotated
            type rule SimpleRule
         """.compiled()
            .dataQualityRule("SimpleRule")!!
         rule.typeDoc.should.equal("This is some docs")
         rule.annotations.should.have.size(1)
         rule.annotations.first().name.should.equal("Annotated")
      }
      it("should be possible to define a rule and apply it against a type") {
         val taxi = """
            type rule NullableShouldNotBeNull

            @Rule(NullableShouldNotBeNull)
            type FirstName inherits String
         """.compiled()
      }
      it("is invalid to a apply a type rule to a model") {
         """
            model rule NullableShouldNotBeNull

            @Rule(NullableShouldNotBeNull) // invalid
            type FirstName inherits String
         """.trimIndent()
      }

      it("should be possible to define a rule with an applyTo condition") {
         val doc = """
            declare function isNullable():Boolean
            declare function somethingElse(): Boolean

            type rule NullableShouldNotBeNull {
               applyTo {
               // These are functions, provided into an evaluator.
                  isNullable(),
                  somethingElse()
               }
            }
            """.compiled()
         val rule = doc.dataQualityRule("NullableShouldNotBeNull")!!
         rule.applyToFunctions.should.have.size(2)
         rule.applyToFunctions[0].qualifiedName.should.equal("isNullable")
         rule.applyToFunctions[1].qualifiedName.should.equal("somethingElse")
      }
   }
})
