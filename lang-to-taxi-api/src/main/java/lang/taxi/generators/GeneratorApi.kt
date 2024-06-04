package lang.taxi.generators

import lang.taxi.services.Service
import lang.taxi.sources.SourceCode
import lang.taxi.types.ParameterizedName
import lang.taxi.types.Type

data class GeneratedTaxiCode(
   val taxi: List<String>,
   val messages: List<Message>,
   val suggestedFileName: String? = null,
   val sourceMap: SourceMap = SourceMap.EMPTY
) {
   val successful = taxi.isNotEmpty()
   val hasErrors = messages.hasErrors()
   val hasWarnings = messages.hasWarnings()
   val errorCount = messages.errorCount
   val warningCount = messages.warningCount
   val concatenatedSource = taxi.joinToString("\n")
}

typealias SourceFileName = String

/**
 * When generating taxi code, this provides a map from the generated members
 * to the original source file they came from
 */
data class SourceMap(
   // TODO : In future, it may make sense to expand this to include location as well?
   // However, in practice I suspect that's overkill, as the goal here is to re-parse the
   // source to do things like Protobuf of Avro serde, and in those cases, we need the whole file
   val types: Map<ParameterizedName, SourceFileName>,
   val services: Map<ParameterizedName, SourceFileName>
) {

   fun contains(name: ParameterizedName):Boolean {
      return containsType(name) || containsService(name)
   }
   fun containsType(name: ParameterizedName): Boolean = types.containsKey(name)
   fun containsService(name: ParameterizedName): Boolean = services.containsKey(name)

   fun getSourceFileName(name: ParameterizedName):SourceFileName {
      return types[name] ?: services[name] ?: error("$name not found in this source map")
   }
   fun getSourceFileNameForType(name: ParameterizedName):SourceFileName {
      return types[name] ?: error("$name not found in this source map")
   }

   companion object {
      val EMPTY = SourceMap(emptyMap(), emptyMap())
      fun forMembers(fileName: SourceFileName, types: Collection<Type> = emptyList(), services: Collection<Service> = emptyList()): SourceMap {
         return SourceMap(
            types = types.associate { it.toQualifiedName().parameterizedName to fileName },
            services = services.associate { it.toQualifiedName().parameterizedName to fileName }
         )
      }
   }
   val isEmpty = types.isEmpty() && services.isEmpty()
   val isNotEmpty = !isEmpty

   fun combine(other: SourceMap):SourceMap {
      return SourceMap(
         types + other.types,
         services + other.services
      )
   }
}

fun List<Message>.hasErrors(): Boolean {
   return this.any { it.level == Level.ERROR }
}

fun List<Message>.hasWarnings(): Boolean {
   return this.any { it.level == Level.WARN }
}

val List<Message>.errorCount: Int
   get() {
      return this.count { it.level == Level.ERROR }
   }

val List<Message>.warningCount: Int
   get() {
      return this.count { it.level == Level.WARN }
   }

data class Message(val level: Level, val message: String, val link: String? = null)
enum class Level {
   INFO,
   WARN,
   ERROR;
}

class Logger(val logName: String = "lang.taxi.CodeGenerationLogger") {
   constructor(logClass: Class<*>) : this(logClass.simpleName)

   private val _messages: MutableList<Message> = mutableListOf()

   val messages: List<Message>
      get() {
         return _messages.toList()
      }

   fun info(message: String, link: String? = null) = append(Level.INFO, message, link)
   fun warn(message: String, link: String? = null) = append(Level.WARN, message, link)
   fun error(message: String, link: String? = null) = append(Level.ERROR, message, link)

   private fun append(level: Level, message: String, link: String?) {
      _messages.add(Message(level, message, link))
   }

   fun failure(message: String, link: String? = null): GeneratedTaxiCode {
      error(message, link)
      return GeneratedTaxiCode(emptyList(), this.messages)
   }
}

