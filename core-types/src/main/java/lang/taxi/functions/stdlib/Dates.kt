package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object Dates {
   val functions = listOf(
      AddMinutes,
      AddDays,
      AddSeconds,
      Now,
      CurrentDate,
      CurrentDateTime,
      CurrentTime,
      ParseDate
   )
}

object AddMinutes : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Adds the specified number of minutes to a datetime or instant. Use a negative value to subtract minutes.

      ```taxi
      // Schedule a meeting 30 minutes from the event start
      find {
         meetingEnd: DateTime = addMinutes(DateTime, 30)
      }
      ```
      ]]
      declare function <T> addMinutes(T, Int):T""".trimIndent()
   override val name: QualifiedName = stdLibName("dates.addMinutes")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42QQUvEMBCF_8owp11IIa0WJCB4kGUVeuvNeIjNrFabtCapupT-d6dqQfGgOc0ML_PefBPG5oGcQYUo8HmkcOTyvn0hD5P2ABCTCUnBpUlUt47gHAYTIi39RmMhi9NM5lle1rlUJ1JJqXGr_az9ofX2a8fFrg_OJNYf-WVVlVkL-71y7kPMiqEb445dfxgZa6vWj4niZp0KKLf_XOn4a6xf-79XZnkhl8xMgG8zjhKFiGqaBcY03nF5cyuQ3gZqEtnr2HtmNGlcQ2tU8B0FLChKjYKna4pfGnnGuDQupoy4ebqyqPzYdZwh9I_s9NnO78Tc7_eiAQAA
      DocsSnippet(
         markdown = "Adds minutes to a datetime — positive to advance, negative to go back.",
         query = StubQueryMessage(
            schema = "",
            query = """
               given {
                  start: DateTime = parseDate("2024-01-15T10:30:00")
               }
               find {
                  @Format("yyyy-MM-dd HH:mm")
                  plusFive: DateTime = addMinutes(DateTime, 5)
                  @Format("yyyy-MM-dd HH:mm")
                  minusTwo: DateTime = addMinutes(DateTime, -120)
               }
            """.trimIndent(),
            expectedJson = """{"plusFive": "2024-01-15 10:35", "minusTwo": "2024-01-15 08:30"}"""
         )
      )
   )
}

object AddSeconds : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Adds the specified number of seconds to a datetime or instant. Use a negative value to subtract seconds.

      ```taxi
      // Expire a token 3600 seconds (1 hour) from now
      find {
         expiresAt: Instant = addSeconds(Instant, 3600)
      }
      ```
      ]]
      declare function <T> addSeconds(T, Int):T""".trimIndent()
   override val name: QualifiedName = stdLibName("dates.addSeconds")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/41QQU7DMBD8ympPrZRITkIPWELigFCL1BM9gTmYeKGBeBNsB1FF-TtbWkQPSODT2Ds7M54RY70lb1EjZvg2UNgJfG7eiWE0DAAx2ZA0rFgAJ7iA3oZIVzbRzGCpyrNcFXmx2BRKV0ordWdwbngy_NSwO2pcXnfB2yQLOzn5ep07B8ul9l7H-MUXUt8OcbNtQtqdulnnbqnu2MXZ8TGDSs3_L-sbFl3iv0TzYq86SQvyQespUYioxynDmIZHgfcPGdJHT3UidxM7lp5Ggz-pDWo4bQQOjVTKYCaD7xy_0cpzvRDa3lzS1K8rh5qHtpUsoXsRx8N1-gTTLyEWrgEAAA==
      DocsSnippet(
         markdown = "Adds seconds to an instant — positive to advance, negative to go back.",
         query = StubQueryMessage(
            schema = "",
            query = """
               given {
                  start: Instant = parseDate("2024-01-15T10:30:00Z")
               }
               find {
                  @Format("yyyy-MM-dd HH:mm:ss")
                  plusThirty: Instant = addSeconds(Instant, 30)
                  @Format("yyyy-MM-dd HH:mm:ss")
                  minusTen: Instant = addSeconds(Instant, -10)
               }
            """.trimIndent(),
            expectedJson = """{"plusThirty": "2024-01-15 10:30:30", "minusTen": "2024-01-15 10:29:50"}"""
         )
      )
   )
}

