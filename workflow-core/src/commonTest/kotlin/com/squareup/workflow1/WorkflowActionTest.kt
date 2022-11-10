package com.squareup.workflow1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class WorkflowActionTest {

  @Test fun applyTo_works_when_no_output_is_set() {
    val action = object : WorkflowAction<String, String, String?>() {
      override fun Updater.apply() {
        state = "state: $state, props: $props"
      }
    }
    val (state, output) = action.applyTo("props", "state")
    assertEquals("state: state, props: props", state)
    assertNull(output)
  }

  @Test fun applyTo_works_when_null_output_is_set() {
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

  @Test fun applyTo_works_when_non_null_output_is_set() {
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
