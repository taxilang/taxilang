package lang.taxi.types

interface TypeWithFields : Named {
   /**
    * Returns fields declared in parents, as well as
    * fields in this type
    */
   val allFields : List<Field>
   val fields: List<Field>
   fun hasField(name: String): Boolean = allFields.any { it.name == name }
   fun field(name: String): Field  = allFields.firstOrNull { it.name == name } ?: error("Type ${this.qualifiedName} has no field named '$name'")
}
