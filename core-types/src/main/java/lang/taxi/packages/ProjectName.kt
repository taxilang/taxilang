package lang.taxi.packages

data class ProjectName(
   val organisation: String,
   val name: String
) {
   val id: String = "$organisation/$name"

   companion object {
      fun fromId(id: String): ProjectName {
         val parts = id.split("/")
         require(parts.size == 2) { "Invalid project id. $id should be in the form of organisation/name" }
         val (organisation, name) = parts
         return ProjectName(organisation, name)
      }
   }
}
