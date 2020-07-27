package lang.taxi.generators.java;

import lang.taxi.annotations.DataType;

@DataType("companyX.common.Product")
public class JavaTypeTest {
   @DataType("companyX.common.ProductIdentifier")
   private Long productIdentifier;

   @DataType("companyX.common.ProductName")
   private String productName;

   @DataType("companyX.common.assetId")
   private Integer assetId;

   @DataType("companyX.common.hash")
   private Short hash;
}
