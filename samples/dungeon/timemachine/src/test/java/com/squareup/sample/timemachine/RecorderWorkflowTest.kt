package com.squareup.sample.timemachine

import com.google.common.truth.Truth.assertThat
import com.squareup.sample.timemachine.RecorderWorkflow.RecorderProps.PlaybackAt
import com.squareup.sample.timemachine.RecorderWorkflow.RecorderProps.RecordValue
import com.squareup.sample.timemachine.RecorderWorkflow.Recording
import com.squareup.workflow1.testing.testRender
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

@OptIn(ExperimentalTime::class)
class RecorderWorkflowTest {

  @Test fun `onPropsChanged records value when recording`() {
    val clock = TestTimeSource()
    val workflow = RecorderWorkflow<String>(clock)
    val startTime = clock.markNow()
    clock += Duration.milliseconds(42)

    val newState = workflow.onPropsChanged(
        old = RecordValue("foo"),
        new = RecordValue("bar"),
        state = Recording(
            startTime = startTime,
            series = TimeSeries(listOf("foo" to Duration.milliseconds(0)))
        )
    )

    assertThat(newState.series.duration).isEqualTo(Duration.milliseconds(42))
    assertThat(newState.series.values.toList()).isEqualTo(listOf("foo", "bar"))
  }

  @Test fun `onPropsChanged doesn't record value when not recording`() {
    val clock = TestTimeSource()
    val workflow = RecorderWorkflow<String>(clock)
    val startTime = clock.markNow()
    clock += Duration.milliseconds(42)

    val newState = workflow.onPropsChanged(
        old = RecordValue("foo"),
        new = PlaybackAt(Duration.milliseconds(42)),
        state = Recording(
            startTime = startTime,
            series = TimeSeries(listOf("foo" to Duration.milliseconds(0)))
        )
    )

    assertThat(newState.series.duration).isEqualTo(Duration.milliseconds(0))
    assertThat(newState.series.values.toList()).isEqualTo(listOf("foo"))
  }

  @Test fun `render returns recorded value when recording`() {
    val clock = TestTimeSource()
    val workflow = RecorderWorkflow<String>(clock)
    val startTime = clock.markNow()

    workflow
        .testRender(
            props = RecordValue("bar"),
            initialState = Recording(
                startTime = startTime,
                series = TimeSeries(listOf("foo" to Duration.milliseconds(42)))
            )
        )
        .render { rendering ->
          assertThat(rendering.value).isEqualTo("bar")
        }
  }

  @Test fun `render returns recorded value when playing back`() {
    val clock = TestTimeSource()
    val workflow = RecorderWorkflow<String>(clock)
    val startTime = clock.markNow()

    workflow
        .testRender(
            props = PlaybackAt(Duration.milliseconds(10)),
            initialState = Recording(
                startTime = startTime,
                series = TimeSeries(
                    listOf(
                        "foo" to Duration.milliseconds(0),
                        "bar" to Duration.milliseconds(42)
                    )
                )
            )
        )
        .render { rendering ->
          assertThat(rendering.value).isEqualTo("foo")
        }
  }
}
