package lang.taxi.compiler

import com.google.common.graph.ValueGraphBuilder
import lang.taxi.TypeSystem
import lang.taxi.types.EnumValueQualifiedName
import lang.taxi.types.Enums.splitEnumValueQualifiedName
import lang.taxi.types.QualifiedName
import java.util.*

/**
 * Registers enum synonyms
 */
class SynonymRegistry<TDefinition>(typeSystem: TypeSystem) {
   private val graph = ValueGraphBuilder.undirected().build<EnumValueQualifiedName, TDefinition>()
   fun registerSynonyms(qualifiedName: EnumValueQualifiedName, synonyms: List<EnumValueQualifiedName>, token: TDefinition) {
      if (synonyms.isEmpty()) {
         return
      }
      (synonyms + qualifiedName).forEach { graph.addNode(it) }
      synonyms.forEach { synonym ->
         graph.putEdgeValue(qualifiedName, synonym, token)
      }

   }

   fun synonymNamesFor(enumValue: EnumValueQualifiedName): Set<EnumValueQualifiedName> {
      return synonymsFor(enumValue).map { it.first }.toSet()
   }

   fun synonymsFor(enumValue: EnumValueQualifiedName): List<Pair<EnumValueQualifiedName, TDefinition>> {
      val nodesToVisit = LinkedList<EnumValueQualifiedName>(listOf(enumValue))
      val visitedNodes = mutableSetOf<EnumValueQualifiedName>()
      val synonyms = mutableListOf<Pair<EnumValueQualifiedName, TDefinition>>()
      while (nodesToVisit.isNotEmpty()) {
         val node = nodesToVisit.poll()
         if (visitedNodes.add(node)) {
            graph.adjacentNodes(node).forEach { synonym ->
               nodesToVisit.offer(synonym)
               synonyms.add(synonym to graph.edgeValue(node, synonym).get())
            }
         }
      }
      return synonyms
         .filter { it.first != enumValue }
         .toList()
   }

   fun getTypesWithSynonymsRegistered(): Map<QualifiedName, List<EnumValueQualifiedName>> {
      return this.graph.nodes().map { splitEnumValueQualifiedName(it).first to it }
         .groupBy { it.first }
         .mapValues { (_, values) -> values.map { it.second } }

   }
}

