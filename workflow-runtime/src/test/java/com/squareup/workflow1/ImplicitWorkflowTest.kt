package com.squareup.workflow1

import com.squareup.workflow1.ImplicitWorkflowTest.TestWorkflow.Props
import com.squareup.workflow1.ImplicitWorkflowTest.TestWorkflow.Rendering
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImplicitWorkflowTest {

  @Test fun `implicit workflow works`() {
    runBlockingTest {
      val recordedOutputs = mutableListOf<String>()
      val props = MutableStateFlow(Props("initial") { recordedOutputs += "counter: $it" })
      val workflowScope = this + Job()
      val renderings = renderWorkflowIn(TestWorkflow, workflowScope, props, onOutput = {})

      assertEquals("initial", renderings.value.rendering.props)
      assertEquals("0", renderings.value.rendering.counter)
      assertEquals(emptyList<String>(), recordedOutputs)

      renderings.value.rendering.onClick()
      advanceUntilIdle()

      assertEquals("initial", renderings.value.rendering.props)
      assertEquals("1", renderings.value.rendering.counter)
      // Output uses previous counter value since it doesn't update in-place.
      assertEquals(listOf("counter: 0"), recordedOutputs)

      // Changing the props should use a different state, resetting the counter.
      props.value = props.value.copy(text = "new props")
      advanceUntilIdle()

      assertEquals("new props", renderings.value.rendering.props)
      assertEquals("42", renderings.value.rendering.counter)
      assertEquals(listOf("counter: 0"), recordedOutputs)

      renderings.value.rendering.onClick()
      advanceUntilIdle()

      assertEquals("new props", renderings.value.rendering.props)
      assertEquals("43", renderings.value.rendering.counter)
      assertEquals(listOf("counter: 0", "counter: 42"), recordedOutputs)

      renderings.value.rendering.onClick()
      advanceUntilIdle()

      assertEquals("new props", renderings.value.rendering.props)
      assertEquals("44", renderings.value.rendering.counter)
      assertEquals(listOf("counter: 0", "counter: 42", "counter: 43"), recordedOutputs)

      // Go back to initial state to restore state to pre-props change.
      // This is not ideal behavior – ideally the state would reset between render passes.
      // Use a counter to track stale states?
      props.value = props.value.copy(text = "initial")
      advanceUntilIdle()

      assertEquals("initial", renderings.value.rendering.props)
      assertEquals("1", renderings.value.rendering.counter)
      assertEquals(listOf("counter: 0", "counter: 42", "counter: 43"), recordedOutputs)

      assertTrue(workflowScope.isActive)
      workflowScope.cancel()
    }
  }

  private object TestWorkflow : ImplicitWorkflow<Props, Rendering>() {
    data class Props(
      val text: String,
      val onCounterChanged: (Int) -> Unit
    )

    data class Rendering(
      val props: String,
      val counter: String,
      val onClick: () -> Unit
    )

    override fun Ctx.render(props: Props): Rendering {
      if (props.text == "initial") {
        // First counter property
        var counter by state { 0 }
        return Rendering(
            props = props.text,
            counter = counter.toString(),
            onClick = {
              // Can't be ++ or += because of missing compiler support. Maybe fixed in 1.4? Compose
              // uses this pattern extensively.
              // See https://youtrack.jetbrains.com/issue/KT-14833 (according to this, has been fixed
              // in the IR compiler)
              // See https://youtrack.jetbrains.com/issue/KT-39804 (dup of above)
              // See https://stackoverflow.com/questions/45571272/augment-assignment-and-increment-are-not-supported-for-local-delegated-propertie
              @Suppress("ReplaceWithOperatorAssignment")
              counter = counter + 1
              props.onCounterChanged(counter)
            }
        )
      } else {
        // Second counter property
        var counter by state { 42 }
        return Rendering(
            props = props.text,
            counter = counter.toString(),
            onClick = {
              // Can't be ++ or += because of missing compiler support. Maybe fixed in 1.4? Compose
              // uses this pattern extensively.
              // See https://youtrack.jetbrains.com/issue/KT-14833 (according to this, has been fixed
              // in the IR compiler)
              // See https://youtrack.jetbrains.com/issue/KT-39804 (dup of above)
              // See https://stackoverflow.com/questions/45571272/augment-assignment-and-increment-are-not-supported-for-local-delegated-propertie
              @Suppress("ReplaceWithOperatorAssignment")
              counter = counter + 1
              props.onCounterChanged(counter)
            }
        )
      }
    }
  }
}
