package lang.taxi.accessors

import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.Type

/**
 * A ProjectionScope is defined when projecting a field.
 * It encapsulates the input type being provided.
 * Additionally may provide a name
 *
 * eg:
 *  find { Film[] } as (film:Film) -> {   //<-------this is a projection scope, of "film:Film"
 *     title : FilmTitle
 *     headLiner : someFunction() as (actor:Actor) -> {  // <---------this is a projection scope of "actor:Actor"
 *        name : actor.name
 *
 */
data class ProjectionFunctionScope(override val name: String, override val type: Type) : Argument{
   companion object {
      const val THIS = "this"
      fun implicitThis(type:Type): ProjectionFunctionScope {
         return ProjectionFunctionScope(THIS, type)
      }
   }

   override fun pruneFieldPath(path: List<String>): List<String> {
      // The first identifier is the name of the scope, and doesn't
      // need to be resolved (eg: "this")
      return path.drop(1)
   }

   override fun pruneFieldSelectors(fieldSelectors: List<FieldReferenceSelector>): List<FieldReferenceSelector> {
      // The first identifier is the name of the scope, and doesn't
      // need to be resolved (eg: "this")
      return fieldSelectors.drop(1)
   }
}


/**
 * Am argument (who can have a value resolved
 * at runtime), which is used wihtin expressions.
 *
 * Arguments generally come from projection scopes, or
 * Variables passed into queries.
 *
 * TODO : Not sure about the name.
 */
interface Argument {
   val name: String
   val type: Type

   fun matchesReference(identifiers:List<String>):Boolean {
      return identifiers.isNotEmpty() && identifiers.first() == name
   }

   fun matchesScope(other: Argument): Boolean {
      return when {
         this == other -> true
         // TODO : I'm not sure about this implementation.
         // eg: Two scopes, both called "THIS", (because of implicit this)
         // could incorrectly match here.
         this.name == other.name && this.type == other.type -> true
         else -> false
      }

   }

   /**
    * Allows implementations to optionally prune the first few parts of a
    * path.
    */
   fun pruneFieldSelectors(fieldSelectors: List<FieldReferenceSelector>): List<FieldReferenceSelector> {
      return fieldSelectors
   }
   fun pruneFieldPath(path: List<String>):List<String> {
      return path.drop(1)
   }
}
