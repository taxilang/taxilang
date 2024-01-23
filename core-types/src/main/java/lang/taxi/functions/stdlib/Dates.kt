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
   // TODO  : Overload support needed
   // We used to declare this as:
   // declare function addMinutes(DateTime, Int):DateTime
   // declare function addMinutes(Instant, Int): Instant
   // However, we don't support overloads, so this fails at evaluation time.
   // (See DateMathTests)
   override val taxi: String = """declare function <T> addMinutes(T, Int):T"""
   override val name: QualifiedName = stdLibName("dates.addMinutes")
}

object AddSeconds : FunctionApi {
   // TODO  : Overload support needed
   // We used to declare this as:
   // declare function addSeconds(DateTime, Int):DateTime
   // declare function addSeconds(Instant, Int): Instant
   // However, we don't support overloads, so this fails at evaluation time.
   // (See DateMathTests)
   override val taxi: String = """declare function <T> addSeconds(T, Int):T"""
   override val name: QualifiedName = stdLibName("dates.addSeconds")
}

object AddDays : FunctionApi {
   // TODO  : Overload support needed
   // We used to declare this as:
   // declare function addDays(DateTime, Int):DateTime
   // declare function addDays(Instant, Int): Instant
   // However, we don't support overloads, so this fails at evaluation time.
   // (See DateMathTests)
   override val taxi: String = """declare function <T> addDays(T, Int):T
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
