package lang.taxi

import com.nhaarman.mockitokotlin2.eq
import com.winterbe.expekt.should
import lang.taxi.types.PrimitiveType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FormattedTypesSpec : Spek({
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

      it("should expose underlying unformatted type") {
         """type TransactionEventDateTime inherits Instant
            type Order {
                orderDateTime : TransactionEventDateTime( @format = "yyyy-MM-dd HH:mm:ss.SSSSSSS")
            }
         """.trimMargin()
            .compiled()
            .objectType("Order").field("orderDateTime").type
            .formattedInstanceOfType?.qualifiedName.should.equal("TransactionEventDateTime")
      }

      it("should parse formatted types") {
         val src = """
            type TradeDate inherits Instant( @format = 'mm/dd/yyThh:nn:ss.mmmmZ' )
         """.compiled().type("TradeDate").format?.first().should.equal("mm/dd/yyThh:nn:ss.mmmmZ")

      }

      it("should parse formatted types with single quotes within double quotes") {
         val doc = """
            type TransactionEventDateTime inherits Instant
            type Order {
                orderDateTime : TransactionEventDateTime( @format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", @format = "yyyy-MM-dd'T'HH:mm:ss.SSS")
            }
         """.trimMargin()
            .compiled()

         doc.objectType("Order").field("orderDateTime").type.format?.first().should.equal("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
         doc.objectType("Order").field("orderDateTime").type.format?.get(1).should.equal("yyyy-MM-dd'T'HH:mm:ss.SSS")
      }

      it("should parse multiple formatted types with single quotes") {
         val doc = """
            type TransactionEventDateTime inherits Instant
            type TransactionEventDate inherits Date
            type Order {
                orderDateTime : TransactionEventDateTime( @format = 'yyyy-MM-dd HH:mm:ss.SSSSSSS')
                orderDate : TransactionEventDate( @format = 'yyyyMMdd')
            }
         """.trimMargin()
            .compiled()

         doc.objectType("Order").field("orderDateTime").type.format?.first().should.equal("yyyy-MM-dd HH:mm:ss.SSSSSSS")
         doc.objectType("Order").field("orderDate").type.format?.first().should.equal("yyyyMMdd")
      }

      it("should parse multiple formatted types with different quotes") {
         val doc = """
            type TransactionEventDateTime inherits Instant
            type TransactionEventDate inherits Date
            type Order {
                orderDateTimeQuote : TransactionEventDateTime( @format = 'yyyy-MM-dd HH:mm:ss.SSSSSSS')
                orderDateTimeDoubleQuote : TransactionEventDateTime( @format = "yyyy/MM/dd HH:mm:ss")
                orderDateQuote : TransactionEventDate( @format = 'yyyyMMdd')
                orderDateDoubleQuote : TransactionEventDate( @format = "yyyy/MM/dd")
            }
         """.trimMargin()
            .compiled()

         doc.objectType("Order").field("orderDateTimeQuote").type.format?.first().should.equal("yyyy-MM-dd HH:mm:ss.SSSSSSS")
         doc.objectType("Order").field("orderDateTimeDoubleQuote").type.format?.first().should.equal("yyyy/MM/dd HH:mm:ss")
         doc.objectType("Order").field("orderDateQuote").type.format?.first().should.equal("yyyyMMdd")
         doc.objectType("Order").field("orderDateDoubleQuote").type.format?.first().should.equal("yyyy/MM/dd")
      }

      it("should allow modifying formats inline") {
         """
            type TradeDate inherits Instant( @format = "mm/dd/yyThh:nn:ss.mmmmZ" )

            type Trade {
               tradeDate : TradeDate( @format = 'dd/yy/mm' )
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").type.format?.first().should.equal("dd/yy/mm")
      }

      it("should allow a subtype to inherit the format of its base type") {
         """
            type TradeDate inherits Instant( @format = "dd/yy/mm" )
            type SettlementDate inherits TradeDate
            type Trade {
               tradeDate : SettlementDate
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").type.format?.first().should.equal("dd/yy/mm")

      }

      it("should allow a subtype to override the format of its base type") {
         """
            type TradeDate inherits Instant( @format = "dd/yy/mm" )
            type SettlementDate inherits TradeDate( @format = "mm/yy/dd" )
            type Trade {
               tradeDate : SettlementDate
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").type.format?.first().should.equal("mm/yy/dd")


      }

      it("should allow positive offset specification for Instant based types") {
         val tradeDateType = """
            type TradeDate inherits Instant( @format = ['mm/dd/yyThh:nn:ss.mmmmZ'], @offset = 60 )
         """.compiled().type("TradeDate")

         tradeDateType.format?.first().should.equal("mm/dd/yyThh:nn:ss.mmmmZ")
         tradeDateType.offset.should.equal(60)
      }

      it("should allow negative offset specification for Instant based types") {
         val tradeDateType = """
            type TradeDate inherits Instant( @format = ['mm/dd/yyThh:nn:ss.mmmmZ'], @offset = -300 )
         """.compiled().type("TradeDate")

         tradeDateType.format?.first().should.equal("mm/dd/yyThh:nn:ss.mmmmZ")
         tradeDateType.offset.should.equal(-300)
      }

      it("should not allowed offset definition for date based types") {
         val errors = """
            type TransactionEventDate inherits Date
            type Order {
                orderDateQuote : TransactionEventDate( @format = ['yyyyMMdd'], @offset = -300 )
            }
         """.trimMargin()
            .validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("@offset is only applicable to Instant based types")
      }

      it("should not allowed offset definition for time based types") {
         val errors = """
            type TransactionEventTime inherits Time
            type Order {
                orderTimeQuote : TransactionEventTime( @format = ['HH:mm:ss'], @offset = -300 )
            }
         """.trimMargin()
            .validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("@offset is only applicable to Instant based types")
      }

      it("should allow multiple formats with or without offset values for Instant based fields") {
         val orderEventDateTimeType = """
            type OrderEventDateTime inherits Instant( @format = ["yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.S'Z'"], @offset = 240)
         """.trimIndent()
            .compiled().type("OrderEventDateTime")
         orderEventDateTimeType.offset.should.equal(240)
      }

      it("should not allow invalid offset specification for Instant based types") {
         //// https://en.wikipedia.org/wiki/List_of_UTC_time_offsets - time offsets range [UTC-12, UTC+14]
         val errors = """
            type TradeDate inherits Instant( @format = ['mm/dd/yyThh:nn:ss.mmmmZ'] @offset = 900 )
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("""@offset value can't be larger than 840 (UTC+14) or smaller than -720 (UTC-12)""")
      }

      it("should  allow  offset only specification for Instant based types") {
         //// https://en.wikipedia.org/wiki/List_of_UTC_time_offsets - time offsets range [UTC-12, UTC+14]
         val tradeDateType = """
            type TradeDate inherits Instant( @offset = 30 )
         """.compiled().type("TradeDate")
         tradeDateType.offset.should.equal(30)
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
