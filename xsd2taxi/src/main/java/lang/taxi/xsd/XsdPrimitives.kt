package lang.taxi.xsd

import com.google.common.io.Resources
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import org.antlr.v4.runtime.CharStreams
import java.io.File
import java.nio.charset.Charset

object XsdPrimitives {
   // Declare inline.
   // Loading resources from classpaths is tricky in native images.
   val primitivesTaxiSource:String = """
namespace org.w3

type string inherits String
type anyType inherits Any
type integer inherits Int
type date inherits Date
type boolean inherits Boolean
type time inherits Time
type dateTime inherits DateTime
type float inherits Decimal
type double inherits Decimal
type decimal inherits Decimal

type base64Binary inherits String
type hexBinary inherits String

type anyURI inherits String
type ID inherits String
type IDREF inherits String
type QName inherits String
type NOTATION inherits String

type normalizedString inherits String
type token inherits normalizedString
type language inherits token
type Name inherits token
type NCName inherits Name
type ID inherits NCName
type IDREF inherits NCName
type alias IDREFS as IDREF[]

type NMTOKEN inherits token
type alias NMTOKENS as NMTOKEN[]

type ENTITY inherits NCName
type alias ENTITIES as ENTITY[]

type nonPositiveInteger inherits integer
type long inherits integer
type nonNegativeInteger inherits integer
type negativeInteger inherits nonPositiveInteger

// This is to conform with the xsd spec, but it's
// clearly confusing
type int inherits long
type short inherits int
type byte inherits short

type unsignedLong inherits nonNegativeInteger
type positiveInteger inherits nonNegativeInteger
type unsignedInt inherits unsignedLong
type unsignedShort inherits unsignedInt
type unsignedByte inherits unsignedShort

type duration inherits String
type gYearMonth inherits String
type gYear inherits String
type gMonthDay inherits String
type gDay inherits String
type gMonth inherits String

   """.trimIndent()

   // TODO: Replace this

   @Deprecated("Use SchemaNames")
   const val XML_NAMESPACE = SchemaNames.XML_NAMESPACE

   @Deprecated("Use SchemaNames")
   val XML_NAMESPACE_PACKAGE = SchemaNames.XML_PACKAGE_NAME
   private val primitives: Map<QualifiedName, Type>

   val primtiviesTaxiDoc:TaxiDocument


   init {
      val charstream = CharStreams.fromString(primitivesTaxiSource)
      primtiviesTaxiDoc = Compiler(charstream)
         .compile()
      primitives = primtiviesTaxiDoc
         .types
         .map { type ->
            when  (type) {
               // We define a bunch of xsd type aliases, however, prefer the Taxi equivalent type
               is TypeAlias -> type.toQualifiedName() to type.aliasType!!
               else -> type.toQualifiedName() to type
            }
         }
         .toMap()
   }

//   private val primitives: Map<QualifiedName, Type> = mapOf(
//      "string" to PrimitiveType.STRING,
//      "anyType" to PrimitiveType.ANY,
//      "integer" to PrimitiveType.INTEGER,
//      "date" to PrimitiveType.LOCAL_DATE,
//      "boolean" to PrimitiveType.BOOLEAN,
//      "time" to PrimitiveType.TIME,
//      "dateTime" to PrimitiveType.DATE_TIME,
//      "float" to PrimitiveType.DECIMAL,
//      "double" to PrimitiveType.DECIMAL,
//      "decimal" to PrimitiveType.DECIMAL,
//      "NMTOKEN" to PrimitiveType.STRING,
//      "NMTOKENS" to PrimitiveType.STRING,
//      // Really not sure what to do here.  Do we introduce a special type?
//      "base64Binary" to PrimitiveType.STRING,
//      // TODO : This is actually a semantic type, we should expose an xsd schema, and
//      // map this to xsd:ID
//      "ID" to PrimitiveType.STRING,
//      "IDREF" to PrimitiveType.STRING,
//      "hexBinary" to PrimitiveType.STRING,
//      "anyURI" to PrimitiveType.STRING

   //   ).map { (k, v) -> QualifiedName(SchemaNames.XML_PACKAGE_NAME, k) to v }
//      .toMap()
   val ANY_TYPE: QualifiedName = primitives.filter { (k, _) -> k.typeName == "anyType" }.keys.first()
   fun isPrimitive(namespace: String, name: String) = isPrimitive(QualifiedName(namespace, name))
   fun isPrimitive(qualifiedName: QualifiedName) = primitives.containsKey(qualifiedName)
   fun getType(qualifiedName: QualifiedName): Type = primitives.getValue(qualifiedName)
}
