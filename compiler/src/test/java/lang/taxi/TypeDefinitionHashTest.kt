package lang.taxi

import com.winterbe.expekt.should
import org.antlr.v4.runtime.CharStreams
import org.junit.Test
import java.util.*

class TypeDefinitionHashTest  {

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
         type HpcOrder inherits order.OrderId
      """.trimIndent()
      val commonDocV1 = Compiler(commonSrcV1, "common-schema").compile()
      val commonDocV2 = Compiler(commonSrcV2, "common-schema").compile()
      val baseDocV1 = Compiler(baseSrcV1, "base-schema", listOf(commonDocV1)).compile()
      val baseDocV2 = Compiler(baseSrcV1, "base-schema", listOf(commonDocV2)).compile()
      val taxiDocV1 = Compiler(srcV1, "schema", listOf(baseDocV1)).compile()
      val taxiDocV2 = Compiler(srcV1, "schema", listOf(baseDocV2)).compile()
      val typeV1 = taxiDocV1.type("HpcOrder")
      val typeV2 = taxiDocV2.type("HpcOrder")

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
      val taxiDocV1 = schemaV1Compiler.compile()
      val taxiDocV2 = Compiler(extensionSrc, "extension-schema", listOf(schemaV1Compiler.compile())).compile()
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
      val taxiDocV1 = Compiler(extensionSrcV1, "extension-schema", listOf(schemaV1Compiler.compile())).compile()
      val taxiDocV2 = Compiler(extensionSrcV2, "extension-schema", listOf(schemaV1Compiler.compile())).compile()
      val typeV1 = taxiDocV1.type("Order")
      val typeV2 = taxiDocV2.type("Order")

      typeV1.definitionHash.should.not.be.equal(typeV2.definitionHash)
   }

   @Test
   fun `enum synonyms cause non deterministic hash calculation`() {
      val commonSrcFile = "/common/Common.taxi"
      val semaforSrcFile = "/cemafor/Orders.taxi"
      val hpcSrcFile = "/hpc/Orders.taxi"
      val cacibSrcFile = "/cacib/Orders.taxi"
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
      val cacibSrc = """
         namespace cacib
         model Order {
         }
      """.trimIndent()
      val semaforSrc = """
         import cacib.Order
         import common.OrderId
         import common.OrderEventType
         namespace cemafor
         model Order inherits cacib.Order {
            orderId: common.OrderId
            entryType: common.OrderEventType
         }
         enum EntryType {
            Opened synonym of OrderEventType.Open,
            WithHeld synonym of OrderEventType.Withheld
         }
      """.trimIndent()
      val hpcSrc = """
         import cacib.Order
         import common.OrderId
         import common.OrderEventType
         namespace hpc
         model Order inherits cacib.Order {
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
            CharStreams.fromString(commonSrc, commonSrcFile),
            CharStreams.fromString(semaforSrc, semaforSrcFile),
            CharStreams.fromString(hpcSrc, hpcSrcFile),
            CharStreams.fromString(cacibSrc, cacibSrcFile)
         )
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()
         val inputsV2 = listOf(
            CharStreams.fromString(commonSrc, commonSrcFile),
            CharStreams.fromString(semaforSrc, semaforSrcFile),
            CharStreams.fromString(hpcSrc, hpcSrcFile),
            CharStreams.fromString(cacibSrc, cacibSrcFile)
         )
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()

         docV1.type("cemafor.Order").definitionHash
            .should.be.equal(docV2.type("cemafor.Order").definitionHash)
         docV1.type("hpc.Order").definitionHash
            .should.be.equal(docV2.type("hpc.Order").definitionHash)
      }
   }

   @Test
   fun `comment added a type that is referenced should change the hash`() {
      val commonSrcFile = "/common/Common.taxi"
      val cemaforSrcFile = "/cemafor/Orders.taxi"
      val commonSrcV1 = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open,
            Filled
         }
      """.trimIndent()
      val commonSrcV2 = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open,
            Filled
            // added comment that should change hash
         }
      """.trimIndent()
      val cemaforSrc = """
         namespace cemafor
         model Order {
            orderId: common.OrderId
            entryType: common.OrderEventType
         }
      """.trimIndent()

      for (i in 1..100) {
         val inputsV1 = listOf(CharStreams.fromString(commonSrcV1, commonSrcFile), CharStreams.fromString(cemaforSrc, cemaforSrcFile))
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()
         val inputsV2 = listOf(CharStreams.fromString(commonSrcV2, commonSrcFile), CharStreams.fromString(cemaforSrc, cemaforSrcFile))
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()
         docV1.type("cemafor.Order").definitionHash
            .should.not.be.equal(docV2.type("cemafor.Order").definitionHash)
      }
   }

   @Test
   fun `comment added to a type that is not reference should not change the hash`() {
      val commonSrcFile = "/common/Common.taxi"
      val cemaforSrcFile = "/cemafor/Orders.taxi"
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
      val cemaforSrc = """
         namespace cemafor
         model Order {
            orderId: common.OrderId
            entryType: common.OrderEventType
         }
      """.trimIndent()

      for (i in 1..100) {
         val inputsV1 = listOf(CharStreams.fromString(commonSrcV1, commonSrcFile), CharStreams.fromString(cemaforSrc, cemaforSrcFile))
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()
         val inputsV2 = listOf(CharStreams.fromString(commonSrcV2, commonSrcFile), CharStreams.fromString(cemaforSrc, cemaforSrcFile))
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()
         docV1.type("cemafor.Order").definitionHash
            .should.be.equal(docV2.type("cemafor.Order").definitionHash)
      }
   }

   @Test
   fun `adding new service to the schema should not change the hash`() {
      val commonSrcFile = "/common/Common.taxi"
      val cemaforSrcFile = "/cemafor/Orders.taxi"
      val cemaforServiceSrcFile = "/cemafor/service"
      val commonSrcV1 = """
         namespace common
         type OrderId inherits String
         enum OrderEventType {
            Open,
            Filled
         }
      """.trimIndent()
      val cemaforSrc = """
         namespace cemafor
         model Order {
            orderId: common.OrderId
            entryType: common.OrderEventType
         }
      """.trimIndent()
      val cemaforServiceSrc = """
         import common.OrderId
         namespace cemafor

         service UserService {
            @HttpOperation(method = "GET" , url = "/client/orderId/{common.OrderId}")
            operation getOrderById( @PathVariable(name = "userId") userId : common.OrderId) : Order
         }
      """.trimIndent()

      for (i in 1..100) {
         val inputsV1 = listOf(
            CharStreams.fromString(commonSrcV1, commonSrcFile),
            CharStreams.fromString(cemaforSrc, cemaforSrcFile))
         Collections.shuffle(inputsV1)
         val docV1 = Compiler(inputsV1).compile()

         val inputsV2 = listOf(
            CharStreams.fromString(commonSrcV1, commonSrcFile),
            CharStreams.fromString(cemaforSrc, cemaforSrcFile),
            CharStreams.fromString(cemaforServiceSrc, cemaforServiceSrcFile))
         Collections.shuffle(inputsV2)
         val docV2 = Compiler(inputsV2).compile()

         docV1.type("cemafor.Order").definitionHash
            .should.be.equal(docV2.type("cemafor.Order").definitionHash)
      }
   }

}