package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.accessors.CollectionProjectionExpressionAccessor
import lang.taxi.accessors.FieldSourceAccessor
import lang.taxi.types.ArrayType
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName
import lang.taxi.types.QueryMode
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TaxiQlSpec : DescribeSpec({
   describe("Taxi Query Language") {
      val schema = """
         namespace foo

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
      it("Should Allow anonymous projected type definition") {
         val src = """
            import foo.Order
            import foo.OutputOrder

            find {
               Order[]( TradeDate  >= startDate , TradeDate < endDate )
            } as {
               tradeTimestamp
            }[]
      """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType.should.not.be.`null`
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousProjectedType")
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
                       Order[]( TradeDate >= startDate , TradeDate < endDate )
                    } as OutputOrder[]
                 }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.parameters.should.have.size(2)
         query.parameters.should.equal(
            mapOf(
               "startDate" to QualifiedName.from("lang.taxi.Instant"),
               "endDate" to QualifiedName.from("lang.taxi.Instant")
            )
         )
         query.projectedType?.toQualifiedName()?.parameterizedName.should.equal("lang.taxi.Array<foo.OutputOrder>")
         query.typesToFind.should.have.size(1)
         query.typesToFind.first().type.parameterizedName.should.equal("lang.taxi.Array<foo.Order>")
      }


      it("should compile a named query with params") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder
                 import foo.TradeDate

                 query RecentOrdersQuery( startDate:Instant, endDate:Instant ) {
                    find {
                       Order[]( TradeDate >= startDate , TradeDate < endDate )
                    } as OutputOrder[]
                 }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.name.should.equal("RecentOrdersQuery")
         query.parameters.should.have.size(2)
         query.parameters.should.equal(
            mapOf(
               "startDate" to QualifiedName.from("lang.taxi.Instant"),
               "endDate" to QualifiedName.from("lang.taxi.Instant")
            )
         )
         query.projectedType?.toQualifiedName()?.parameterizedName.should.equal("lang.taxi.Array<foo.OutputOrder>")
         query.typesToFind.should.have.size(1)

         val typeToFind = query.typesToFind.first()
         typeToFind.type.parameterizedName.should.equal("lang.taxi.Array<foo.Order>")
         typeToFind.constraints.should.have.size(2)
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
         fact.fqn.should.equal(QualifiedName("foo", "CustomerEmailAddress"))
         fact.value.should.equal("jimmy@demo.com")

         query.typesToFind.should.have.size(1)
         val discoveryType = query.typesToFind.first()
         discoveryType.type.fullyQualifiedName.should.equal("foo.Trade")
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
         name.should.be.`null`
         fact.fqn.should.equal(QualifiedName("foo", "CustomerEmailAddress"))
         fact.value.should.equal("jimmy@demo.com")

      }

      it("should compile an unnamed query") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
                 }
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         queries.first()
      }



      it("Should not Allow anonymous projected type definitions with invalid field references") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
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
         fact.fqn.should.equal(QualifiedName("foo", "CustomerEmailAddress"))
         fact.value.should.equal("jimmy@demo.com")

         query.typesToFind.should.have.size(1)
         val discoveryType = query.typesToFind.first()
         discoveryType.type.fullyQualifiedName.should.startWith("Anonymous")
         discoveryType.startingFacts.should.have.size(1)
         discoveryType.startingFacts.should.equal(query.facts)
      }

      it("query body can be an anonymous projection with constraints") {
         val src = """
                 given { email : CustomerEmailAddress = "jimmy@demo.com"}
                 find {
                   tradeDate: TradeDate
                   traderId: TraderId
                 }  ( TradeDate  >= startDate , TradeDate < endDate )
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.facts.should.have.size(1)
         val (name, fact) = query.facts.first()
         name.should.equal("email")
         fact.fqn.should.equal(QualifiedName("foo", "CustomerEmailAddress"))
         fact.value.should.equal("jimmy@demo.com")

         query.typesToFind.should.have.size(1)
         val discoveryType = query.typesToFind.first()
         discoveryType.anonymousType?.anonymous.should.be.`true`
         discoveryType.startingFacts.should.have.size(1)
         discoveryType.startingFacts.should.equal(query.facts)
         discoveryType.constraints.size.should.equal(2)
      }


      it("Should Allow anonymous type that extends base type") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
                 } as OutputOrder {
                    tradeTimestamp
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousProjectedType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("tradeTimestamp").should.be.`true`
         anonymousType.hasField("outputId").should.be.`true`
      }

      it("Should Not Allow anonymous type that extends base type when anonymous type reference a field that does not part of discovery type") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
                 } as OutputOrder {
                    invalidField
                 }[]
           """.trimIndent()
         val queryCompilationError =
            Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
         queryCompilationError.first().detailMessage.should.contain("should be an object type containing field invalidField")
      }

      it("Should Allow anonymous type that extends a base type and adds additional field definitions") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
                 } as OutputOrder {
                    insertedAt: foo.InsertedAt
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousProjectedType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("insertedAt").should.be.`true`
         anonymousType.inheritsFrom.should.have.size(1)
         anonymousType.fields.should.have.size(2)
         anonymousType.inheritedFields.should.have.size(1)
      }

      it("Should Allow anonymous type with field definitions") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
                 } as {
                    insertedAt: foo.InsertedAt
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousProjectedType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("insertedAt").should.be.`true`
      }


      it("Should Allow anonymous type with field definitions referencing type to discover") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
                 } as {
                    traderEmail: UserEmail by Order['traderId']
                 }[]
           """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.projectedType!!.anonymous.should.be.`true`
         query.projectedType!!.toQualifiedName().parameterizedName.should.contain("lang.taxi.Array<AnonymousProjectedType")
         val anonymousType = query.projectedType!!.typeParameters().first() as ObjectType
         anonymousType.hasField("traderEmail").should.be.`true`
         anonymousType.field("traderEmail").accessor.should.not.be.`null`
         val fieldSourceAccessor = anonymousType.field("traderEmail").accessor as FieldSourceAccessor
         fieldSourceAccessor.attributeType.should.equal(QualifiedName.from("foo.TraderId"))
         fieldSourceAccessor.sourceAttributeName.should.equal("traderId")
         fieldSourceAccessor.sourceType.should.equal(QualifiedName.from("foo.Order"))
      }


      it("Should Allow anonymous type with field definitions referencing projected type") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
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

      it("Should Allow anonymous type with field definitions referencing a type in the schema") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
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


      it("Should Fail anonymous type with field definitions referencing projected type but have invalid field type") {
         val src = """
                 import foo.Order
                 import foo.OutputOrder

                 find {
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
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
                    Order[]( TradeDate  >= startDate , TradeDate < endDate )
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
         query.typesToFind[0].type.parameterizedName.should.equal("lang.taxi.Array<foo.Order>")
      }


      it("should handle queries of array types with short syntax") {
         val src = """
                 import foo.Order

                 find { Order[] }
              """.trimIndent()
         val queries = Compiler(source = src, importSources = listOf(taxi)).queries()
         val query = queries.first()
         query.typesToFind[0].type.parameterizedName.should.equal("lang.taxi.Array<foo.Order>")
      }

      // This feature (referencing the parent view, and extending an projection type) is cool, but
      // has been disabled for npow
      xit("Should Allow anonymous type with complex field definitions referencing projected type") {
         val src = """
                      import foo.Order
                      import foo.OutputOrder

                      find {
                         Order[]( TradeDate  >= startDate , TradeDate < endDate )
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


      it("Should Detect anonymous type with invalid complex field definitions referencing projected type") {
         val src = """
                     import foo.Order
                     import foo.OutputOrder

                     find {
                        Order[]( TradeDate  >= startDate , TradeDate < endDate )
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
                               Order[]( TradeDate >= startDate , TradeDate < endDate )
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
                                   Order[]( TradeDate >= startDate , TradeDate < endDate )
                                } as {
                                   insertedAt: foo.InsertedAt
                                }
                             }
                          """.trimIndent()
         val queryCompilationError =
            Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
         queryCompilationError.first().detailMessage.should.contain("projection type is a list but the type to discover is not, both should either be list or single entity.")
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

      it("should parse collection projection identifiers in queries") {
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

      it("is possible to define projection specs on a top level return value") {
         val (schema,query) = """model Musical {
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
         val expression = (collectionType.typeParameters()[0] as ObjectType).expression!! as CollectionProjectionExpressionAccessor
         expression.type.qualifiedName.should.equal("Musical")
         expression.projectionScope!!.accessors.should.have.size(1)
      }

      it("should support annotations on anonymous projection types") {
         val (schema,query) = """
         model Composer {
            name : ComposerName inherits String
         }""".compiledWithQuery(
            """find { Composer } as @HelloWorld {
               name : ComposerName
            }"""
         )
         query.projectedType!!.asA<ObjectType>().annotations.should.have.size(1)
      }

      it("should parse collection projection identifiers with additional scopes in queries") {
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
         query.typesToFind.single().type.parameterizedName.should.equal("io.films.Film")
         query.projectedType!!.asA<ObjectType>().field("foo").type.qualifiedName.should.equal("io.films.FilmId")
      }

      it("is valid to declare projections on fields in projected types") {
         val (schema,query) = """model Person {
            name : PersonName inherits String
            }
         """.compiledWithQuery("""find { Person } as {
             name : PersonName
             // Projection on a field type
             other : Person as {
               nickName : PersonName
            }
         }
         """.trimIndent())
         val field = query.projectedType!!.asA<ObjectType>()
            .field("other")
         field.type.qualifiedName.should.not.equal("Person")
         field.type.qualifiedName.should.startWith("Anonymous")
         val type = field.type.asA<ObjectType>()
         type.field("nickName").type.qualifiedName.should.equal("PersonName")
      }

      it("is possible to select a subset of fields in an inline projection") {
         val (schema,query) = """
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
         """.compiledWithQuery("""find { Film } as {
            | title : FilmTitle
            | actors : Person[] as {
            |    firstName,
            |    lastName
            |}[]
         """.trimMargin())
         val actors = query.projectedObjectType.field("actors")
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
   }
})
