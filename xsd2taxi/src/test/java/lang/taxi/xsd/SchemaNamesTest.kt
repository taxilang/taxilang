package lang.taxi.xsd

import com.winterbe.expekt.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SchemaNamesTest : Spek({
   describe("parsing xsd schema names to package names") {
      it("should turn a uri to a package name"){
         SchemaNames.schemaNamespaceToPackageName("http://tempuri.org/PurchaseOrderSchema.xsd")
            .should.equal("org.tempuri")
      }
      it("should do something with these iso20022 uris") {
         val packageName = SchemaNames.schemaNamespaceToPackageName("urn:iso:std:iso:20022:tech:xsd:pain.002.001.11")
         // I don't love the impl, but not sure what's better
         packageName.should.equal("iso.std.iso20022.tech.xsd.pain00200111")
      }
   }
})
