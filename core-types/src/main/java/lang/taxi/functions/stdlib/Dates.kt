package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Dates {
   val functions = listOf(
      AddMinutes,
      AddDays,
      AddSeconds,
      Now,
      ParseDate
   )
}

object AddMinutes : FunctionApi {
   override val taxi: String = """declare function addMinutes(DateTime, Int):DateTime
      |declare function addMinutes(Instant, Int): Instant
   """.trimMargin()
   override val name: QualifiedName = stdLibName("dates.addMinutes")
}

object AddSeconds : FunctionApi {
   override val taxi: String = """declare function addSeconds(DateTime, Int):DateTime
      |declare function addSeconds(Instant, Int): Instant
   """.trimMargin()
   override val name: QualifiedName = stdLibName("dates.addSeconds")
}

object AddDays : FunctionApi {
   override val taxi: String = """declare function addDays(DateTime, Int):DateTime
      |declare function addDays(Instant, Int): Instant
   """.trimMargin()
   override val name: QualifiedName = stdLibName("dates.addDays")
}

object Now : FunctionApi {
   override val taxi: String = """declare function <T> now():T"""
   override val name: QualifiedName = stdLibName("dates.now")
}

object ParseDate : FunctionApi {
   override val taxi: String = """declare function <T> parseDate(String):T"""
   override val name: QualifiedName = stdLibName("dates.parseDate")
}
