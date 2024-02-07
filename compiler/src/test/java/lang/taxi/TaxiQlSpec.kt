package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.accessors.CollectionProjectionExpressionAccessor
import lang.taxi.accessors.FieldSourceAccessor
import lang.taxi.expressions.OperatorExpression
import lang.taxi.query.Parameter
import lang.taxi.query.QueryMode
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.*

class TaxiQlSpec : DescribeSpec({
   describe("Taxi Query Language") {
      val schema = """
         namespace foo

         type Quantity inherits Int
         type InsertedAt inherits Instant
         type TraderId inherits String
         type UserEmail inherits String
         type FirstName inherits String
         type LastName inherits String
         type OutputId inherits String

         type CustomerEmailAddress inherits String
         model Customer {
            email : CustomerEmailAddress
         }
         model Person {
            email : CustomerEmailAddress
            firstName : FirstName
            lastName : LastName
         }
         model Trade

         model OutputOrder {
            outputId: OutputId
         }
         type TradeDate inherits Instant
         model Order {
            tradeTimestamp : TradeDate
            traderId: TraderId
         }

         model Trade {
            traderId: TraderId
         }
      """.trimIndent()
      val taxi = Compiler(schema).compile()

      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Allow anonymous projected type definition") {
         val src = """
            import foo.Order
            import foo.OutputOrder

            find {
               Order[]( TradeDate  >= startDate && TradeDate < endDate )
            } as {
               tradeTimestamp
            }[]
      """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType.should.not.be.`null`
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("tradeTimestamp").should.be.`true`
      }

      it("should compile a simple query") {
         val src = """
            find { Order }
         """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.queryMode.should.equal(QueryMode.FIND_ALL)
      }

      it("should resolve unambiguous types without imports") {
         val src = """
                 query RecentOrdersQuery( startDate:Instant, endDate:Instant ) {
                    find {
                       Order[]( TradeDate >= startDate && TradeDate < endDate )
                    } as OutputOrder[]
                 }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.parameters.should.have.size(2)
         query.parameters.should.equal(
            listOf(
               Parameter.variable("startDate", PrimitiveType.INSTANT),
               Parameter.variable("endDate", PrimitiveType.INSTANT),
            )
         )
         query.projectedType?.toQualifiedName()?.parameterizedName.should.equal("lang.taxi.Array<foo.OutputOrder>")
         query.typesToFind.should.have.size(1)
         query.typesToFind.first().typeName.parameterizedName.should.equal("lang.taxi.Array<foo.Order>")
      }


      it("should compile a named query with params") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder
                 import foo.TradeDate

                 query RecentOrdersQuery( startDate:Instant, endDate:Instant ) {
                    given { startDate, endDate }
                    find {
                       Order[]( TradeDate >= startDate && TradeDate < endDate )
                    } as OutputOrder[]
                 }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.name.should.equal(QualifiedName.from("RecentOrdersQuery"))
         query.parameters.should.have.size(2)
         query.parameters.should.equal(
            listOf(
               Parameter.variable("startDate", PrimitiveType.INSTANT),
               Parameter.variable("endDate", PrimitiveType.INSTANT),
            )
         )
         query.projectedType?.toQualifiedName()?.parameterizedName.should.equal("lang.taxi.Array<foo.OutputOrder>")
         query.typesToFind.should.have.size(1)

         val typeToFind = query.typesToFind.first()
         typeToFind.typeName.parameterizedName.should.equal("lang.taxi.Array<foo.Order>")
         val constraint = typeToFind.constraints.single().asA<ExpressionConstraint>().expression as OperatorExpression
         val firstExpression = constraint.lhs.asA<OperatorExpression>()
         firstExpression.operator.shouldBe(FormulaOperator.GreaterThanOrEqual)
         firstExpression.rhs.asA<ArgumentSelector>().scope.name.shouldBe("startDate")

         val secondExpression = constraint.rhs.asA<OperatorExpression>()
         secondExpression.operator.shouldBe(FormulaOperator.LessThan)
         secondExpression.rhs.asA<ArgumentSelector>().scope.name.shouldBe("endDate")
      }

      it("should compile a query that exposes facts") {
         val src = """
                 given {
                    email : CustomerEmailAddress = "jimmy@demo.com"
                 }
                 find { Trade }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.facts.should.have.size(1)
         val (name, fact) = query.facts.first()
         name.should.equal("email")
         fact.typedValue.fqn.should.equal(QualifiedName("foo", "CustomerEmailAddress"))
         fact.typedValue.value.should.equal("jimmy@demo.com")

         query.typesToFind.should.have.size(1)
         val discoveryType = query.typesToFind.first()
         discoveryType.typeName.fullyQualifiedName.should.equal("foo.Trade")
         discoveryType.startingFacts.should.have.size(1)
         discoveryType.startingFacts.should.equal(query.facts)
      }

      it("is possible to express facts without specifying a variable name") {
         val (schema, query) = schema.compiledWithQuery(
            """
            given { CustomerEmailAddress = "jimmy@demo.com" }
            find { Trade }
         """.trimIndent()
         )
         query.facts.should.have.size(1)
         val (name, fact) = query.facts.first()
         fact.typedValue.fqn.should.equal(QualifiedName("foo", "CustomerEmailAddress"))
         fact.typedValue.value.should.equal("jimmy@demo.com")
      }

      it("is possible to declare a fact that is an object") {
         val (_, query) = schema.compiledWithQuery(
            """
             given { person: Person = { email: "jimmy@demo.com", firstName: "Jimmy", lastName: "Schmitt" } }
            find { Trade }
         """.trimIndent()
         )
         query.facts.should.have.size(1)
         val (name, fact) = query.facts.first()
         fact.typedValue.value.shouldBe(
            mapOf(
               "email" to "jimmy@demo.com",
               "firstName" to "Jimmy",
               "lastName" to "Schmitt"
            )
         )
      }

      it("is possible to declare a fact that is an array") {
         val (_, query) = schema.compiledWithQuery(
            """
             given { person: Person[] = [
               { email: "jimmy@demo.com", firstName: "Jimmy", lastName: "Schmitt" },
               { email: "mary@demo.com", firstName: "Mary", lastName: "Jones" }
             ] }
            find { Trade }
         """.trimIndent()
         )
         query.facts.should.have.size(1)
         val (name, fact) = query.facts.first()
         fact.typedValue.value.shouldBe(
            listOf(
               mapOf(
                  "email" to "jimmy@demo.com",
                  "firstName" to "Jimmy",
                  "lastName" to "Schmitt"
               ),
               mapOf(
                  "email" to "mary@demo.com",
                  "firstName" to "Mary",
                  "lastName" to "Jones"
               )
            )
         )
      }

      it("returns an error assigning a string to a number") {
         val exception = schema.compiledWithQueryProducingCompilationException(
            """
             given { quantity : Quantity = "1" }
            find { Trade }
         """.trimIndent(),
            CompilerConfig(typeCheckerEnabled = FeatureToggle.ENABLED)
         )
         exception.errors.shouldContainMessage("Type mismatch. Type of lang.taxi.String is not assignable to type foo.Quantity")

      }
      it("returns an error assigning an object to an array") {
         val exception = schema.compiledWithQueryProducingCompilationException(
            """
             given { person: Person[] = { email: "jimmy@demo.com", firstName: "Jimmy", lastName: "Schmitt" } }
            find { Trade }
         """.trimIndent(),
            CompilerConfig(typeCheckerEnabled = FeatureToggle.ENABLED)
         )
         exception.errors.shouldContainMessage("Map is not assignable to lang.taxi.Array")
      }

      it("returns an error assigning an object with missing properties") {
         val exception = schema.compiledWithQueryProducingCompilationException(
            """
             given { person: Person = { email: "jimmy@demo.com" /*, firstName: "Jimmy", lastName: "Schmitt" */ } }
            find { Trade }
         """.trimIndent(),
            CompilerConfig(typeCheckerEnabled = FeatureToggle.ENABLED)
         )
         exception.errors.shouldContainMessage("Map is not assignable to type foo.Person as mandatory properties firstName, lastName are missing")
      }

      it("should compile an unnamed query") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]
                 }""".trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         queries.first()
      }

      it("should return correct source for an unnamed query") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]
                 }""".trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         queries.single().compilationUnits.single().source.content.withoutWhitespace().shouldBe(src.withoutWhitespace())
      }

      it("should return correct source for a named query") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 query MyQuery {
                    find {
                       Order[]
                    }
                 }
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         queries.single().compilationUnits.single().source.content.withoutWhitespace().shouldBe(src.withoutWhitespace())
      }


      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should not Allow anonymous projected type definitions with invalid field references") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as {
                    invalidField
                 }[]
           """.trimIndent()
         val queryCompilationError =
            Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
         queryCompilationError.first().detailMessage.should.contain("should be an object type containing field invalidField")
      }

      it("query body can be an anonymous projection") {
         val src = """
                 given { email : CustomerEmailAddress = "jimmy@demo.com"}
                 find {
                   tradeDate: TradeDate
                 }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.facts.should.have.size(1)
         val (name, fact) = query.facts.first()
         name.should.equal("email")
         fact.typedValue.fqn.should.equal(QualifiedName("foo", "CustomerEmailAddress"))
         fact.typedValue.value.should.equal("jimmy@demo.com")

         query.typesToFind.should.have.size(1)
         val discoveryType = query.typesToFind.first()
         discoveryType.typeName.fullyQualifiedName.should.startWith("Anonymous")
         discoveryType.startingFacts.should.have.size(1)
         discoveryType.startingFacts.should.equal(query.facts)
      }

      it("query body can be an anonymous projection with constraints") {
         val src = """
                 given { startDate: TradeDate = parseDate("2022-12-02"), endDate: TradeDate = parseDate("2022-12-23") }
                 find {
                   tradeDate: TradeDate
                   traderId: TraderId
                 }  ( TradeDate  >= startDate && TradeDate < endDate )
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.facts.should.have.size(2)
         val (name, fact) = query.facts.first()
         name.should.equal("startDate")
         fact.type.qualifiedName.should.equal("foo.TradeDate")
         // Used to be constant:
//         fact.typedValue.value.should.equal("2022-12-02")

         query.typesToFind.should.have.size(1)
         val discoveryType = query.typesToFind.first()
         discoveryType.anonymousType?.anonymous.should.be.`true`
         discoveryType.startingFacts.should.have.size(2)
      }

      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Allow anonymous type that extends base type") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as OutputOrder {
                    tradeTimestamp
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("tradeTimestamp").should.be.`true`
         anonymousType.hasField("outputId").should.be.`true`
      }

      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Not Allow anonymous type that extends base type when anonymous type reference a field that does not part of discovery type") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as OutputOrder {
                    invalidField
                 }[]
           """.trimIndent()
         val queryCompilationError =
            Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
         queryCompilationError.first().detailMessage.should.contain("should be an object type containing field invalidField")
      }

      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Allow anonymous type that extends a base type and adds additional field definitions") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as OutputOrder {
                    insertedAt: foo.InsertedAt
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("insertedAt").should.be.`true`
         anonymousType.inheritsFrom.should.have.size(1)
         anonymousType.fields.should.have.size(2)
         anonymousType.inheritedFields.should.have.size(1)
      }

      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Allow anonymous type with field definitions") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as {
                    insertedAt: foo.InsertedAt
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("insertedAt").should.be.`true`
      }


      xit("Should Allow anonymous type with field definitions referencing type to discover") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]
                 } as {
                    traderEmail: UserEmail by Order['traderId']
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("traderEmail").should.be.`true`
         anonymousType.field("traderEmail").accessor.should.not.be.`null`
         val fieldSourceAccessor = anonymousType.field("traderEmail").accessor as FieldSourceAccessor
         fieldSourceAccessor.attributeType.should.equal(QualifiedName.from("foo.TraderId"))
         fieldSourceAccessor.sourceAttributeName.should.equal("traderId")
         fieldSourceAccessor.sourceType.should.equal(QualifiedName.from("foo.Order"))
      }


      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Allow anonymous type with field definitions referencing projected type") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as foo.Trade {
                    traderEmail: UserEmail by Trade['traderId']
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.qualifiedName.should.startWith("lang.taxi.Array")
         query.projectedType!!.anonymous.should.be.`true`
         val anonType = query.projectedType!!.typeParameters().first() as ObjectType
         anonType.fields.size.should.equal(2)
         val fieldSourceAccessor = anonType.field("traderEmail").accessor as FieldSourceAccessor
         fieldSourceAccessor.sourceType.should.equal(anonType.toQualifiedName())
         fieldSourceAccessor.sourceAttributeName.should.equal("traderId")
         fieldSourceAccessor.attributeType.should.equal(QualifiedName.from("foo.TraderId"))
      }

      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Allow anonymous type with field definitions referencing a type in the schema") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as foo.Trade {
                    traderEmail: UserEmail by Order['traderId']
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.qualifiedName.should.startWith("lang.taxi.Array")
         query.projectedType!!.anonymous.should.be.`true`
         val anonType = query.projectedType!!.typeParameters().first() as ObjectType
         anonType.fields.size.should.equal(2)
         val fieldSourceAccessor = anonType.field("traderEmail").accessor as FieldSourceAccessor
         fieldSourceAccessor.sourceType.should.equal(QualifiedName.from("foo.Order"))
         fieldSourceAccessor.sourceAttributeName.should.equal("traderId")
         fieldSourceAccessor.attributeType.should.equal(QualifiedName.from("foo.TraderId"))
      }


      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Fail anonymous type with field definitions referencing projected type but have invalid field type") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as foo.Trade {
                    traderEmail: InvalidType by (this.traderId)
                 }[]
           """.trimIndent()
         val queryCompilationError =
            Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
         queryCompilationError.first().detailMessage.should.contain("InvalidType is not defined")
      }

      // Not sure if this feature is used.  I like it, but the syntax is a little confusing
      xit("Should Allow anonymous type with complex field definitions referencing type to be discovered") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate && TradeDate < endDate )
                 } as {
                        salesPerson: {
                            firstName : FirstName
                            lastName : LastName
                        } by (this.traderId)
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("salesPerson").should.be.`true`
         val nestedAnonymousType = anonymousType.field("salesPerson").type as ObjectType
         nestedAnonymousType.anonymous.should.be.`true`
         nestedAnonymousType.hasField("firstName").should.be.`true`
         nestedAnonymousType.hasField("lastName").should.be.`true`
      }

      it("should handle queries of array types with long syntax") {
         val src = """
                 import foo.Order

                 find { lang.taxi.Array<Order> }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.typesToFind[0].typeName.parameterizedName.should.equal("lang.taxi.Array<foo.Order>")
      }


      it("should handle queries of array types with short syntax") {
         val src = """
                 import foo.Order

                 find { Order[] }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.typesToFind[0].typeName.parameterizedName.should.equal("lang.taxi.Array<foo.Order>")
      }

      // This feature (referencing the parent view, and extending an projection type) is cool, but
      // has been disabled for npow
      xit("Should Allow anonymous type with complex field definitions referencing projected type") {
         val src = """
                      import foo.Order
                      import foo.OutputOrder

                      find {
                         Order[]( TradeDate  >= startDate && TradeDate < endDate )
                      } as foo.Trade {
                             salesPerson: {
                                 firstName : {
                                    name: FirstName
                                 }
                                 lastName : {
                                    name: LastName
                                    }
                             } by (this.traderId)
                      }[]
                """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("salesPerson").should.be.`true`
         anonymousType.hasField("traderId").should.be.`true`
         val nestedAnonymousType = anonymousType.field("salesPerson").type as ObjectType
         nestedAnonymousType.anonymous.should.be.`true`
         nestedAnonymousType.hasField("firstName").should.be.`true`
         nestedAnonymousType.hasField("lastName").should.be.`true`
         nestedAnonymousType.field("firstName").type.anonymous.should.be.`true`
         nestedAnonymousType.field("lastName").type.anonymous.should.be.`true`
      }


      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("Should Detect anonymous type with invalid complex field definitions referencing projected type") {
         val src = """
                     import foo.Order
                     import foo.OutputOrder

                     find {
                        Order[]( TradeDate  >= startDate && TradeDate < endDate )
                     } as foo.Trade {
                            salesPerson: {
                                firstName : InvalidType
                                lastName : LastName
                            }by (this.traderId)
                     }[]
               """.trimIndent()
         val queryCompilationError =
            Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
         queryCompilationError.first().detailMessage.should.contain("InvalidType is not defined")
      }

      it("when discovery type is a collection then the type that we project into should also be a collection") {
         val src = """
                         query RecentOrdersQuery( startDate:Instant, endDate:Instant ) {
                            find {
                               Order[]( TradeDate >= startDate && TradeDate < endDate )
                            } as OutputOrder
                         }
                      """.trimIndent()
         val queryCompilationError =
            Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
         queryCompilationError.first().detailMessage.should.contain("projection type is a list but the type to discover is not, both should either be list or single entity.")
      }

      it("discovery type and anonymous projected type should either be list or be single entity II") {
         val src = """
                             query RecentOrdersQuery( startDate:Instant, endDate:Instant ) {
                                find {
                                   Order[]( TradeDate >= startDate && TradeDate < endDate )
                                } as {
                                   insertedAt: foo.InsertedAt
                                }
                             }
                          """.trimIndent()
         val queryCompilationError =
            Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
         queryCompilationError.first().detailMessage.should.contain("projection type is a list but the type to discover is not, both should either be list or single entity.")
      }

      it("is possible to add annotations to named queries and their params") {
         val query = """
            type MovieId inherits String
            model Movie {}

            @HttpOperation( method = "GET" , url = "/reviews/{movieId}")
            query MoviesAndReviews( @PathVariable("movieId") movieId : MovieId ) {
               find { Movie( MovieId == movieId ) }
            }
         """.compiled()
            .query("MoviesAndReviews")
         query.annotations.shouldHaveSize(1)
         query.annotations.single().qualifiedName.shouldBe("HttpOperation")

         val queryParam = query.parameters.single()
         queryParam.annotations.shouldHaveSize(1)
         queryParam.annotations.single().qualifiedName.shouldBe("PathVariable")
      }

      it("is possible to add docs to named queries and their params") {
         val query = """
            type MovieId inherits String
            model Movie {}

            [[ returns movies and their reviews ]]
            query MoviesAndReviews(  movieId : MovieId ) {
               find { Movie( MovieId == movieId ) }
            }
         """.compiled()
            .query("MoviesAndReviews")
         query.typeDoc.shouldBe("returns movies and their reviews")
      }

      // Design choice: This is a bad idea.
      // If we want "top level" annotations, they should
      // be on the query block. Otherwise, we lose our
      // ability to specifically annotate the find or given blocks.
      // .... time passes ....
      // Counterpoint:
      // Sometimes we want to do exactly what this test does.
      it("is possible to add annotations to unnamed queries") {
         val (_, query) = """
            type MovieId inherits String
            model Movie {}

         """.compiledWithQuery(
            """
             @IgnoreNullsInResponse
             find { Movie( MovieId == '123' ) }
         """.trimIndent()
         )
         query.annotations.shouldHaveSize(1)

      }

      it("should set name as null on unnamed queries") {
         val query = """
            type MovieId inherits Int
            model Movie {}

            find { Movie( MovieId == 123 ) }
         """.compiled()
            .queries.single()
         query.name.fullyQualifiedName.shouldNotBeEmpty()
      }
      it("defines queries within their namespace") {
         val query = """
            namespace test {

               type MovieId inherits Int
               model Movie {}

               query FindMyMovie {
                  find { Movie( MovieId == 123 ) }
               }

            }
         """.compiled()
            .queries.single()
         query.name.fullyQualifiedName.shouldBe("test.FindMyMovie")
      }

      it("assigns random names within namespace for unnamed queries within a namespace") {
         val query = """
            namespace test {

               type MovieId inherits Int
               model Movie {}

               find { Movie( MovieId == 123 ) }
            }
         """.compiled()
            .queries.single()
         query.name.namespace.shouldBe("test")
         query.name.typeName.shouldNotBeEmpty()
      }

      it("should parse nested collections of anonymous types") {
         val (schema, query) = """
            model Product {
               sku : ProductSku inherits String
               size : ProductSize inherits String
            }
            model TransactionItem {
               sku : ProductSku
            }
            model Transaction {
               items : TransactionItem[]
            }
         """.compiledWithQuery(
            """
            find { Transaction[] } as {
               items : {
                  sku : ProductSku
                  size : ProductSize
               }[]
            }[]
         """
         )
         val resultCollectionType = query.projectedType!! as ArrayType
         val resultMemberType = resultCollectionType.type as ObjectType
         val itemsFieldType = resultMemberType.field("items").type as ArrayType
         val itemsFieldMemberType = itemsFieldType.type as ObjectType
         itemsFieldMemberType.fields.should.have.size(2)
      }

      xit("should parse collection projection identifiers in queries") {
         val (schema, query) = """
            model Product {
               sku : ProductSku inherits String
               size : ProductSize inherits String
            }
            model TransactionItem {
               sku : ProductSku
            }
            model Transaction {
               items : TransactionItem[]
            }
         """.compiledWithQuery(
            """
            find { Transaction[] } as {
               items : {
                  sku : ProductSku
                  size : ProductSize
               }[] by [TransactionItem]
            }[]
         """
         )
         val resultCollectionType = query.projectedType!! as ArrayType
         val resultMemberType = resultCollectionType.type as ObjectType
         val itemsField = resultMemberType.field("items")
         itemsField.accessor!!.asA<CollectionProjectionExpressionAccessor>().type.qualifiedName.should.equal("TransactionItem")
      }

      xit("is possible to define projection specs on a top level return value") {
         val (schema, query) = """model Musical {
            title : MusicalTitle inherits String
            year : YearProduced inherits Int
         }
         model Composer {
            name : ComposerName inherits String
            majorWorks : { musicals : Musical[] }
         }""".compiledWithQuery(
            """find { Composer } as {
               name : ComposerName
               title : MusicalTitle
               year: YearProduced
            }[] by [Musical with ( ComposerName )]"""
         )
         val collectionType = query.projectedType!! as ArrayType
         val expression =
            (collectionType.typeParameters()[0] as ObjectType).expression!! as CollectionProjectionExpressionAccessor
         expression.type.qualifiedName.should.equal("Musical")
         expression.projectionScope!!.accessors.should.have.size(1)
      }

      it("should support annotations on anonymous projection types") {
         val (schema, query) = """
         model Composer {
            name : ComposerName inherits String
         }""".compiledWithQuery(
            """find { Composer } as @HelloWorld {
               name : ComposerName
            }"""
         )
         query.projectedType!!.asA<ObjectType>().annotations.should.have.size(1)
      }

      xit("should parse collection projection identifiers with additional scopes in queries") {
         val (schema, query) = """
            model Product {
               sku : ProductSku inherits String
               size : ProductSize inherits String
            }
            model TransactionItem {
               sku : ProductSku
            }
            model Transaction {
               items : TransactionItem[]
            }
         """.compiledWithQuery(
            """
            find { Transaction[] } as {
               items : {
                  sku : ProductSku
                  size : ProductSize
               }[] by [TransactionItem with ( Product, 2 + 4, "Jimmy" )]
            }[]
         """
         )
         val resultCollectionType = query.projectedType!! as ArrayType
         val resultMemberType = resultCollectionType.type as ObjectType
         val itemsField = resultMemberType.field("items")
         val projectionAccessor = itemsField.accessor!!.asA<CollectionProjectionExpressionAccessor>()
         projectionAccessor.type.qualifiedName.should.equal("TransactionItem")
         projectionAccessor.projectionScope!!.accessors.should.have.size(3)
      }

      it("correctly resolves short names in queries") {
         val (schema, query) = """
            namespace io.films {
               model Film {
                 id : FilmId inherits String
                 name : FilmTitle inherits String
               }
            }
         """.compiledWithQuery(
            // In the below query, we don't import or use the fully qualified names,
            // however because the names are unambiguous, we expect the compiler to resolve them
            """
            find { Film } as {
              foo : FilmId
              name : FilmTitle
             }
         """
         )
         query.typesToFind.single().typeName.parameterizedName.should.equal("io.films.Film")
         query.projectedType!!.asA<ObjectType>().field("foo").type.qualifiedName.should.equal("io.films.FilmId")
      }


      it("is valid to declare projections on fields in projected types") {
         val (schema, query) = """model Person {
            name : PersonName inherits String
            }
         """.compiledWithQuery(
            """find { Person } as {
             name : PersonName
             // Projection on a field type
             other : Person as {
               nickName : PersonName
            }
         }
         """.trimIndent()
         )
         val field = query.projectedType!!.asA<ObjectType>()
            .field("other")
         field.type.qualifiedName.should.not.equal("Person")
         field.type.qualifiedName.should.startWith("Anonymous")
         val type = field.type.asA<ObjectType>()
         type.field("nickName").type.qualifiedName.should.equal("PersonName")
      }

      it("is possible to project the result of an expression to an existing type") {
         val (schema, query) = """
                     model Actor {
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
               // This is the test...
               // using "as" to project Actor to ActorName
               star : singleBy(film.cast, (Actor) -> Actor::ActorId, film.headliner) as ActorName
            }[]

         """.trimIndent()
         )
         val field = query.projectedObjectType!!.field("star")
         field.type.qualifiedName.should.equal("ActorName")
         field.projection!!.sourceType.qualifiedName.shouldBe("Actor")
      }

      // This feature got broken while implementing named projection scopes.
      // However, it's unused, and the syntax isn't really standard with spread operators.
      // Lets re-introduce if we decide to revive the feature
      xit("is possible to select a subset of fields in an inline projection") {
         val (schema, query) = """
            model Person {
               firstName : FirstName inherits String
               lastName : LastName inherits String
               age : Age inherits Int
               city : CityName inherits String
            }
            model Film {
               title : FilmTitle inherits String
               actors : Person[]
            }
         """.compiledWithQuery(
            """find { Film } as {
            | title : FilmTitle
            | actors : Person[] as {
            |    firstName
            |    lastName
            |}[]
            |}
         """.trimMargin()
         )
         println(query)
         val actors = query.projectedObjectType!!.field("actors")
         actors.type.asA<ObjectType>()
      }


      // This feature has been disabled for now.
      xit("by should be supported with an anonymously typed field") {
         val taxiDoc = Compiler(
            """
         type QtyFill inherits Decimal
         type UnitMultiplier inherits Decimal
         type FilledNotional inherits Decimal
         type InputId inherits String
         type TraderId inherits String
         type TraderName inherits String
         type TraderSurname inherits String

         model InputModel {
           multiplier: UnitMultiplier by default(2)
           qtyFill: QtyFill
           id: InputId
         }

         model OutputModel {
            qtyHit : QtyFill?
            unitMultiplier: UnitMultiplier?
            filledNotional : FilledNotional?  by (this.qtyHit * this.unitMultiplier)
            traderId: TraderId by default("id1")
         }

         model TraderInfo {
            @Id
            traderId: TraderId
            traderName: TraderName
            traderSurname: TraderSurname
         }

         @Datasource
         service MultipleInvocationService {
            operation getInputData(): InputModel[]
         }

         service TraderService {
            operation getTrader(TraderId): TraderInfo
         }
      """.trimIndent()
         ).compile()

         val queryString = """
            find {
                InputModel[]
              } as OutputModel {
                 inputId: InputId
                 trader: {
                    name: TraderName
                    surname: TraderSurname
                 } by (this.traderId)
               }[]
            """.trimIndent()
         val queries = Compiler(source = queryString, importSources = listOf(taxiDoc)).queries()
         val query = queries.first()
         val anonymousTypeDefinition =
            query.projectedType!!.typeParameters().first() as ObjectType
         anonymousTypeDefinition.hasField("trader").should.be.`true`
         val traderField = anonymousTypeDefinition.field("trader")
         traderField.accessor.should.not.be.`null`
      }

      it("should support map as a query directive") {
         val query = """
            model Film {
               filmId : FilmId inherits String
               title : FilmTitle inherits String
            }
            model Musical {
               musicalId : MusicalId inherits String
            }
            query convertFilm( @RequestBody input : Film[] ) {
               // This is saying "First convert each Film to a Musical, then project"
               map { Musical } as {
                  id : MusicalId
                  filmId : FilmId
                  title : FilmTitle
               }[] // The result is an array, because the input was a Film[]
            }
         """.compiled()
            .query("convertFilm")

         query.queryMode.shouldBe(QueryMode.MAP)
         query.projectedType.shouldNotBeNull()
         query.projectedType.shouldBeInstanceOf<ArrayType>()
         val projectedFields = query.projectedObjectType!!.fields
         projectedFields.map { it.name }.shouldContainAll(
            "id","filmId","title"
         )
      }

      it("is an error to project a nested array to an object") {
         val exception = """
            model Actor {
               name : PersonName inherits String
            }
            model Film {
               title : FilmTitle inherits String
               cast : Actor[]
            }
         """.compiledWithQueryProducingCompilationException(
            """find { Film } as {
               | title : FilmTitle
               | cast : Actor[] as {
               |  personName : PersonName
               | }
               |}
            """.trimMargin()
         )
         exception.shouldNotBeNull()
         exception.errors.single().detailMessage.shouldBe("Illegal assignment. Cannot project an array to a non-array. Are you missing a [] after the projection?")
      }
   }
})
