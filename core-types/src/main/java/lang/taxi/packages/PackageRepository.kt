package lang.taxi.packages

// TODO : Credentials
data class Repository(
   val url: String,
   val name: String? = null,
   val type: String = "nexus",
   // Has to be a map, as we use this to build mulitple different
   // repository types
   // note : Typing this as Map<String,Any> causes deserialization issues,
   // which I haven't investigated.  Will revisit this if/when  <String,String>
   // becomes a problem
   val settings: Map<String, String> = emptyMap()
)
