package lang.taxi.types

import kotlin.reflect.KType

interface DataSource : Named, Compiled

data class FileDataSource(
   override val qualifiedName: String,
   val location: String,
   val format: String,
   val returnType: Type,
   val mappings: List<ColumnMapping>,
   val annotations: List<Annotation> = emptyList(),
   override val compilationUnits: List<CompilationUnit>
) : DataSource

data class ColumnMapping(
   val propertyName: String,
   val index: Any
)
