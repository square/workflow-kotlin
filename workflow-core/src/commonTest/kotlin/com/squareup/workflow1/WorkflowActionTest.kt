package com.squareup.workflow1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class WorkflowActionTest {

  @Test fun `applyTo works when no output is set`() {
    val action = object : WorkflowAction<String, String, String?>() {
      override fun Updater.apply() {
        state = "state: $state, props: $props"
      }
    }
    val (state, output) = action.applyTo("props", "state")
    assertEquals("state: state, props: props", state)
    assertNull(output)
  }

  @Test fun `applyTo works when null output is set`() {
    val action = object : WorkflowAction<String, String, String?>() {
      override fun Updater.apply() {
        state = "state: $state, props: $props"
        setOutput(null)
      }
    }
    val (state, output) = action.applyTo("props", "state")
    assertEquals("state: state, props: props", state)
    assertNotNull(output)
    assertNull(output.value)
  }

  @Test fun `applyTo works when non-null output is set`() {
    val action = object : WorkflowAction<String, String, String?>() {
      override fun Updater.apply() {
        state = "state: $state, props: $props"
        setOutput("output")
      }
    }
    val (state, output) = action.applyTo("props", "state")
    assertEquals("state: state, props: props", state)
    assertNotNull(output)
    assertEquals("output", output.value)
  }
}
