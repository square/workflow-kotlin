@file:Suppress("JUnitMalformedDeclaration", "DEPRECATION")

package com.squareup.workflow1

import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RENDER_PER_ACTION
import com.squareup.workflow1.RuntimeConfigOptions.STABLE_EVENT_HANDLERS
import com.squareup.workflow1.testing.WorkflowTestParams
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * A lot of duplication here with [StatelessWorkflowEventHandlerTest]
 */
@OptIn(WorkflowExperimentalRuntime::class)
@Burst
class StatefulWorkflowEventHandlerTest(
  private val remembering: Boolean = false,
  stableEventHandlers: Boolean = false,
) {

  private val runtimeConfig = if (stableEventHandlers) {
    setOf(
      STABLE_EVENT_HANDLERS
    )
  } else {
    RENDER_PER_ACTION
  }

  @Test fun eventHandler0() {
    Workflow.stateful(Unit) {
      eventHandler("", remember = remembering) { setOutput("yay") }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke()
      assertEquals("yay", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler1() {
    Workflow.stateful<Unit, S, (S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1 ->
        setOutput(e1)
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("yay")
      assertEquals("yay", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler2() {
    Workflow.stateful<Unit, S, (S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2 ->
        setOutput("$e1-$e2")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b")
      assertEquals("a-b", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler3() {
    Workflow.stateful<Unit, S, (S, S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2, e3 ->
        setOutput("$e1-$e2-$e3")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c")
      assertEquals("a-b-c", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler4() {
    Workflow.stateful<Unit, S, (S, S, S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2, e3, e4 ->
        setOutput("$e1-$e2-$e3-$e4")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d")
      assertEquals("a-b-c-d", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler5() {
    Workflow.stateful<Unit, S, (S, S, S, S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2, e3, e4, e5 ->
        setOutput("$e1-$e2-$e3-$e4-$e5")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e")
      assertEquals("a-b-c-d-e", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler6() {
    Workflow.stateful<Unit, S, (S, S, S, S, S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2, e3, e4, e5, e6 ->
        setOutput("$e1-$e2-$e3-$e4-$e5-$e6")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f")
      assertEquals("a-b-c-d-e-f", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler7() {
    Workflow.stateful<Unit, S, (S, S, S, S, S, S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2, e3, e4, e5, e6, e7 ->
        setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f", "g")
      assertEquals("a-b-c-d-e-f-g", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler8() {
    Workflow.stateful<Unit, S, (S, S, S, S, S, S, S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2, e3, e4, e5, e6, e7, e8 ->
        setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f", "g", "h")
      assertEquals("a-b-c-d-e-f-g-h", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler9() {
    Workflow.stateful<Unit, S, (S, S, S, S, S, S, S, S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2, e3, e4, e5, e6, e7, e8, e9 ->
        setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8-$e9")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f", "g", "h", "i")
      assertEquals("a-b-c-d-e-f-g-h-i", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }

  @Test fun eventHandler10() {
    Workflow.stateful<Unit, S, (S, S, S, S, S, S, S, S, S, S) -> Unit>(Unit) {
      eventHandler("", remember = remembering) { e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 ->
        setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8-$e9-$e10")
      }
    }.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(runtimeConfig = runtimeConfig)
    ) {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f", "g", "h", "i", "k")
      assertEquals("a-b-c-d-e-f-g-h-i-k", awaitNextOutput())
      val next = awaitNextRendering()
      if (remembering) {
        assertSame(first, next)
      } else {
        assertNotSame(first, next)
      }
    }
  }
}
