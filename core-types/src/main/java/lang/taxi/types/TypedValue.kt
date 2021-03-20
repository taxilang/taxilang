package lang.taxi.types

/*
 * Represents a 'fact' that is supplied through 'given' statement in Queries.
 * In Vyne World, this will be converted into a TypedInstance and fed into
 * the relevant 'QueryContext'
 */
data class TypedValue(val fqn: QualifiedName, val value: Any?)
