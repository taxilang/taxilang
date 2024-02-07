package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import lang.taxi.messages.Severity
import lang.taxi.types.PrimitiveType

class FormattedTypesSpec : DescribeSpec({
   describe("formatted types") {
      it("should expose default formats for date types") {
         val doc = Compiler("").compile()
         doc.type(PrimitiveType.INSTANT.toQualifiedName()).format?.first().should.equal("yyyy-MM-dd'T'HH:mm:ss[.SSS]X")
      }

      it("should inherit default formats") {
         """type TradeDate inherits Instant"""
            .compiled()
            .type("TradeDate")
            .format.should.equal(PrimitiveType.INSTANT.format)
      }

      it("should inherit default formats on fields") {
         val doc = """
         type BirthDate inherits Date

         model Person {
            birthDate : BirthDate
         }
         """.compiled()

         doc.type("BirthDate").format.shouldNotBeNull()
         doc.type("BirthDate").formatAndZoneOffset.shouldNotBeNull()
         doc.model("Person")
            .field("birthDate")
            .format.shouldNotBeNull()

      }

      it("should expose format on field") {
         val format = """
            type TransactionEventDateTime inherits Instant
            type Order {
                @Format("yyyy-MM-dd HH:mm:ss.SSSSSSS")
                orderDateTime : TransactionEventDateTime
            }
         """.trimMargin()
            .compiled()
            .objectType("Order").field("orderDateTime")
            .formatAndZoneOffset
         format!!.patterns.shouldBe(listOf("yyyy-MM-dd HH:mm:ss.SSSSSSS"))
      }

      it("should parse formatted types") {
         val src = """
            @Format('mm/dd/yyThh:nn:ss.mmmmZ')
            type TradeDate inherits Instant
         """.compiled()
         src.type("TradeDate").format?.first().should.equal("mm/dd/yyThh:nn:ss.mmmmZ")
      }

      it("should parse formatted types with single quotes within double quotes") {
         val doc = """
            type TransactionEventDateTime inherits Instant
            type Order {
               @Format("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
               @Format("yyyy-MM-dd'T'HH:mm:ss.SSS")
               orderDateTime : TransactionEventDateTime
            }
         """.trimMargin()
            .compiled()

         val format = doc.objectType("Order")
            .field("orderDateTime").format!!
         format.first().should.equal("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
         format[1].should.equal("yyyy-MM-dd'T'HH:mm:ss.SSS")
      }

      it("should parse multiple formatted types with single quotes") {
         val doc = """
            type TransactionEventDateTime inherits Instant
            type TransactionEventDate inherits Date
            type Order {
                @Format( 'yyyy-MM-dd HH:mm:ss.SSSSSSS' )
                orderDateTime : TransactionEventDateTime
                @Format('yyyyMMdd')
                orderDate : TransactionEventDate
            }
         """.trimMargin()
            .compiled()

         doc.objectType("Order").field("orderDateTime").format?.first().should.equal("yyyy-MM-dd HH:mm:ss.SSSSSSS")
         doc.objectType("Order").field("orderDate").format?.first().should.equal("yyyyMMdd")
      }

      it("should parse multiple formatted types with different quotes") {
         val doc = """
            type TransactionEventDateTime inherits Instant
            type TransactionEventDate inherits Date
            type Order {
                @Format('yyyy-MM-dd HH:mm:ss.SSSSSSS')
                orderDateTimeQuote : TransactionEventDateTime
                @Format("yyyy/MM/dd HH:mm:ss")
                orderDateTimeDoubleQuote : TransactionEventDateTime
                @Format('yyyyMMdd')
                orderDateQuote : TransactionEventDate
                @Format( "yyyy/MM/dd" )
                orderDateDoubleQuote : TransactionEventDate
            }
         """.trimMargin()
            .compiled()

         doc.objectType("Order")
            .field("orderDateTimeQuote").format?.first().should.equal("yyyy-MM-dd HH:mm:ss.SSSSSSS")
         doc.objectType("Order")
            .field("orderDateTimeDoubleQuote").format?.first().should.equal("yyyy/MM/dd HH:mm:ss")
         doc.objectType("Order").field("orderDateQuote").format?.first().should.equal("yyyyMMdd")
         doc.objectType("Order").field("orderDateDoubleQuote").format?.first().should.equal("yyyy/MM/dd")
      }

      it("should allow modifying formats inline") {
         """
            @Format("mm/dd/yyThh:nn:ss.mmmmZ")
            type TradeDate inherits Instant

            type Trade {
               @Format('dd/yy/mm')
               tradeDate : TradeDate
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").format?.first().should.equal("dd/yy/mm")
      }

      it("should allow a subtype to inherit the format of its base type") {
         """
            @Format( "dd/yy/mm" )
            type TradeDate inherits Instant
            type SettlementDate inherits TradeDate
            type Trade {
               tradeDate : SettlementDate
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").format?.first().should.equal("dd/yy/mm")

      }

      it("should allow a subtype to override the format of its base type") {
         """
            @Format( "dd/yy/mm" )
            type TradeDate inherits Instant
            @Format( "mm/yy/dd" )
            type SettlementDate inherits TradeDate
            type Trade {
               tradeDate : SettlementDate
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").format?.first().should.equal("mm/yy/dd")


      }

      it("should allow positive offset specification for Instant based types") {
         val tradeDateType = """
            @Format(value = 'mm/dd/yyThh:nn:ss.mmmmZ', offset = 60)
            type TradeDate inherits Instant
         """.compiled().type("TradeDate")

         tradeDateType.format?.first().should.equal("mm/dd/yyThh:nn:ss.mmmmZ")
         tradeDateType.offset.should.equal(60)
      }

      it("should allow negative offset specification for Instant based types") {
         val tradeDateType = """
            @Format(value = 'mm/dd/yyThh:nn:ss.mmmmZ', offset = -300)
            type TradeDate inherits Instant
         """.compiled().type("TradeDate")

         tradeDateType.format?.first().should.equal("mm/dd/yyThh:nn:ss.mmmmZ")
         tradeDateType.offset.should.equal(-300)
      }

      it("should not allowed offset definition for date based types") {
         val errors = """
            type TransactionEventDate inherits Date
            model Order {
                @Format(value = 'yyyyMMdd', offset = -300 )
                orderDateQuote : TransactionEventDate
            }
         """.trimMargin()
            .validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("Offset is only applicable to Instant based types")
      }

      it("should define formats on fields") {
         val schema = """
            @Format("dd/MM/yy'T'HH:mm:ss" )
            type MyDate inherits Instant

            model Person {
               fromType : MyDate
               @Format("yyyy-MM-dd HH:mm:ss")
               fromTypeWithFormat : MyDate

               @Format(offset = 60)
               fromTypeWithOffset : MyDate
            }
      """.compiled()
         schema.type("MyDate").format!!.shouldContainExactly("dd/MM/yy'T'HH:mm:ss" )
         val person = schema.objectType("Person")
         person.field("fromType").formatAndZoneOffset!!.patterns.shouldContainExactly("dd/MM/yy'T'HH:mm:ss" )
         person.field("fromTypeWithFormat").formatAndZoneOffset!!.patterns.shouldContainExactly("yyyy-MM-dd HH:mm:ss")
         person.field("fromTypeWithOffset").formatAndZoneOffset!!.patterns.shouldContainExactly("dd/MM/yy'T'HH:mm:ss" )
         person.field("fromTypeWithOffset").formatAndZoneOffset!!.utcZoneOffsetInMinutes!!.shouldBe(60)
      }

      it("should allow offset only declaration") {
         val type = """
            @Format(offset = 60)
            type SummerTime inherits Instant
         """.compiled()
            .type("SummerTime")
         type.offset.shouldBe(60)
         type.format.shouldNotBeEmpty()
      }

      it("should take an inherited format from a type that defines only an offset") {
         val type = """
            @Format(offset = 60)
            type SummerTime inherits MyFormat

            @Format(value = "dd/MM/yyThh:nn:ssZ")
            type MyFormat inherits Instant
         """.compiled()
            .type("SummerTime")
         type.offset.shouldBe(60)
         type.format.shouldContainExactly("dd/MM/yyThh:nn:ssZ")
      }

      it("should take an inherited format pattern from a field that defined only an offset") {
         val field = """
              @Format(value = "dd/MM/yyThh:nn:ssZ")
            type MyFormat inherits Instant

            model Person {
               @Format(offset = 60)
               dateOfBirth: MyFormat
            }
         """.compiled()
            .model("Person")
            .field("dateOfBirth")
         field.format.shouldContainExactly("dd/MM/yyThh:nn:ssZ")
         field.offset.shouldBe(60)
      }

      it("should not allowed offset definition for time based types") {
         val errors = """
            type TransactionEventTime inherits Time
            model Order {
                @Format(value = 'HH:mm:ss', offset = -300 )
                orderTimeQuote : TransactionEventTime
            }
         """.trimMargin()
            .validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("Offset is only applicable to Instant based types")
      }

      it("should allow multiple formats with or without offset values for Instant based fields") {
         val orderEventDateTimeType = """
            @Format("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
            @Format(value = "yyyy-MM-dd'T'HH:mm:ss'Z'", offset = 240 )
            type OrderEventDateTime inherits Instant
         """.trimIndent()
            .compiled().type("OrderEventDateTime")
         orderEventDateTimeType.offset.should.equal(240)
      }

      it("should not allow invalid offset specification for Instant based types") {
         //// https://en.wikipedia.org/wiki/List_of_UTC_time_offsets - time offsets range [UTC-12, UTC+14]
         val errors = """
            @Format(value = 'mm/dd/yyThh:nn:ss.mmmmZ', offset = 900 )
            type TradeDate inherits Instant
         """.validated()
            .filter { it.severity == Severity.ERROR }
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("""Offset value can't be larger than 840 (UTC+14) or smaller than -720 (UTC-12)""")
      }

      xit("should  allow  offset only specification for Instant based types") {
         //// https://en.wikipedia.org/wiki/List_of_UTC_time_offsets - time offsets range [UTC-12, UTC+14]
         val tradeDateType = """
            @Format( offset = 30 )
            type TradeDate inherits Instant
         """.compiled().type("TradeDate")
         tradeDateType.offset.should.equal(30)
      }

      describe("Formatted string types") {
         // This is a first-pass mvp of the feature, adding as-needed to support xml parsing
         it("should allow pattern on string types") {
            // This test
            val currencyCode = """
            @Format("[A-Z]{3,3}")
            type CurrencyCode inherits String
         """.compiled().type("CurrencyCode")
            currencyCode.format.should.have.elements("[A-Z]{3,3}")
         }
      }


      describe("future formatted types - with format spec") {
         val formatDefinition = """
            format TemporalFormat {
                'yyyy' -> LongYear : Int
                'yy' -> ShortYear : Int
                'mon' -> ignorecase enum MonthName { JAN,FEB,MAR,APR,MAY,JUN,JUL,AUG,SEP,OCT,NOV,DEC } // Adds concept 'ignorecase enum'
                'm' -> MonthNumber : Int  // Do we need to support Padded / unpadded here?
                'mm' -> PaddedMonthNumber : Int(1..12) // Note - ranged types
                'd' -> DayOfMonth : Int(1..31)
                'dd' -> PaddedDayOfMonth : Int(1..31)
                'h' -> HourOfDay12Hr : Int(1..12)
                'hh' -> HourOfDay12Hr : Int(1..12)
                'HH' -> HourOfDay24Hr : Int(0..23)
                'p' -> ignorecase enum AmPm { AM, PM }
                'MM' -> Minute : Int(0..59)
                'SS' -> Second : Int(0..59)
                'L' -> Millisecond : Int(0..999)
                'Z' -> ZoneOffset : Int
                'EM' -> MillisecondSinceEpoch : Int
                'ES' -> SecondSinceEpoch : Int
                '-' | 'T' | ':' | '.' | '/' | ' ' -> Seperator : String
            }

            formatted type Instant( format : TemporalFormat ) {
                year : TemporalFormat.LongYear
                month : TemporalFormat.MonthNumber
                day : TemporalFormat.DayOfMonth
                hour : TemporalFormat.HourOfDay24Hr
                minute : TemporalFormat.Minute
                second : TemporalFormat.Second
                milli -> TemporalFormat.Millisecond
            }
            type IsoDateTime : Instant( format : 'yyyy-mm-ddTHH:MM:SS.LLLLZ' ) // Should match 2020-04-20T23:40:23.0Z
            type OddDateTime : Instant( format : 'mon/dd/yy hh:MM:ss p' )  // Should match Apr/23/20 11:40:23 PM


         """.trimIndent()

         xit("should be invalid to override a format without all the correct components") {

         }
      }

   }
})
