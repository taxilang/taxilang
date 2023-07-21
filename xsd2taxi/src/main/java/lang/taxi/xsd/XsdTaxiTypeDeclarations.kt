package lang.taxi.xsd

import com.sun.xml.xsom.ForeignAttributes
import lang.taxi.types.QualifiedName

object XsdTaxiTypeDeclarations {
   private const val TAXI_XSD_NAMESPACE = "http://taxilang.org/"
   private const val TYPE_DECLARATION = "type"
   private const val CREATE = "create"

   /**
    * The equivalent of declaring taxi:type="foo" taxi:create="true" ...
    */
   private const val CREATE_TYPE_SHORTHAND = "createsType"

   private fun ForeignAttributes.getValueIfPresent(namespace: String, name: String): String? {
      for (i in 0 until this.length) {
         if (this.matchesName(namespace, name, i)) {
            return this.getValue(i)
         }
      }
      return null
   }

   private fun getTaxiTypeReference(foreignAttributes: ForeignAttributes): TaxiTypeReference? {
      val createdTypeName = foreignAttributes.getValueIfPresent(TAXI_XSD_NAMESPACE, CREATE_TYPE_SHORTHAND)
      if (createdTypeName != null) {
         return TaxiTypeReference(QualifiedName.from(createdTypeName), true)
      }
      val typeName: String = foreignAttributes.getValueIfPresent(TAXI_XSD_NAMESPACE, TYPE_DECLARATION) ?: return null
      val declaresCreation = foreignAttributes.getValueIfPresent(TAXI_XSD_NAMESPACE, CREATE)
         ?.toBoolean() ?: false
      return TaxiTypeReference(QualifiedName.from(typeName), declaresCreation)
   }


   fun getTaxiTypeReference(foreignAttributesList: List<ForeignAttributes>): TaxiTypeReference? {
      return foreignAttributesList
         .asSequence()
         .mapNotNull { getTaxiTypeReference(it) }
         .firstOrNull()
   }

   private fun ForeignAttributes.matchesName(namespace: String, name: String, index: Int): Boolean {
      val uri = this.getURI(index)
      val localName = this.getLocalName(index)
      return uri == namespace && name == localName
   }

}

data class TaxiTypeReference(
   val typeName: QualifiedName,
   /**
    * Indicates if this declaration is intending to create a type (true),
    * or if the type should already exist, and is therefore imported (false)
    */
   val createsType: Boolean = false
)
