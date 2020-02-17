package lang.taxi.types

interface DataSource : Named

data class FileDataSource(
   override val qualifiedName: String,
   val location: String,
   val format: String,
   val returnType: Type,
   val mappings: List<ColumnMapping>
) : DataSource

data class ColumnMapping(
   val propertyName: String,
   val index: Int
)
