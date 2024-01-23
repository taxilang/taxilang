package lang.taxi

import com.winterbe.expekt.should
import io.kotest.assertions.fail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.*
import lang.taxi.types.*

class ExpressionsSpec : DescribeSpec({

   describe("Expressions on models") {

      it("can use an expression type on a model") {
         """ type Height inherits Int
         type Width inherits Int
         type Area inherits Int by Height * Width
         model Rectangle {
            height : Height
            width : Width
            area : Area
         }"""
            .compiled()
            .model("Rectangle")
            .field("area")
            .type.qualifiedName.should.equal("Area")
      }
      it("can use functions in expression types") {
         val expressionType = """
            declare function squared(Int):Int

            type Height inherits Int

            type MultipleFunction inherits Int by squared(squared(Height))
         """.compiled()
            .objectType("MultipleFunction")
         val expression = expressionType.expression as FunctionExpression
         expression.function.function.qualifiedName.should.equal("squared")
         expression.function.inputs.should.have.size(1)
         val firstInput = expression.function.inputs.first() as FunctionExpression
         firstInput.inputs.should.have.size(1)
         val firstNestedInput = firstInput.inputs.first() as TypeExpression
         firstNestedInput.type.qualifiedName.should.equal("Height")
      }

      it("can use functions on rhs of expression types") {
         val expressionType = """
            declare function squared(Int):Int

            type Height inherits Int

            type MyExpression inherits Int by Height * squared(Height)
         """.compiled()
            .objectType("MyExpression")
         val expression = expressionType.expression as OperatorExpression
         val rhs = expression.rhs as FunctionExpression
         rhs.function.function.qualifiedName.should.equal("squared")
         rhs.function.inputs.should.have.size(1)
         val firstInput = rhs.function.inputs.first() as TypeExpression
         firstInput.type.qualifiedName.should.equal("Height")
      }

      describe("type reference selectors") {
         it("can use type references in am expression with inferred type") {
            val model = """
               model A
               model B
               model Foo {
                  field : A::B
                }
            """.compiled()
               .model("Foo")
            val field = model.field("field")
            field.type.qualifiedName.shouldBe("B")
            val accessor = field.accessor.shouldBeInstanceOf<ModelAttributeReferenceSelector>()
            accessor.memberSource.parameterizedName.shouldBe("A")
            accessor.targetType.qualifiedName.shouldBe("B")
         }
         it("can use type references in am expression with explict type") {
            val model = """
               model A
               model B
               model Foo {
                  field : B = A::B
                }
            """.compiled()
               .model("Foo")
            val field = model.field("field")
            field.type.qualifiedName.shouldBe("B")
            val accessor = field.accessor.shouldBeInstanceOf<ModelAttributeReferenceSelector>()
            accessor.memberSource.parameterizedName.shouldBe("A")
            accessor.targetType.qualifiedName.shouldBe("B")
         }
         it("can reference an array on the LHS of a type reference expression") {
            val field = """
               model A
               model B
               model Foo {
                  field : A[]::B
                }
            """.compiled()
               .model("Foo").field("field")
            field.type.qualifiedName.shouldBe("B")
            val accessor = field.accessor.shouldBeInstanceOf<ModelAttributeReferenceSelector>()
            accessor.memberSource.parameterizedName.shouldBe("lang.taxi.Array<A>")
            accessor.targetType.qualifiedName.shouldBe("B")
         }
         it("can reference an array on the RHS of a type reference expression") {
            val field = """
               model A
               model B
               model Foo {
                  field : A::B[]
                }
            """.compiled()
               .model("Foo").field("field")
            field.type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<B>")
            val accessor = field.accessor.shouldBeInstanceOf<ModelAttributeReferenceSelector>()
            accessor.memberSource.parameterizedName.shouldBe("A")
            accessor.targetType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<B>")
         }

         it("can reference an array of arrays") {
            val field = """
               model A
               model B
               model Foo {
                  field : (A::B[])[]
                }
            """.compiled()
               .model("Foo").field("field")
            field.type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<lang.taxi.Array<B>>")
            val accessor = field.accessor.shouldBeInstanceOf<ModelAttributeReferenceSelector>()
            accessor.memberSource.parameterizedName.shouldBe("A")
            accessor.targetType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<B>")
            accessor.returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<lang.taxi.Array<B>>")
         }

         it("can define expressions against types that reference each other") {
            // These types create a circular reference, which could be bad.
            // However, it's not a compilation error.
            // This test ensures that we're handling expression type resolution partway through compilation of
            // a dependent type.
            // Sepcifically this works by in the TokenProcessor.compileType()
            // we register an interim definition of the type, before compiling expressions
            val schema = """
               type OriginalQuantity inherits Decimal by SoldQuantity + RemainingQuantity
               type RemainingQuantity inherits Decimal by OriginalQuantity - SoldQuantity
               type SoldQuantity inherits Decimal by OriginalQuantity - RemainingQuantity
            """.compiled()
            val originalQty = schema.objectType("OriginalQuantity")
            originalQty.basePrimitive!!.shouldBe(PrimitiveType.DECIMAL)
            originalQty.expression.shouldNotBeNull()

            val remainingQty = schema.objectType("RemainingQuantity")
            remainingQty.basePrimitive!!.shouldBe(PrimitiveType.DECIMAL)
            remainingQty.expression.shouldNotBeNull()

            val soldQuantity = schema.objectType("SoldQuantity")
            soldQuantity.basePrimitive!!.shouldBe(PrimitiveType.DECIMAL)
            soldQuantity.expression.shouldNotBeNull()
         }

         it("parses using a coalesce operator") {
            val field = """model Person {
                firstName : String ?: 'Jimmy'
               }
            """.compiled()
               .objectType("Person")
               .field("firstName")
            val expression = field.accessor!!.asA<OperatorExpression>()
            expression.operator.shouldBe(FormulaOperator.Coalesce)
            expression.lhs.shouldBeInstanceOf<TypeExpression>()
            expression.rhs.shouldBeInstanceOf<LiteralExpression>()
         }

         it("can reference itself using a type reference selector") {
            val field = """
               model A
               model B
               model Foo {
                  field : Foo::A
               }
            """.compiled()
               .model("Foo")
               .field("field")
            field.accessor.shouldNotBeNull()
            val selector = field.accessor.shouldBeInstanceOf<ModelAttributeReferenceSelector>()
            selector.memberSource.shouldBe("Foo".toQualifiedName())
            selector.targetType.qualifiedName.shouldBe("A")
         }

         it("parses projections on expressions correctly") {
            val (schema, query) = """model Actor {
              actorId : ActorId inherits Int
              name : ActorName inherits String
            }
            model Film {
               title : FilmTitle inherits String
               headliner : ActorId
               cast: Actor[]
            }
         """.compiledWithQuery(
               """
            find { Film[] } as (film:Film) -> {
               title : FilmTitle
               star : singleBy(film.cast, (Actor) -> Actor::ActorId, film.headliner) as (actor:Actor) -> {
                  name : actor.name
                  title : film.title
               }
            }[]
         """.trimIndent()
            )
            val accessor = query.projectedObjectType!!.field("star").accessor!!
            accessor.shouldBeInstanceOf<ProjectingExpression>()
            val projectedType = accessor.projection.projectedType as ObjectType
            projectedType.anonymous.shouldBeTrue()
            projectedType.fields.map { it.name }.shouldContainExactly("name", "title")
            val functionExpression = accessor.expression.shouldBeInstanceOf<FunctionExpression>()
            functionExpression.function.qualifiedName.shouldBe("taxi.stdlib.singleBy")
         }
         it("parses nested inline projections on expressions correctly") {
            val (schema, query) = """model Actor {
              actorId : ActorId inherits Int
              name : ActorName inherits String
            }
            type ImdbActorId inherits Int
            model Film {
               title : FilmTitle inherits String
               headliner : ActorId
               cast: Actor[]
            }
         """.compiledWithQuery(
               """
            find { Film[] } as (film:Film) -> {
               title : FilmTitle
               // The test is the "Actor::ActorId as ImdbActorId" below....
               star : singleBy(film.cast, (Actor) -> Actor::ActorId as ImdbActorId, film.headliner) as (actor:Actor) -> {
                  name : actor.name
                  title : film.title
               }
            }[]
         """.trimIndent()
            )
            val accessor = query.projectedObjectType!!.field("star").accessor!!
            accessor.shouldBeInstanceOf<ProjectingExpression>()
            val nestedProjection = accessor.asA<ProjectingExpression>()
               .expression.asA<FunctionExpression>()
               .inputs[1].asA<ProjectingExpression>()

            nestedProjection.returnType.qualifiedName.shouldBe("ImdbActorId")
            nestedProjection.expression.shouldBeInstanceOf<LambdaExpression>()
         }

         it("parses nested inline projections to an inline anonymous type on expressions correctly") {
            val (schema, query) = """model Actor {
              actorId : ActorId inherits Int
              name : ActorName inherits String
            }
            type ImdbActorId inherits Int
            model Film {
               title : FilmTitle inherits String
               headliner : ActorId
               cast: Actor[]
            }
         """.compiledWithQuery(
               """
            find { Film[] } as (film:Film) -> {
               title : FilmTitle
               // The test is the "Actor::ActorId as ImdbActorId" below....
               star : singleBy(film.cast, (Actor) -> Actor::ActorId as { id : ActorId, imdb: ImdbActorId }, film.headliner) as (actor:Actor) -> {
                  name : actor.name
                  title : film.title
               }
            }[]
         """.trimIndent()
            )
            val accessor = query.projectedObjectType!!.field("star").accessor!!
            accessor.shouldBeInstanceOf<ProjectingExpression>()
            val nestedProjection = accessor.asA<ProjectingExpression>()
               .expression.asA<FunctionExpression>()
               .inputs[1].asA<ProjectingExpression>()

            nestedProjection.returnType.anonymous.shouldBeTrue()
            nestedProjection.returnType.asA<ObjectType>().fields.map { it.name }.shouldContainExactly("id", "imdb")
            nestedProjection.expression.shouldBeInstanceOf<LambdaExpression>()
         }
      }

      it("a single arg expression without a type declaration can resolve as a function call") {
         val schemaWithoutFieldType = """
   model Person {}
   model Movie {
      // This is the test.
      // There's no type declaration, so it must be inferred from the function.
      // However, the grammar is ambiguous - this could be either:
      //   - A type of first with a constraint
      //   - A function call named first, with a param
      // So, where the grammar can't differentiate, the parser has to.
       starring : first(Person[])
   }
""".compiled()

         val starringField = schemaWithoutFieldType.model("Movie")
            .field("starring")
         starringField
            .type.qualifiedName
            .shouldBe("Person")
      }
      it("reports type errors on a single arg expression without a type declaration resolved as a function call") {
         val errors = """
   model Person {}
   model Movie {
       // first expects an array for it's parameter.
       // This shouldn't compile.
       // This code goes through a different path because of grammar challenges discussed above
       starring : first(2)
   }
""".validated()
         errors.shouldContainMessage("Not enough information to infer type argument T (of lang.taxi.Array<taxi.stdlib.first\$T>) from the provided inputs")
      }

      it("reports invalid number of args on a single arg expression without a type declaration resolved as a function call") {
         val errors = """
   model Person {}
   model Movie {
       // first expects an array for it's parameter.
       // This shouldn't compile.
       // This code goes through a different path because of grammar challenges discussed above
       starring : first()
   }
""".validated()
         errors.shouldContainMessage("Function taxi.stdlib.first expects 1 parameters, but none were provided")
      }

      it("should use a projection statement after an expression to determine type") {
         val model = """
         model Person {
            id: PersonId inherits String
         }
         model Movie {
            // Selecting the first person as the star
             starring : PersonId = first(Person[]) as PersonId
         }
         """.compiled()
            .model("Movie")
         model.field("starring")
            .type.qualifiedName.shouldBe("PersonId")
      }

      it("should use a projection statement after an expression to infer type") {
         val model = """
         model Person {}
         type PersonId inherits String
         model Movie {
             // no explicit type declaration
             starring: first(Person[]) as PersonId
         }
         """.compiled()
            .model("Movie")
         val field = model.field("starring")
         field.type.qualifiedName.shouldBe("PersonId")
         field.projection.shouldNotBeNull()
      }
      it("should raise a compilation error if the projected type is not assignable") {
         val errors = """
         type MovieRating inherits Int
         model Person {
            id: PersonId inherits String
         }
         model Movie {
             // this should create a compilation error...
             starring : PersonId = first(Person[]) as MovieRating
         }
         """.validated()
         errors.shouldContainMessage("Type mismatch. Type of MovieRating is not assignable to type PersonId")
      }
   }
})
