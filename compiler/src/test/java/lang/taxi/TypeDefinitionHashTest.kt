package lang.taxi

import com.google.common.hash.Hashing
import com.winterbe.expekt.should
import org.antlr.v4.runtime.CharStreams
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.todo

class TypeDefinitionHashTest {

   @Test
   fun `hash stays the same if nothing changes`() {
      val src = """
         type Symbol inherits String
      """.trimIndent()
      val taxiDocumentV1 = Compiler(src, "simple-schema").compile()
      val taxiDocumentV2 = Compiler(src, "simple-schema").compile()
      val typeV1 = taxiDocumentV1.type("Symbol")
      val typeV2 = taxiDocumentV2.type("Symbol")

      typeV1.definitionHash.should.equal("dba03a")
      typeV1.definitionHash.should.equal(typeV2.definitionHash)
   }

   @Test
   fun `hash changes when type changes`() {
      val srcV1 = """
         type Symbol inherits String
      """.trimIndent()
      val srcV2 = """
         type Symbol inherits Int
      """.trimIndent()
      val taxiDocumentV1 = Compiler(srcV1, "simple-schema").compile()
      val taxiDocumentV2 = Compiler(srcV2, "simple-schema").compile()
      val typeV1 = taxiDocumentV1.type("Symbol")
      val typeV2 = taxiDocumentV2.type("Symbol")

      typeV1.definitionHash.should.not.be.equal(typeV2.definitionHash)
   }

   @Test
   fun `hash changes when referenced type changes`() {
      val commonSrcV1 = """
         namespace common
         type Symbol inherits String
      """.trimIndent()
      val commonSrcV2 = """
         namespace common
         type Symbol inherits Int
      """.trimIndent()
      val srcV1 = """
         import common.Symbol
         type Symbol inherits common.Symbol
      """.trimIndent()
      val commonDocV1 = Compiler(commonSrcV1, "common-schema").compile()
      val commonDocV2 = Compiler(commonSrcV2, "common-schema").compile()
      val taxiDocV1 = Compiler(srcV1, "simple-schema", listOf(commonDocV1)).compile()
      val taxiDocV2 = Compiler(srcV1, "simple-schema", listOf(commonDocV2)).compile()
      val typeV1 = taxiDocV1.type("Symbol")
      val typeV2 = taxiDocV2.type("Symbol")

      typeV1.definitionHash.should.not.be.equal(typeV2.definitionHash)
   }

   @Test
   fun `hash changes when referenced type changes deep in dependency tree`() {
      val commonSrcV1 = """
         namespace common
         type ID inherits String
      """.trimIndent()
      val commonSrcV2 = """
         namespace common
         type ID inherits Int
      """.trimIndent()
      val baseSrcV1 = """
         import common.ID
         namespace order {
            type OrderId inherits common.ID
         }
      """.trimIndent()
      val srcV1 = """
         import order.OrderId
         type TestOrderId inherits order.OrderId
      """.trimIndent()
      val commonDocV1 = Compiler(commonSrcV1, "common-schema").compile()
      val commonDocV2 = Compiler(commonSrcV2, "common-schema").compile()
      val baseDocV1 = Compiler(baseSrcV1, "base-schema", listOf(commonDocV1)).compile()
      val baseDocV2 = Compiler(baseSrcV1, "base-schema", listOf(commonDocV2)).compile()
      val taxiDocV1 = Compiler(srcV1, "schema", listOf(baseDocV1)).compile()
      val taxiDocV2 = Compiler(srcV1, "schema", listOf(baseDocV2)).compile()
      val typeV1 = taxiDocV1.type("TestOrderId")
      val typeV2 = taxiDocV2.type("TestOrderId")

      typeV1.definitionHash.should.not.be.equal(typeV2.definitionHash)
   }

   @Test
   fun `hash changes when type extension is added`() {
      val extensionSrc = """
         type extension Order {
            @Indexed symbol
         }
      """.trimIndent()
      val src = """
         type Symbol inherits String
         type Order {
            symbol: Symbol
         }
      """.trimIndent()
      val schemaV1Compiler = Compiler(src, "schema")
      val schemaV2Compiler = Compiler(src, "schema")
      val taxiDocV1 = schemaV1Compiler.compile()
      val taxiDocV2 = Compiler(extensionSrc, "extension-schema", listOf(schemaV2Compiler.compile())).compile()
      val typeV1 = taxiDocV1.type("Order")
      val typeV2 = taxiDocV2.type("Order")

      typeV1.definitionHash.should.not.be.equal(typeV2.definitionHash)
   }

