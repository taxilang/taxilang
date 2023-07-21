package lang.taxi.xsd

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.Compiler
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.testing.TestHelpers
import org.assertj.core.util.Files
import java.io.File

class XsdToTaxiSpec : DescribeSpec({
   describe("converting xsd to taxi") {
      it("should set field docs from attributes") {
         val schema = xsd(
            """
          <xsd:element name="PurchaseOrder" type="tns:PurchaseOrderType"/>
          <xsd:complexType name="PurchaseOrderType">
          <xsd:attribute name="OrderDate" type="xsd:date">
            <xsd:annotation>
               <xsd:documentation>The date for an order</xsd:documentation>
            </xsd:annotation>
          </xsd:attribute>
          </xsd:complexType>
          """
         ).asTaxi()
         val expected = """namespace org.tempuri {
            type PurchaseOrderType {
               [[ The date for an order ]]
               @lang.taxi.xml.XmlAttribute
               OrderDate : Date?
            }
         }
         """
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should generate model for a complex type") {
         val schema = """<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
           xmlns:tns="http://tempuri.org/PurchaseOrderSchema.xsd"
           targetNamespace="http://tempuri.org/PurchaseOrderSchema.xsd"
           elementFormDefault="qualified">
 <xsd:element name="PurchaseOrder" type="tns:PurchaseOrderType"/>
 <xsd:complexType name="PurchaseOrderType">
  <xsd:sequence>
   <xsd:element name="ShipTo" type="tns:USAddress" maxOccurs="2"/>
   <xsd:element name="BillTo" type="tns:USAddress"/>
  </xsd:sequence>
  <xsd:attribute name="OrderDate" type="xsd:date"/>
 </xsd:complexType>

 <xsd:complexType name="USAddress">
  <xsd:sequence>
   <xsd:element name="name"   type="xsd:string"/>
   <xsd:element name="street" type="xsd:string"/>
   <xsd:element name="city"   type="xsd:string"/>
   <xsd:element name="state"  type="xsd:string"/>
   <xsd:element name="zip"    type="xsd:integer"/>
  </xsd:sequence>
  <xsd:attribute name="country" type="xsd:NMTOKEN" fixed="US"/>
 </xsd:complexType>
</xsd:schema>"""
         val taxi = TaxiGenerator().generateAsStrings(
            schema.asTempFile()
         )
         val expected = """
            namespace org.tempuri {
               type PurchaseOrderType {
                  ShipTo : USAddress
                  BillTo : USAddress
                  @lang.taxi.xml.XmlAttribute OrderDate : Date?
               }

               type USAddress {
                  name : String
                  street : String
                  city : String
                  state : String
                  zip : Int
                  @lang.taxi.xml.XmlAttribute country : org.w3.NMTOKEN?
               }
            }
         """.trimIndent()
         TestHelpers.expectToCompileTheSame(taxi.taxi, xsdTaxiSources(expected))
      }

      it("should generated inherited types correctly") {
         val schema = xsd(
            """<xsd:simpleType name="ISODate">
        <xsd:restriction base="xsd:date"/>
    </xsd:simpleType>"""
         ).asTaxi()

         val expected = """namespace org.tempuri {
   type ISODate inherits lang.taxi.Date(@format = "yyyy-MM-dd")
}"""
         // TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should parse enums") {
         val schema = xsd(
            """    <xsd:simpleType name="MandateClassification1Code">
        <xsd:restriction base="xsd:string">
            <xsd:enumeration value="FIXE"/>
            <xsd:enumeration value="USGB"/>
            <xsd:enumeration value="VARI"/>
        </xsd:restriction>
    </xsd:simpleType>"""
         ).asTaxi()
         val expected = """namespace org.tempuri {
   enum MandateClassification1Code {
      FIXE,
      USGB,
      VARI
   }
}"""
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should handle enums with spaces") {
         val schema = xsd(
            """    <xsd:simpleType name="MandateClassification1Code">
        <xsd:restriction base="xsd:string">
            <xsd:enumeration value="FOO BAR"/>
            <xsd:enumeration value="USGB"/>
            <xsd:enumeration value="VARI"/>
        </xsd:restriction>
    </xsd:simpleType>"""
         ).asTaxi()
         val expected = """namespace org.tempuri {
   enum MandateClassification1Code {
      FOO_BAR("FOO BAR"),
      USGB,
      VARI
   }
}"""
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should generate a synthetic type for xsd types that inherits enums") {
         val schema = xsd(
            """
<xsd:complexType name="IdentifiedPayerReceiver">
    <xsd:annotation>
      <xsd:documentation xml:lang="en">A type extending the PayerReceiverEnum type wih an id attribute.</xsd:documentation>
    </xsd:annotation>
    <xsd:simpleContent>
      <xsd:extension base="tns:PayerReceiverEnum">
        <xsd:attribute name="id" type="xsd:ID" />
      </xsd:extension>
    </xsd:simpleContent>
  </xsd:complexType>
  <xsd:simpleType name="PayerReceiverEnum">
  <xsd:restriction base="xsd:string">
      <xsd:enumeration value="Payer"/>
      <xsd:enumeration value="Receiver"/>
  </xsd:restriction>
</xsd:simpleType>
  """
         ).asTaxi()
         val expected = """namespace org.tempuri {
   [[ A type extending the PayerReceiverEnum type wih an id attribute. ]]
   type IdentifiedPayerReceiver {
      @lang.taxi.xml.XmlAttribute id : org.w3.ID?
      @lang.taxi.xml.XmlBody payerReceiverEnum : PayerReceiverEnum
   }

   enum PayerReceiverEnum {
      Payer,
      Receiver
   }
}"""
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should generate mandatory fields as non-nullable") {
         val schema = xsd(
            """<xsd:complexType name="USAddress">
  <xsd:sequence>
   <xsd:element name="name" maxOccurs="1" minOccurs="1"  type="xsd:string"/>
  </xsd:sequence>
 </xsd:complexType>"""
         ).asTaxi()
         val expected = """namespace org.tempuri {
   type USAddress {
      name : String
   }
}"""
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }
      it("should generate optional fields as nullable") {
         val schema = xsd(
            """<xsd:complexType name="USAddress">
  <xsd:sequence>
   <xsd:element name="name" maxOccurs="1" minOccurs="0"  type="xsd:string"/>
  </xsd:sequence>
 </xsd:complexType>"""
         ).asTaxi()
         val expected = """namespace org.tempuri {
   type USAddress {
      name : String?
   }
}"""
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should generate string types with patterns") {
         val schema = xsd(
            """<xsd:simpleType name="ActiveCurrencyCode">
        <xsd:restriction base="xsd:string">
            <xsd:pattern value="[A-Z]{3,3}"/>
        </xsd:restriction>
    </xsd:simpleType>"""
         ).asTaxi()
         val expected = """namespace org.tempuri
            |
            |@Format("[A-Z]{3,3}")
            |type ActiveCurrencyCode inherits String
         """.trimMargin()
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should escape patterns with slashes") {
         val schema = xsd(
            """    <xsd:simpleType name="PhoneNumber">
        <xsd:restriction base="xsd:string">
            <xsd:pattern value="\+[0-9]{1,3}-[0-9()+\-]{1,30}"/>
        </xsd:restriction>
    </xsd:simpleType>"""
         ).asTaxi()
         val expected = """namespace org.tempuri
            |@Format("\\+[0-9]{1,3}-[0-9()+\\-]{1,30}")
            |type PhoneNumber inherits String
         """.trimMargin()
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should generate documented enums correctly") {
         val schema = xsd(
            """  <xsd:simpleType name="DayTypeEnum">
    <xsd:annotation>
      <xsd:documentation source="http://www.FpML.org" xml:lang="en">A day type classification used in counting the number of days between two dates.</xsd:documentation>
    </xsd:annotation>
    <xsd:restriction base="xsd:token">
      <xsd:enumeration value="Business">
        <xsd:annotation>
          <xsd:documentation source="http://www.FpML.org" xml:lang="en">When calculating the number of days between two dates the count includes only business days.</xsd:documentation>
        </xsd:annotation>
      </xsd:enumeration>
       </xsd:restriction>
  </xsd:simpleType>
   """
         ).asTaxi()
         val expected = """
            namespace org.tempuri
            [[ A day type classification used in counting the number of days between two dates. ]]
            enum DayTypeEnum {
               [[ When calculating the number of days between two dates the count includes only business days. ]]
               Business
            }
         """.trimIndent()
         val taxi = TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
         // docs are not asserted when comparing compiled objects, so doing that manually
         val enum = taxi.enumType("org.tempuri.DayTypeEnum")
         enum.typeDoc.should.equal("A day type classification used in counting the number of days between two dates.")
         enum.ofValue("Business").typeDoc.should.equal("When calculating the number of days between two dates the count includes only business days.")
      }

      it("should generate inherited enums") {
         val schema = xsd(
            """
 <xsd:simpleType name="PutCallEnum">
 <xsd:annotation>
   <xsd:documentation source="http://www.FpML.org" xml:lang="en">Specifies whether the option is a call or a put.</xsd:documentation>
 </xsd:annotation>
 <xsd:restriction base="xsd:token">
   <xsd:enumeration value="Put">
     <xsd:annotation>
       <xsd:documentation source="http://www.FpML.org" xml:lang="en">A put option gives the holder the right to sell the underlying asset by a certain date for a certain price.</xsd:documentation>
     </xsd:annotation>
   </xsd:enumeration>
   <xsd:enumeration value="Call">
     <xsd:annotation>
       <xsd:documentation source="http://www.FpML.org" xml:lang="en">A call option gives the holder the right to buy the underlying asset by a certain date for a certain price.</xsd:documentation>
     </xsd:annotation>
   </xsd:enumeration>
 </xsd:restriction>
</xsd:simpleType>
<xsd:simpleType name="EquityOptionTypeEnum">
    <xsd:annotation>
      <xsd:documentation source="http://www.FpML.org" xml:lang="en">Specifies an additional Forward type.</xsd:documentation>
    </xsd:annotation>
    <xsd:union memberTypes="tns:PutCallEnum">
      <xsd:simpleType>
        <xsd:restriction base="xsd:token">
          <xsd:enumeration value="Forward" >
            <xsd:annotation>
              <xsd:documentation source="http://www.FpML.org" xml:lang="en">DEPRECATED value which will be removed in FpML-5-0 onwards A forward contract is an agreement to buy or sell the underlying asset at a certain future time for a certain price.</xsd:documentation>
            </xsd:annotation>
          </xsd:enumeration>
        </xsd:restriction>
      </xsd:simpleType>
    </xsd:union>
</xsd:simpleType>
  """
         ).asTaxi()

         // TestHelpers is not working for this test case.
         val enum = Compiler.forStrings(schema.taxi)
            .compile()
            .enumType("org.tempuri.EquityOptionTypeEnum")

         enum.of("Put").synonyms.should.contain("org.tempuri.PutCallEnum.Put")
         enum.of("Call").synonyms.should.contain("org.tempuri.PutCallEnum.Call")
         enum.of("Forward").synonyms.should.be.empty
      }

      it("should generate choice elements as a list of fields until we come up with something better") {
         val schema = xsd(
            """<xsd:complexType name="MandateClassification1Choice">
        <xsd:choice>
            <xsd:element name="Cd" type="xsd:string"/>
            <xsd:element name="Prtry" type="xsd:string"/>
        </xsd:choice>
    </xsd:complexType>"""
         ).asTaxi()
         val expected = """namespace org.tempuri
   model MandateClassification1Choice {
      Cd : String?
      Prtry : String?
   }

""".trimMargin()
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should generate types including xsd group elements") {
         val schema = xsd(
            """
<xsd:complexType name="Product" abstract="true">
    <xsd:annotation>
      <xsd:documentation xml:lang="en">The base type which all FpML products extend.</xsd:documentation>
    </xsd:annotation>
    <xsd:group ref="tns:Product.model" minOccurs="0" />
    <xsd:attribute name="id" type="xsd:ID" />
  </xsd:complexType>
 <xsd:group name="Product.model">
   <xsd:sequence>
      <xsd:element name="primaryAssetClass" type="xsd:string" minOccurs="0">
        <xsd:annotation>
          <xsd:documentation xml:lang="en">A classification of the most important risk class of the trade. FpML defines a simple asset class categorization using a coding scheme.</xsd:documentation>
        </xsd:annotation>
      </xsd:element>
   </xsd:sequence>
   </xsd:group>
  """
         ).asTaxi()

         val expected = """namespace org.tempuri {
   [[ The base type which all FpML products extend. ]]
   type Product {
      [[ A classification of the most important risk class of the trade. FpML defines a simple asset class categorization using a coding scheme. ]]
      primaryAssetClass : String?
      @lang.taxi.xml.XmlAttribute id : org.w3.ID?
   }
}"""

         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should mark required attributes as non-nullable") {
         val schema = xsd(
            """
          <xsd:complexType name="PurchaseOrderType">
          <xsd:attribute name="OrderDate" type="xsd:date" use="required">
          </xsd:attribute>
          </xsd:complexType>
""".trimIndent()
         )
            .asTaxi()
         val expected = """namespace org.tempuri {
   type PurchaseOrderType {
      // Note - not nullable
      @lang.taxi.xml.XmlAttribute OrderDate : Date
   }
}"""

         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      it("should generate inherited types") {
         val schema = xsd(
            """
  <xsd:complexType name="ReturnSwapNotionalAmountReference">
    <xsd:annotation>
      <xsd:documentation xml:lang="en">A reference to the return swap notional amount.</xsd:documentation>
    </xsd:annotation>
    <xsd:complexContent>
      <xsd:extension base="tns:Reference">
        <xsd:attribute name="href" type="xsd:IDREF" use="required" />
      </xsd:extension>
    </xsd:complexContent>
  </xsd:complexType>
  <xsd:complexType name="Reference" abstract="true">
    <xsd:annotation>
      <xsd:documentation xml:lang="en">The abstract base class for all types which define intra-document pointers.</xsd:documentation>
    </xsd:annotation>
  </xsd:complexType>
         """.trimIndent()
         ).asTaxi()

         val expected = """namespace org.tempuri {
   [[ The abstract base class for all types which define intra-document pointers. ]]
   type Reference

   [[ A reference to the return swap notional amount. ]]
   type ReturnSwapNotionalAmountReference inherits org.tempuri.Reference {
      @lang.taxi.xml.XmlAttribute href : org.w3.IDREF
   }
}"""
         TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
      }

      describe("generating semantic types") {
         it("generates field attributes using declared taxi type") {
            val schema = xsd(
               """
                        <xsd:complexType name="CountryInfo">
                           <xsd:sequence>
                              <xsd:element name="ISOCode" type="xsd:string" taxi:type="com.foo.IsoCode" taxi:create="true"/>
                              <xsd:element name="Name" type="xsd:string" taxi:type="com.foo.CountryName" taxi:create="true"/>
                              <xsd:element name="CapitalCity" type="xsd:string" taxi:type="com.foo.CapitalCityName" taxi:create="true"/>
                           </xsd:sequence>
                        </xsd:complexType>
            """.trimIndent()
            ).asTaxi()

            val expected = """
            namespace com.foo {
               type IsoCode inherits String
               type CountryName inherits String
               type CapitalCityName inherits String
            }
            namespace org.tempuri {
               model CountryInfo {
                  ISOCode : com.foo.IsoCode
                  Name : com.foo.CountryName
                  CapitalCity : com.foo.CapitalCityName
               }
            }
            """.trimIndent()
            TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
         }
         it("generates field attributes using declared taxi type with createsType shorthande") {
            val schema = xsd(
               """
                        <xsd:complexType name="CountryInfo">
                           <xsd:sequence>
                              <xsd:element name="ISOCode" type="xsd:string" taxi:createsType="com.foo.IsoCode" />
                              <xsd:element name="Name" type="xsd:string" taxi:createsType="com.foo.CountryName" />
                              <xsd:element name="CapitalCity" type="xsd:string" taxi:createsType="com.foo.CapitalCityName" />
                           </xsd:sequence>
                        </xsd:complexType>
            """.trimIndent()
            ).asTaxi()

            val expected = """
            namespace com.foo {
               type IsoCode inherits String
               type CountryName inherits String
               type CapitalCityName inherits String
            }
            namespace org.tempuri {
               closed model CountryInfo {
                  ISOCode : com.foo.IsoCode
                  Name : com.foo.CountryName
                  CapitalCity : com.foo.CapitalCityName
               }
            }
            """.trimIndent()
            TestHelpers.expectToCompileTheSame(schema.taxi, xsdTaxiSources(expected))
         }
      }
   }
})

fun xsdTaxiSources(content: String): List<String> {
   return listOf(
      XsdAnnotations.annotationsTaxiSource, XsdPrimitives.primitivesTaxiSource, content
   )
}

fun xsd(content: String): String {
   return """<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
           xmlns:tns="http://tempuri.org/PurchaseOrderSchema.xsd"
           xmlns:taxi="http://taxilang.org/"
           targetNamespace="http://tempuri.org/PurchaseOrderSchema.xsd"
           elementFormDefault="qualified">
           $content
</xsd:schema>"""
}

private fun String.asTempFile(): File {
   val file = Files.newTemporaryFile()
   file.writeText(this)
   return file
}

private fun String.asTaxi(): GeneratedTaxiCode {
   val taxi = TaxiGenerator().generateAsStrings(
      this.asTempFile()
   )
   return taxi
}
