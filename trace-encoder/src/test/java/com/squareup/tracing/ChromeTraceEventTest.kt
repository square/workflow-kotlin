package com.squareup.tracing

import com.squareup.tracing.ChromeTraceEvent.Companion.INSTANT_SCOPE_PROCESS
import com.squareup.tracing.ChromeTraceEvent.Phase.ASYNC_BEGIN
import okio.Buffer
import org.junit.Test
import kotlin.test.assertEquals

class ChromeTraceEventTest {

  @Test fun `serialization golden value`() {
    val traceEvent = ChromeTraceEvent(
      name = "name",
      category = "category",
      phase = ASYNC_BEGIN,
      timestampMicros = 123456,
      processId = 1,
      threadId = 1,
      id = -123L,
      scope = INSTANT_SCOPE_PROCESS,
      args = mapOf("key" to "value")
    )
    val serialized = Buffer()
      .also { traceEvent.writeTo(it) }
      .readUtf8()
    val expectedValue =
      """{"name":"name","cat":"category","ph":"b","ts":123456,"pid":1,"tid":1,"id":-123,""" +
        """"s":"p","args":{"key":"value"}}"""

    assertEquals(expectedValue, serialized)
  }
}