   @Test
   fun `hash changes when type extension is changed`() {
      val extensionSrcV1 = """
         type extension Order {
            @Indexed symbol
         }
      """.trimIndent()
      val extensionSrcV2 = """
         @Entity
         type extension Order {
         }
      """.trimIndent()
      val src = """
         type Symbol inherits String
         type Order {
            symbol: Symbol
         }
      """.trimIndent()
      val schemaV1Compiler = Compiler(src, "schema")
      val schemaV2Compiler = Compiler(src, "schema")
      val taxiDocV1 = Compiler(extensionSrcV1, "extension-schema", listOf(schemaV1Compiler.compile())).compile()
      val taxiDocV2 = Compiler(extensionSrcV2, "extension-schema", listOf(schemaV2Compiler.compile())).compile()
      val typeV1 = taxiDocV1.type("Order")
      val typeV2 = taxiDocV2.type("Order")

      typeV1.definitionHash.should.not.be.equal(typeV2.definitionHash)
   }

   @Test
   fun `enum synonyms cause non deterministic hash calculation`() {
      val commonFile = "/common/Common.taxi"
      val commonSrc = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open,
            Filled,
            Withheld
         }
         enum extension OrderEventType {
            @Indexed
            Open,
            @Indexed
            Filled
         }
      """.trimIndent()
      val commonOrderFile = "/common/order/Orders.taxi"
      val commonOrder = """
         namespace common.order
         model Order {
         }
      """.trimIndent()
      val broker2File = "/broker2/Orders.taxi"
      val broker2Src = """
         import common.order.Order
         import common.OrderId
         import common.OrderEventType
         namespace broker2
         model Order inherits common.order.Order {
            orderId: common.OrderId
            entryType: common.OrderEventType
         }
         enum EntryType {
            Opened synonym of OrderEventType.Open,
            WithHeld synonym of OrderEventType.Withheld
         }
      """.trimIndent()
      val broker1File = "/broker3/Orders.taxi"
      val broker1Src = """
         import common.order.Order
         import common.OrderId
         import common.OrderEventType
         namespace broker1
         model Order inherits common.order.Order {
            orderId: common.OrderId
            entryType: EntryType
         }
         enum EntryType {
            Opened synonym of OrderEventType.Open,
            WithHeld synonym of OrderEventType.Withheld
         }
      """.trimIndent()

      for (i in 1..100) {
         val inputsV1 = listOf(
            CharStreams.fromString(commonSrc, commonFile),
            CharStreams.fromString(commonOrder, commonOrderFile),
            CharStreams.fromString(broker2Src, broker2File),
            CharStreams.fromString(broker1Src, broker1File)
         )
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()
         val inputsV2 = listOf(
            CharStreams.fromString(commonSrc, commonFile),
            CharStreams.fromString(commonOrder, commonOrderFile),
            CharStreams.fromString(broker2Src, broker2File),
            CharStreams.fromString(broker1Src, broker1File)
         )
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()

         docV1.type("broker2.Order").definitionHash
            .should.be.equal(docV2.type("broker2.Order").definitionHash)
         docV1.type("broker1.Order").definitionHash
            .should.be.equal(docV2.type("broker1.Order").definitionHash)
      }
   }

   @Test
   fun `hash does not change when comments are added to the type`() {
      val commonFile = "/common/Common.taxi"
      val commonV1 = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open,
            Filled
         }
      """.trimIndent()
      val commonV2 = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open,
            Filled
            // added comment that should change hash
         }
      """.trimIndent()
      val broker1File = "/broker1/Orders.taxi"
      val broker1 = """
         namespace broker1
         model Order {
            orderId: common.OrderId
            entryType: common.OrderEventType
         }
      """.trimIndent()

      for (i in 1..100) {
         val inputsV1 = listOf(CharStreams.fromString(commonV1, commonFile), CharStreams.fromString(broker1, broker1File))
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()
         val inputsV2 = listOf(CharStreams.fromString(commonV2, commonFile), CharStreams.fromString(broker1, broker1File))
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()
         docV1.type("broker1.Order").definitionHash
            .should.not.be.equal(docV2.type("broker1.Order").definitionHash)
      }
   }

   @Test
   fun `hash does not change when comments are added to a type that is not reference`() {
      val commonSrcFile = "/common/Common.taxi"
      val commonSrcV1 = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open
         }
         model FixedIncomeCrossCurrencySwaps {
            id: OrderId
            eventType: OrderEventType
         }
      """.trimIndent()
      val commonSrcV2 = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open
         }
         model FixedIncomeCrossCurrencySwaps {
            id: OrderId
            // commented out in v2 to prove it does not affect hashing
            //eventType: OrderEventType
         }
      """.trimIndent()
      val broker1File = "/broker1/Orders.taxi"
      val broker1 = """
         namespace broker1
         model Order {
            orderId: common.OrderId
            entryType: common.OrderEventType
         }
      """.trimIndent()

      for (i in 1..100) {
         val inputsV1 = listOf(CharStreams.fromString(commonSrcV1, commonSrcFile), CharStreams.fromString(broker1, broker1File))
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()
         val inputsV2 = listOf(CharStreams.fromString(commonSrcV2, commonSrcFile), CharStreams.fromString(broker1, broker1File))
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()
         docV1.type("broker1.Order").definitionHash
            .should.be.equal(docV2.type("broker1.Order").definitionHash)
      }
   }

   @Test
   fun `hash does not change when adding new service to the schema`() {
      val commonSrcFile = "/common/Common.taxi"
      val commonSrcV1 = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open,
            Filled
         }
      """.trimIndent()
      val broker1File = "/broker1/Orders.taxi"
      val broker1 = """
         namespace broker1
         model Order {
            orderId: common.OrderId
            entryType: common.OrderEventType
         }
      """.trimIndent()
      val broker1ServiceFile = "/broker1/service"
      val broker1Service = """
         import common.OrderId
         namespace broker1

         service UserService {
            @HttpOperation(method = "GET" , url = "/client/orderId/{common.OrderId}")
            operation getOrderById( @PathVariable(name = "userId") userId : common.OrderId) : Order
         }
      """.trimIndent()

      for (i in 1..100) {
         val inputsV1 = listOf(
            CharStreams.fromString(commonSrcV1, commonSrcFile),
            CharStreams.fromString(broker1, broker1File))
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()

         val inputsV2 = listOf(
            CharStreams.fromString(commonSrcV1, commonSrcFile),
            CharStreams.fromString(broker1, broker1File),
            CharStreams.fromString(broker1Service, broker1ServiceFile))
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()

         docV1.type("broker1.Order").definitionHash
            .should.be.equal(docV2.type("broker1.Order").definitionHash)
      }
   }

   @Test
   fun `hash changes when white characters are added to type definition`() {
      generateHash("enum CurrencyCode {EUR}").should.equal("84f7bd")
      generateHash("enum CurrencyCode {EUR} ").should.equal("27c555")
      generateHash("enum CurrencyCode { EUR }").should.equal("a367df")
      generateHash("enum CurrencyCode { \t\rEUR\t\r }").should.equal("ac08ab")
      generateHash("enum CurrencyCode { \nEUR\n }").should.equal("261227")
   }

   @Test
   fun `hash changes when format changes`() {
      val formatHash1 = generateHash("""type Person { birthDate : @Format( 'dd-mmm-yyyy' ) Date  }""")
      val formatHash2 = generateHash("""type Person { birthDate : @Format( 'dd/mmm/yyyy' ) Date }""")
      formatHash1.should.not.equal(formatHash2)

      val birthDate = "type BirthDate inherits Date \n"
      val formatHash3 = generateHash("""$birthDate type Person { birthDate : @Format( 'dd/mmm/yyyy' ) BirthDate  }""")
      val formatHash4 = generateHash("""$birthDate type Person { birthDate : @Format( 'dd-mmm-yyyy' ) BirthDate  }""")
      formatHash3.should.not.equal(formatHash4)
   }

   private fun generateHash(input: String): String {
      val hasher = Hashing.sha256().newHasher()
      hasher.putUnencodedChars(input)
      return hasher.hash().toString().substring(0, 6)
   }

   @Test
   fun `hash does not change when taxonomy files moved to a different location`() {
      val commonFile = "/taxonomy/src/common/CommonTypes.taxi"
      val common = """
         namespace common
         enum TimeInForce {
            Day(0),
            GTC(1)
         }
         enum ValidityPeriod {
            DAVY synonym of TimeInForce.Day,
            GTCV synonym of TimeInForce.GTC
         }
      """.trimIndent()
      val broker1OrderFile = "/taxonomy/src/broker1/Order.taxi"
      val broker1Order = """
         import common.ValidityPeriod
         namespace broker1
         model Order {
            validityPeriod : ValidityPeriod
         }
      """.trimIndent()
      val broker2OrderFile = "/taxonomy/src/broker2/Order.taxi"
      val broker2Order = """
         import common.TimeInForce
         namespace broker2
         model Order {
            timeInForce : Broker2TimeInForce
            timeInForceStr : Broker2TimeInForceStr
         }
         enum Broker2TimeInForce {
             DAY(0) synonym of TimeInForce.Day,
             GTC(1) synonym of TimeInForce.GTC
         }
         enum Broker2TimeInForceStr {
             DAY synonym of TimeInForce.Day,
             GTC synonym of TimeInForce.GTC
         }
      """.trimIndent()
      val broker3OrderFile = "/taxonomy/src/broker3/Order.taxi"
      val broker3Order = """
         import common.TimeInForce
         namespace broker3
         model Order {
            timeInForce: Broker3TimeInForce
         }
         enum Broker3TimeInForce {
             Gtc synonym of TimeInForce.GTC,
             DayOpen synonym of TimeInForce.Day
         }

      """.trimIndent()

      val oldDir = "/old-location/"
      val newDir = "/new-location/"

      for (i in 1..50) {
         val inputsV1 = listOf(
            CharStreams.fromString(common, "$oldDir$commonFile"),
            CharStreams.fromString(broker1Order, "$oldDir$broker1OrderFile"),
            CharStreams.fromString(broker3Order, "$oldDir$broker3OrderFile"),
            CharStreams.fromString(broker2Order, "$oldDir$broker2OrderFile")
         )
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()

         val inputsV2 = listOf(
            CharStreams.fromString(common, "$newDir$commonFile"),
            CharStreams.fromString(broker1Order, "$newDir$broker1OrderFile"),
            CharStreams.fromString(broker3Order, "$newDir$broker3OrderFile"),
            CharStreams.fromString(broker2Order, "$newDir$broker2OrderFile")
         )
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()

         docV1.type("broker1.Order").definitionHash
            .should.be.equal(docV2.type("broker1.Order").definitionHash)
         docV1.type("broker2.Order").definitionHash
            .should.be.equal(docV2.type("broker2.Order").definitionHash)
      }
   }

   @Test
   fun `hash does not change for generated formatted types`() {
      val commonFile = "/common/Common.taxi"
      val commonSrc = """
         namespace common
         type EventDateTime inherits Instant
      """.trimIndent()
      val broker1File = "/broker1/Order.taxi"
      val broker1Src = """
         import common.EventDateTime
         namespace broker1
         model Order {
            @Between
            @Format("yyyy-MM-dd HH:mm:ss.SSSSSSS")
            broker1OrderDateTime1 : EventDateTime
            @Format("yyyy-MM-dd HH:mm:ss.SSS")
            broker1OrderDateTime2 : EventDateTime
            @Format("yyyy-MM-dd HH:mm:ss")
            broker1OrderDateTime3 : EventDateTime
            @Format("yyyy-MM-dd HH:mm:ss.SSSSSSS")
            broker1OrderDateTime4 : EventDateTime
         }
      """.trimIndent()
      val broker2File = "/broker2/Order.taxi"
      val broker2Src = """
         import common.EventDateTime
         namespace broker2
         model Order {
            @Format("yyyy-MM-dd HH:mm:ss.SSSSSSS")
            broker2OrderDateTime1 : EventDateTime
             @Format( "yyyy-MM-dd HH:mm:ss.SSS")
            broker2OrderDateTime2 : EventDateTime
            @Format( "yyyy-MM-dd HH:mm:ss")
            broker2OrderDateTime3 : EventDateTime
            @Between
            @Format( "yyyy-MM-dd HH:mm:ss.SSSSSSS")
            broker2OrderDateTime4 : EventDateTime
         }
      """.trimIndent()

      for (i in 1..100) {
         val inputsV1 = listOf(CharStreams.fromString(commonSrc, commonFile), CharStreams.fromString(broker1Src, broker1File), CharStreams.fromString(broker2Src, broker2File))
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()

         val inputsV2 = listOf(CharStreams.fromString(commonSrc, commonFile), CharStreams.fromString(broker1Src, broker1File), CharStreams.fromString(broker2Src, broker2File))
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()

         docV1.type("broker1.Order").definitionHash
            .should.be.equal(docV2.type("broker1.Order").definitionHash)
         docV1.type("broker2.Order").definitionHash
            .should.be.equal(docV2.type("broker2.Order").definitionHash)
      }
   }
}
