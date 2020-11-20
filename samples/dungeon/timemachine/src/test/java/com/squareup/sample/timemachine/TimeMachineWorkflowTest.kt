package com.squareup.sample.timemachine

import com.google.common.truth.Truth.assertThat
import com.squareup.sample.timemachine.TimeMachineWorkflow.TimeMachineProps
import com.squareup.sample.timemachine.TimeMachineWorkflow.TimeMachineProps.PlayingBackAt
import com.squareup.sample.timemachine.TimeMachineWorkflow.TimeMachineProps.Recording
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.stateful
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import kotlin.time.seconds

@OptIn(ExperimentalTime::class)
class TimeMachineWorkflowTest {

  @Test fun `records and plays back`() {
    data class DelegateRendering(
      val state: String,
      val setState: (String) -> Unit
    )

    val delegateWorkflow = Workflow.stateful<String, Nothing, DelegateRendering>(
        initialState = "initial",
        render = { state ->
          DelegateRendering(state, setState = eventHandler { s -> this.state = s })
        }
    )
    val clock = TestTimeSource()
    val tmWorkflow = TimeMachineWorkflow(delegateWorkflow, clock)

    tmWorkflow.launchForTestingFromStartWith(Recording(Unit) as TimeMachineProps<Unit>) {
      // Record some renderings.
      awaitNextRendering().let { rendering ->
        assertThat(rendering.value.state).isEqualTo("initial")
        clock += 1.seconds
        rendering.value.setState("second")
      }
      awaitNextRendering().let { rendering ->
        assertThat(rendering.value.state).isEqualTo("second")
      }

      // Play them back.
      sendProps(PlayingBackAt(Unit, Duration.ZERO))
      awaitNextRendering().let { rendering ->
        assertThat(rendering.value.state).isEqualTo("initial")
        assertThat(rendering.totalDuration).isEqualTo(1.seconds)
      }

      clock += 1.seconds
      sendProps(PlayingBackAt(Unit, 1.seconds))
      awaitNextRendering().let { rendering ->
        assertThat(rendering.value.state).isEqualTo("second")
        assertThat(rendering.totalDuration).isEqualTo(1.seconds)

        rendering.value.setState("third")
      }

      // Go back to recording.
      sendProps(Recording(Unit))
      awaitNextRendering().let { rendering ->
        assertThat(rendering.value.state).isEqualTo("third")
        assertThat(rendering.totalDuration).isEqualTo(2.seconds)
      }
    }
  }
}
