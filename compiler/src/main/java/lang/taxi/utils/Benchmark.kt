@file:Suppress("DuplicatedCode")

package lang.taxi.utils

import com.google.common.base.Stopwatch
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object Benchmark {
   fun <T> warmup(name: String, warmup: Int = 10, process: (Stopwatch) -> T): List<T> {
      log().info("Starting warmup for $name")
      val results = (0..warmup).map { count ->
         val stopWatch = Stopwatch.createStarted()
         val result = process(stopWatch)
         log().info("$name warmup $count of $warmup completed in ${stopWatch.elapsed(TimeUnit.MILLISECONDS)}ms")
         result
      }
      log().info("Warmup finished.")
      return results
   }

   fun benchmark(
      name: String,
      warmup: Int = 10,
      iterations: Int = 50,
      logInterval: Int = Math.floorDiv(iterations, 10),
      timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
      process: (Stopwatch) -> Any
   ) {
      warmup(name, warmup, process)
      val executions = (0..iterations).map { count ->
         val stopWatch = Stopwatch.createStarted()
         val result = process(stopWatch)
         val elapsed = stopWatch.elapsed(timeUnit)
         if (count % logInterval == 0) {
            log().info("$name run $count of $iterations completed in ${elapsed}${timeUnit.suffix()}")
         }
         elapsed to result
      }
      val durations = executions.map { it.first }
      val collectionSize =
         executions.mapNotNull { if (it.second is Collection<*>) (it.second as Collection<*>).size else null }
      val avgSize = if (collectionSize.isNotEmpty()) " returning an average of ${
         collectionSize.average().roundToInt()
      } entries" else ""
      log().info(
         "Completed with average process time of ${
            durations.average().toBigDecimal().setScale(2, RoundingMode.HALF_EVEN)
         }${timeUnit.suffix()}$avgSize"
      )
   }
}

fun TimeUnit.suffix(): String {
   return when (this) {
      TimeUnit.SECONDS -> "s"
      TimeUnit.MILLISECONDS -> "ms"
      TimeUnit.MICROSECONDS -> "μs"
      TimeUnit.NANOSECONDS -> "ns"
      else -> this.name
   }
}
