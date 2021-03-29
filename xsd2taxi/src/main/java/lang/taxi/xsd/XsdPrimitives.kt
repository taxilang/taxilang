package lang.taxi.xsd

import com.google.common.io.Resources
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import java.io.File

object XsdPrimitives {
   // TODO: Replace this

   @Deprecated("Use SchemaNames")
   const val XML_NAMESPACE = SchemaNames.XML_NAMESPACE

   @Deprecated("Use SchemaNames")
   val XML_NAMESPACE_PACKAGE = SchemaNames.XML_PACKAGE_NAME
   private val primitives: Map<QualifiedName, Type>

   val primtiviesTaxiDoc:TaxiDocument
   val primitivesTaxiSource:String

   init {
      val file = File(Resources.getResource("XmlDataTypes.taxi").toURI())
      primitivesTaxiSource = file.readText()
      primtiviesTaxiDoc = Compiler(file)
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
