package com.squareup.workflow1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class WorkflowActionTest {

  @Test fun applyTo_works_when_state_is_not_changed() {
    val action = object : WorkflowAction<String, String, String?>() {
      override fun Updater.apply() {
        // no-op
      }
    }
    val (state, result) = action.applyTo("props", "state")
    assertEquals("state", state)
    assertNull(result.output)
    assertFalse(result.outputSet)
    assertFalse(result.stateChanged)
  }

  @Test fun applyTo_works_when_no_output_is_set() {
    val action = object : WorkflowAction<String, String, String?>() {
      override fun Updater.apply() {
        state = "state: $state, props: $props"
      }
    }
    val (state, result) = action.applyTo("props", "state")
    assertEquals("state: state, props: props", state)
    assertNull(result.output)
    assertFalse(result.outputSet)
    assertTrue(result.stateChanged)
  }

  @Test fun applyTo_works_when_null_output_is_set() {
    val action = object : WorkflowAction<String, String, String?>() {
      override fun Updater.apply() {
        state = "state: $state, props: $props"
        setOutput(null)
      }
    }
    val (state, result) = action.applyTo("props", "state")
    assertEquals("state: state, props: props", state)
    assertNotNull(result)
    assertNull(result.output)
    assertTrue(result.outputSet)
    assertTrue(result.stateChanged)
  }

  @Test fun applyTo_works_when_non_null_output_is_set() {
    val action = object : WorkflowAction<String, String, String?>() {
      override fun Updater.apply() {
        state = "state: $state, props: $props"
        setOutput("output")
      }
    }
    val (state, result) = action.applyTo("props", "state")
    assertEquals("state: state, props: props", state)
    assertNotNull(result)
    assertEquals("output", result.output)
    assertTrue(result.outputSet)
    assertTrue(result.stateChanged)
  }
}
