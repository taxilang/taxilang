package lang.taxi.xsd

object EnumNaming {
   fun toValidEnumName(name: String): String {
      if (name.isEmpty()) {
         error("Cannot have an empty enum name")
      }
      var updated = name
      updated = updated.replace(" ", "_")
      updated = if (updated.first().isDigit()) {
         @Suppress("ConvertToStringTemplate")
         "$" + updated
      } else {
         updated
      }
      return updated
   }
}
