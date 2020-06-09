package lang.taxi

import com.winterbe.expekt.should
import org.junit.Test

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

}