object AddDays : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Adds the specified number of days to a date, datetime, or instant. Use a negative value to subtract days.

      ```taxi
      // Calculate an expiry date 90 days from today
      find {
         @Format("yyyy-MM-dd")
         expiresOn: Date = addDays(Date, 90)
      }
      ```
      ]]
      declare function <T> addDays(T, Int):T""".trimIndent()
   override val name: QualifiedName = stdLibName("dates.addDays")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/4VQS0vEMBD-K2FOu5BAm_UBAcHDIij07MHxEJvRXbdN12QqW0r_u1Mf9STm9H3M5HvMCLneUevBAWh46ykNAl_27xTViFEpldkndmrrmdSVOvqUacYrBFvYM1OUpjxHWGOcMD7vY_j-dn3TpdazrA3yTFWZED7XZBbpxPdEh0XVh7D1Q17NVKvL9b8Kjc9cdZF3f0iYTTEnkkoS2LfElDK4cdKQuX8S-PCogU5HqpnCXe6ilB4RfoIhOPXbz1oELXwxXcYbU1pTXiDMTnKo-nAbwMW-acQ4da8i_0WnD9ekwWJoAQAA
      DocsSnippet(
         markdown = "Adds days to a date — positive to advance, negative to go back.",
         query = StubQueryMessage(
            schema = "",
            query = """
               given {
                  start: Date = parseDate("2024-01-15")
               }
               find {
                  @Format("yyyy-MM-dd")
                  nextWeek: Date = addDays(Date, 7)
                  @Format("yyyy-MM-dd")
                  lastMonth: Date = addDays(Date, -30)
               }
            """.trimIndent(),
            expectedJson = """{"nextWeek": "2024-01-22", "lastMonth": "2023-12-16"}"""
         )
      )
   )
}

object Now : FunctionApi {
   override val taxi: String = """
      [[
      Returns the current UTC instant.

      The output changes every time the function is called. Use `@Format` to control how the instant is serialised in query output.

      ```taxi
      // Stamp the current time on a record
      find {
         @Format("yyyy-MM-dd'T'HH:mm:ssXXX")
         processedAt: Instant = now()
      }
      ```
      ]]
      declare function now():Instant""".trimIndent()
   override val name: QualifiedName = stdLibName("dates.now")
}

object CurrentDate : FunctionApi {
   override val taxi: String = """
      [[
      Returns the current local date (no time component).

      ```taxi
      find {
         @Format("yyyy-MM-dd")
         today: Date = currentDate()
      }
      ```
      ]]
      declare function currentDate():Date""".trimIndent()
   override val name: QualifiedName = stdLibName("dates.currentDate")
}

object CurrentTime : FunctionApi {
   override val taxi: String = """
      [[
      Returns the current local time (no date component).

      ```taxi
      find {
         @Format("HH:mm:ss")
         timeNow: Time = currentTime()
      }
      ```
      ]]
      declare function currentTime():Time""".trimIndent()
   override val name: QualifiedName = stdLibName("dates.currentTime")
}

object CurrentDateTime : FunctionApi {
   override val taxi: String = """
      [[
      Returns the current local date and time.

      ```taxi
      find {
         @Format("yyyy-MM-dd HH:mm:ss")
         now: DateTime = currentDateTime()
      }
      ```
      ]]
      declare function currentDateTime():DateTime""".trimIndent()
   override val name: QualifiedName = stdLibName("dates.currentDateTime")
}


object ParseDate : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Parses a string into a date, datetime, or instant value. The target type determines which parsing rules are applied.

      Supported target types are `Date`, `DateTime`, and `Instant`. Use `@Format` on the result field to control the output serialisation format.

      ```taxi
      // Parse an ISO date string into a Date value
      find {
         @Format("yyyy-MM-dd")
         eventDate: Date = parseDate("2024-06-15")
      }
      ```
      ]]
      declare function <T> parseDate(String):T""".trimIndent()
   override val name: QualifiedName = stdLibName("dates.parseDate")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3VPy2oDMQz8FaFTA7vgpG0OgkIPISSF3HKrc3DWCs3Du6nlhYZl_71yHpSUVgczYkYz4w6l-uDgkBAL_Gw5nhRutrWHztYA8DptYnDpweJJp1wsSu8tDs6ck4lLTJBfeIGji8IZq3hkRk-lGZfD55v4LyOYzSgEErl3XG7D1TWjf52XQ0OPhow5X_faX3UucOIoSF1foKR2rfB9VSB_HblK7N-kqfWHncVLlkWC-7oF3Lic_puHn9CcKMlV-7lHqtvDQQvEZqcxl7X_Bp7xRdxdAQAA
      DocsSnippet(
         markdown = "Parses ISO date and datetime strings into their respective Taxi date types.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  @Format("yyyy-MM-dd")
                  asDate: Date = parseDate("2024-06-15")
                  @Format("yyyy-MM-dd HH:mm:ss")
                  asDateTime: DateTime = parseDate("2024-06-15T10:30:00")
               }
            """.trimIndent(),
            expectedJson = """{"asDate": "2024-06-15", "asDateTime": "2024-06-15 10:30:00"}"""
         )
      )
   )
}
