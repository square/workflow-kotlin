package com.squareup.workflow1

import com.squareup.workflow1.testing.headlessIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatelessWorkflowTest {
  private var lastOutput: String? = null

  private fun onOutput(output: String) {
    lastOutput = output
  }

  // TODO param test for stable event handler
  // TODO same again for StatefulWorkflow
  @Test fun eventHandler0_gets_event() {
    Workflow.stateless<Unit, String, () -> Unit> {
      eventHandler("") { setOutput("yay") }
    }.headlessIntegrationTest(onOutput = ::onOutput) {
      assertNull(lastOutput)
      firstRendering.invoke()
      assertEquals("yay", lastOutput)
    }
  }

  //
  //   val context = createdPoisonedContext()
  //   val sink: () -> Unit = context.
  //     // Enable sink sends.
  //   context.freeze()
  //
  //   sink()
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("yay", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler1_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink = context.eventHandler("") { it: String -> setOutput(it) }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foo", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler2_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink = context.eventHandler("") { a: String, b: String -> setOutput(a + b) }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobar", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler3_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink = context.eventHandler("") { a: String, b: String, c: String, d: String ->
  //     setOutput(a + b + c + d)
  //   }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar", "baz")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobarbaz", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler4_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink = context.eventHandler("") { a: String, b: String, c: String, d: String ->
  //     setOutput(a + b + c + d)
  //   }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar", "baz", "bang")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobarbazbang", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler5_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink = context.eventHandler("") { a: String, b: String, c: String, d: String, e: String ->
  //     setOutput(a + b + c + d + e)
  //   }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar", "baz", "bang", "buzz")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobarbazbangbuzz", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler6_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink =
  //     context.eventHandler("") { a: String, b: String, c: String, d: String, e: String, f: String ->
  //       setOutput(a + b + c + d + e + f)
  //     }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar", "baz", "bang", "buzz", "qux")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobarbazbangbuzzqux", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler7_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink =
  //     context.eventHandler("") { a: S, b: S, c: S, d: S, e: S, f: S, g: S ->
  //       setOutput(a + b + c + d + e + f + g)
  //     }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar", "baz", "bang", "buzz", "qux", "corge")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobarbazbangbuzzquxcorge", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler8_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink =
  //     context.eventHandler("") { a: S, b: S, c: S, d: S, e: S, f: S, g: S, h: S ->
  //       setOutput(a + b + c + d + e + f + g + h)
  //     }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar", "baz", "bang", "buzz", "qux", "corge", "fred")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobarbazbangbuzzquxcorgefred", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler9_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink =
  //     context.eventHandler("") { a: S, b: S, c: S, d: S, e: S, f: S, g: S, h: S, i: S ->
  //       setOutput(a + b + c + d + e + f + g + h + i)
  //     }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar", "baz", "bang", "buzz", "qux", "corge", "fred", "xyzzy")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobarbazbangbuzzquxcorgefredxyzzy", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
  //
  // @Test fun eventHandler10_gets_event() {
  //   val context = createdPoisonedContext()
  //   val sink =
  //     context.eventHandler("") { a: S, b: S, c: S, d: S, e: S, f: S, g: S, h: S, i: S, j: S ->
  //       setOutput(a + b + c + d + e + f + g + h + i + j)
  //     }
  //   // Enable sink sends.
  //   context.freeze()
  //
  //   sink("foo", "bar", "baz", "bang", "buzz", "qux", "corge", "fred", "xyzzy", "plugh")
  //
  //   val update = outputs.tryReceive().getOrNull()!!
  //   val (state, result) = update.applyTo("props", "state")
  //   // assertEquals("state", state)
  //   assertEquals("foobarbazbangbuzzquxcorgefredxyzzyplugh", result.output!!.value)
  //   assertFalse(result.stateChanged)
  // }
}
