package lang.taxi

import arrow.core.const
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.FormulaOperator
import kotlin.math.exp

class OperationContractSpreadOperatorSpec : DescribeSpec({
   describe("operation contract spread operator") {

      it("applies a contract based on single property") {
         val operation = """
         model Film {
            id : FilmId inherits Int
            title : Title inherits String
         }
         service FilmApi {
            operation getFilm(FilmId):Film(...)
         }
         """.compiled()
            .service("FilmApi")
            .operation("getFilm")

         operation.contract!!.returnTypeConstraints
            .shouldHaveSize(1)

         val expression = operation.contract!!.returnTypeConstraints.first()
            .shouldBeInstanceOf<ExpressionConstraint>()
            .expression.shouldBeInstanceOf<OperatorExpression>()

         expression.lhs.shouldBeInstanceOf<TypeExpression>()
            .type.qualifiedName.shouldBe("FilmId")
         expression.operator.shouldBe(FormulaOperator.Equal)
         expression.rhs.shouldBeInstanceOf<ArgumentSelector>()
            .scope.name.shouldBe("p0")
      }

      it("applies a contract based on multiple properties") {
         val operation = """
         model Film {
            id : FilmId inherits Int
            title : Title inherits String
            yearReleased : YearReleased inherits Int
         }
         service FilmApi {
            operation getFilm(FilmId, YearReleased):Film(...)
         }
         """.compiled()
            .service("FilmApi")
            .operation("getFilm")

         operation.contract!!.returnTypeConstraints
            .shouldHaveSize(1)

         val expression = operation.contract!!.returnTypeConstraints.first()
            .shouldBeInstanceOf<ExpressionConstraint>()
            .expression.shouldBeInstanceOf<OperatorExpression>()

         expression.lhs.shouldBeInstanceOf<OperatorExpression>().let { lhs ->
            lhs.lhs.shouldBeInstanceOf<TypeExpression>()
               .type.qualifiedName.shouldBe("FilmId")
            lhs.rhs.shouldBeInstanceOf<ArgumentSelector>()
               .scope.name.shouldBe("p0")
            lhs.operator.shouldBe(FormulaOperator.Equal)
         }

         expression.rhs.shouldBeInstanceOf<OperatorExpression>().let { rhs ->
            rhs.lhs.shouldBeInstanceOf<TypeExpression>()
               .type.qualifiedName.shouldBe("YearReleased")
            rhs.rhs.shouldBeInstanceOf<ArgumentSelector>()
               .scope.name.shouldBe("p1")
            rhs.operator.shouldBe(FormulaOperator.Equal)
         }
         expression.operator.shouldBe(FormulaOperator.LogicalAnd)

      }

      it("applies a contract based on remaining properties") {
         val operation = """
         model Film {
            id : FilmId inherits Int
            title : Title inherits String
            yearReleased : YearReleased inherits Int
         }
         service FilmApi {
            operation getFilm(FilmId, yearReleased:YearReleased):Film(YearReleased > yearReleased && ...)
         }
         """.compiled()
            .service("FilmApi")
            .operation("getFilm")

         operation.contract!!.returnTypeConstraints
            .shouldHaveSize(1)

         val expression = operation.contract!!.returnTypeConstraints.first()
            .shouldBeInstanceOf<ExpressionConstraint>()
            .expression.shouldBeInstanceOf<OperatorExpression>()

         expression.lhs.shouldBeInstanceOf<OperatorExpression>().let { lhs ->
            lhs.lhs.shouldBeInstanceOf<TypeExpression>()
               .type.qualifiedName.shouldBe("YearReleased")
            lhs.rhs.shouldBeInstanceOf<ArgumentSelector>()
               .scope.name.shouldBe("yearReleased")
            lhs.operator.shouldBe(FormulaOperator.GreaterThan)
         }

         expression.rhs.shouldBeInstanceOf<OperatorExpression>().let { rhs ->
            rhs.lhs.shouldBeInstanceOf<TypeExpression>()
               .type.qualifiedName.shouldBe("FilmId")
            rhs.rhs.shouldBeInstanceOf<ArgumentSelector>()
               .scope.name.shouldBe("p0")
            rhs.operator.shouldBe(FormulaOperator.Equal)
         }
         expression.operator.shouldBe(FormulaOperator.LogicalAnd)
      }
   }
})
