package lang.taxi.generators.protobuf

import com.winterbe.expekt.should
import org.junit.jupiter.api.Test

class ProtobufUtilsTest {

   @Test
   fun `finds package name`() {
      ProtobufUtils.findPackageName(
         """
syntax = "proto3";

package simple;

import "google/protobuf/timestamp.proto";
message Person {} """
      ).should.equal("simple")
   }

   @Test
   fun `returns empty strings when no package is declared`() {
      ProtobufUtils.findPackageName(
         """
syntax = "proto3";

import "google/protobuf/timestamp.proto";
message Person {} """
      ).should.equal("")
   }
}
