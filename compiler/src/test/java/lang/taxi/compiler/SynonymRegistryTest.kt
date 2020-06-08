package lang.taxi.compiler

import com.nhaarman.mockitokotlin2.mock
import com.winterbe.expekt.should
import lang.taxi.TypeSystem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SynonymRegistryTest {
   lateinit var registry : SynonymRegistry<String>
   @Before
   fun setup() {
     registry = SynonymRegistry(TypeSystem(emptyList()))
   }
   @Test
   fun synonymsResolveTransitively() {
      registry.registerSynonyms("A.1",listOf("B.1","C.1"),"")
      registry.synonymNamesFor("A.1").should.contain.elements("B.1","C.1")
      registry.synonymNamesFor("B.1").should.contain.elements("A.1","C.1")
      registry.synonymNamesFor("C.1").should.contain.elements("A.1","B.1")
   }

   @Test
   fun synonymsResolveThroughGraph() {
      registry.registerSynonyms("A.1",listOf("B.1"),"")
      registry.registerSynonyms("B.1",listOf("C.1"),"")
      registry.synonymNamesFor("A.1").should.contain.elements("B.1","C.1")
      registry.synonymNamesFor("B.1").should.contain.elements("A.1","C.1")
      registry.synonymNamesFor("C.1").should.contain.elements("A.1","B.1")
   }

   @Test
   fun circularReferencesAreOk() {
      registry.registerSynonyms("A.1",listOf("B.1","C.1"),"")
      registry.registerSynonyms("B.1",listOf("A.1"), "")
      registry.synonymNamesFor("A.1").should.contain.elements("B.1","C.1")
      registry.synonymNamesFor("B.1").should.contain.elements("A.1","C.1")
      registry.synonymNamesFor("C.1").should.contain.elements("A.1","B.1")
   }

}

