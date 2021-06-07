package com.squareup.tracing

import com.squareup.tracing.TraceEvent.Instant
import kotlinx.coroutines.runBlocking
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

internal class TraceEncoderTest {

  /**
   * [TimeMark] that always returns [now] as [elapsedNow].
   */
  private class FakeTimeMark : TimeMark {
    var now: Long = 0L
    override val elapsedNow: Long
      get() = now
  }

  @Test fun `multiple events sanity check`() {
    val firstBatch = listOf(
      traceEvent("one"),
      traceEvent("two")
    )
    val secondBatch = listOf(traceEvent("three"))

    val buffer = Buffer()
    runBlocking {
      val fakeTimeMark = FakeTimeMark()
      val encoder = TraceEncoder(this, start = fakeTimeMark) { buffer }
      val logger = encoder.createLogger("process", "thread")

      fakeTimeMark.now = 1L
      logger.log(firstBatch)

      fakeTimeMark.now = 2L
      logger.log(secondBatch)

      encoder.close()
    }
    val serialized = buffer.readUtf8()

    val expectedValue = """
      [
      {"name":"process_name","ph":"M","ts":0,"pid":0,"tid":0,"args":{"name":"process"}},
      {"name":"thread_name","ph":"M","ts":0,"pid":0,"tid":0,"args":{"name":"thread"}},
      {"name":"one","ph":"i","ts":1,"pid":0,"tid":0,"s":"t","args":{}},
      {"name":"two","ph":"i","ts":1,"pid":0,"tid":0,"s":"t","args":{}},
      {"name":"three","ph":"i","ts":2,"pid":0,"tid":0,"s":"t","args":{}},

    """.trimIndent()

    assertEquals(expectedValue, serialized)
  }

  private fun traceEvent(name: String) = Instant(name = name)
}
