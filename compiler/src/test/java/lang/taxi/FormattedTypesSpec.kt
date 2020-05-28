package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.PrimitiveType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FormattedTypesSpec : Spek({
   describe("formatted types") {
      it("should expose default formats for date types") {
         val doc  = Compiler("").compile()
         doc.type(PrimitiveType.INSTANT.toQualifiedName()).format!!.should.equal("yyyy-MM-dd'T'HH:mm:ss[.SSS]X")
      }

      it("should inherit default formats") {
         """type TradeDate inherits Instant"""
            .compiled()
            .type("TradeDate")
            .format.should.equal(PrimitiveType.INSTANT.format)
      }

      it("should parse formatted types") {
         val src = """
            type TradeDate inherits Instant( @format = 'mm/dd/yyThh:nn:ss.mmmmZ' )
         """.compiled().type("TradeDate").format.should.equal("mm/dd/yyThh:nn:ss.mmmmZ")

      }

      it("should allow modifying formats inline") {
         """
            type TradeDate inherits Instant( @format = "mm/dd/yyThh:nn:ss.mmmmZ" )

            type Trade {
               tradeDate : TradeDate( @format = 'dd/yy/mm' )
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").type.format.should.equal("dd/yy/mm")
      }

      it("should allow a subtype to inherit the format of its base type") {
         """
            type TradeDate inherits Instant( @format = "dd/yy/mm" )
            type SettlementDate inherits TradeDate
            type Trade {
               tradeDate : SettlementDate
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").type.format.should.equal("dd/yy/mm")

      }

      it("should allow a subtype to override the format of its base type") {
         """
            type TradeDate inherits Instant( @format = "dd/yy/mm" )
            type SettlementDate inherits TradeDate( @format = "mm/yy/dd" )
            type Trade {
               tradeDate : SettlementDate
            }
         """.compiled()
            .objectType("Trade").field("tradeDate").type.format.should.equal("mm/yy/dd")


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
