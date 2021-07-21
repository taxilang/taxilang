package lang.taxi.compiler

import com.winterbe.expekt.should
import lang.taxi.TypeSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SynonymRegistryTest {
   lateinit var registry : SynonymRegistry<String>
   @BeforeEach
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

