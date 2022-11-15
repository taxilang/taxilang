package lang.taxi.accessors

import arrow.core.Either
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
data class ProjectionFunctionScope(val name: String, val type: Type) {
   fun matchesReference(identifiers:List<String>):Boolean {
      return identifiers.isNotEmpty() && identifiers.first() == name
   }


   companion object {
      const val THIS = "this"
      fun implicitThis(type:Type): ProjectionFunctionScope {
         return ProjectionFunctionScope(THIS, type)
      }
   }
}
