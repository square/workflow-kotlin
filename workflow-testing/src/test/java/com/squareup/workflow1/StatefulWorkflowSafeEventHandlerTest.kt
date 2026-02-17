@file:Suppress("DEPRECATION")

package com.squareup.workflow1

import com.squareup.workflow1.StatefulWorkflowSafeEventHandlerTest.State.Able
import com.squareup.workflow1.StatefulWorkflowSafeEventHandlerTest.State.Baker
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import com.squareup.workflow1.testing.launchForTestingFromStateWith
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class StatefulWorkflowSafeEventHandlerTest {
  private sealed interface State {
    data object Able : State
    data object Baker : State
  }

  private var failedCast = ""
  private val onFailedCast: (name: S, type: KClass<*>, state: State) -> Unit =
    { name, expectedType, state ->
      failedCast = "$name expected: ${expectedType.simpleName} got: $state"
    }

  @Test fun safeEventHandler0() {
    val w = Workflow.stateful<State, S, () -> Unit>(Able) {
      safeEventHandler<Able>(
        "name",
        update = { setOutput("yay") },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke()
      assertEquals("yay", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke()
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler1() {
    val w = Workflow.stateful<State, S, (S) -> Unit>(Able) {
      safeEventHandler<Able, S>(
        "name",
        update = { _, e1 -> setOutput(e1) },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("yay")
      assertEquals("yay", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("yay")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler2() {
    val w = Workflow.stateful<State, S, (S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S>(
        "name",
        update = { _, e1, e2 -> setOutput("$e1-$e2") },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b")
      assertEquals("a-b", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler3() {
    val w = Workflow.stateful<State, S, (S, S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S, S>(
        "name",
        update = { _, e1, e2, e3 -> setOutput("$e1-$e2-$e3") },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c")
      assertEquals("a-b-c", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler4() {
    val w = Workflow.stateful<State, S, (S, S, S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S, S, S>(
        "name",
        update = { _, e1, e2, e3, e4 -> setOutput("$e1-$e2-$e3-$e4") },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d")
      assertEquals("a-b-c-d", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "", "", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler5() {
    val w = Workflow.stateful<State, S, (S, S, S, S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S, S, S, S>(
        "name",
        update = { _, e1, e2, e3, e4, e5 -> setOutput("$e1-$e2-$e3-$e4-$e5") },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e")
      assertEquals("a-b-c-d-e", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "", "", "", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler6() {
    val w = Workflow.stateful<State, S, (S, S, S, S, S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S, S, S, S, S>(
        "name",
        update = { _, e1, e2, e3, e4, e5, e6 -> setOutput("$e1-$e2-$e3-$e4-$e5-$e6") },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f")
      assertEquals("a-b-c-d-e-f", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "", "", "", "", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler7() {
    val w = Workflow.stateful<State, S, (S, S, S, S, S, S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S, S, S, S, S, S>(
        "name",
        update = { _, e1, e2, e3, e4, e5, e6, e7 -> setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7") },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f", "g")
      assertEquals("a-b-c-d-e-f-g", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "", "", "", "", "", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler8() {
    val w = Workflow.stateful<State, S, (S, S, S, S, S, S, S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S, S, S, S, S, S, S>(
        "name",
        update = { _, e1, e2, e3, e4, e5, e6, e7, e8 ->
          setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8")
        },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f", "g", "h")
      assertEquals("a-b-c-d-e-f-g-h", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "", "", "", "", "", "", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler9() {
    val w = Workflow.stateful<State, S, (S, S, S, S, S, S, S, S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S, S, S, S, S, S, S, S>(
        "name",
        update = { _, e1, e2, e3, e4, e5, e6, e7, e8, e9 ->
          setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8-$e9")
        },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f", "g", "h", "i")
      assertEquals("a-b-c-d-e-f-g-h-i", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "", "", "", "", "", "", "", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  @Test fun safeEventHandler10() {
    val w = Workflow.stateful<State, S, (S, S, S, S, S, S, S, S, S, S) -> Unit>(Able) {
      safeEventHandler<Able, S, S, S, S, S, S, S, S, S, S>(
        "name",
        update = { _, e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 ->
          setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8-$e9-$e10")
        },
        onFailedCast = onFailedCast
      )
    }
    w.launchForTestingFromStartWith {
      val first = awaitNextRendering()
      first.invoke("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
      assertEquals("a-b-c-d-e-f-g-h-i-j", awaitNextOutput())
      assertNoFailedCast()
    }
    w.launchForTestingFromStateWith(Baker) {
      val first = awaitNextRendering()
      first.invoke("", "", "", "", "", "", "", "", "", "")
      awaitRuntimeSettled()
      assertFailedCast()
    }
  }

  private fun assertNoFailedCast() {
    assertEquals("", failedCast)
  }

  private fun assertFailedCast() {
    assertEquals("name expected: Able got: Baker", failedCast)
  }
}
