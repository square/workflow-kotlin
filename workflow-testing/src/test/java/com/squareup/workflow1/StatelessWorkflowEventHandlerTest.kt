package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.STABLE_EVENT_HANDLERS
import com.squareup.workflow1.testing.WorkflowTestParams
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * A lot of duplication here with [StatefulWorkflowEventHandlerTest]
 */
@OptIn(WorkflowExperimentalApi::class, WorkflowExperimentalRuntime::class)
class StatelessWorkflowEventHandlerTest {
  private data class Params(
    val remember: Boolean?,
    val runtimeConfig: RuntimeConfig
  ) {
    val remembering = remember ?: runtimeConfig.contains(STABLE_EVENT_HANDLERS)
  }

  private val rememberValues = sequenceOf(true, false, null)
  private val configValues = sequenceOf(emptySet(), setOf(STABLE_EVENT_HANDLERS))
  private val values = rememberValues.flatMap { remember ->
    configValues.map { Params(remember, it) }
  }
  private val parameterizedTestRunner = ParameterizedTestRunner<Params>()

  @Test fun eventHandler0() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, () -> Unit> {
        eventHandler("", remember = params.remembering) { setOutput("yay") }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke()
        assertEquals("yay", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler1() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1 ->
          setOutput(e1)
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("yay")
        assertEquals("yay", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler2() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2 ->
          setOutput("$e1-$e2")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b")
        assertEquals("a-b", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler3() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2, e3 ->
          setOutput("$e1-$e2-$e3")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b", "c")
        assertEquals("a-b-c", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler4() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S, S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2, e3, e4 ->
          setOutput("$e1-$e2-$e3-$e4")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b", "c", "d")
        assertEquals("a-b-c-d", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler5() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S, S, S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2, e3, e4, e5 ->
          setOutput("$e1-$e2-$e3-$e4-$e5")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b", "c", "d", "e")
        assertEquals("a-b-c-d-e", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler6() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S, S, S, S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2, e3, e4, e5, e6 ->
          setOutput("$e1-$e2-$e3-$e4-$e5-$e6")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b", "c", "d", "e", "f")
        assertEquals("a-b-c-d-e-f", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler7() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S, S, S, S, S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2, e3, e4, e5, e6, e7 ->
          setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b", "c", "d", "e", "f", "g")
        assertEquals("a-b-c-d-e-f-g", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler8() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S, S, S, S, S, S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2, e3, e4, e5, e6, e7, e8 ->
          setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b", "c", "d", "e", "f", "g", "h")
        assertEquals("a-b-c-d-e-f-g-h", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler9() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S, S, S, S, S, S, S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2, e3, e4, e5, e6, e7, e8, e9 ->
          setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8-$e9")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b", "c", "d", "e", "f", "g", "h", "i")
        assertEquals("a-b-c-d-e-f-g-h-i", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }

  @Test fun eventHandler10() {
    parameterizedTestRunner.runParametrizedTest(values) { params ->
      Workflow.stateless<Unit, S, (S, S, S, S, S, S, S, S, S, S) -> Unit> {
        eventHandler("", remember = params.remembering) { e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 ->
          setOutput("$e1-$e2-$e3-$e4-$e5-$e6-$e7-$e8-$e9-$e10")
        }
      }.launchForTestingFromStartWith(
        testParams = WorkflowTestParams(runtimeConfig = params.runtimeConfig)
      ) {
        val first = awaitNextRendering()
        first.invoke("a", "b", "c", "d", "e", "f", "g", "h", "i", "k")
        assertEquals("a-b-c-d-e-f-g-h-i-k", awaitNextOutput())
        val next = awaitNextRendering()
        if (params.remembering) {
          assertSame(first, next)
        } else {
          assertNotSame(first, next)
        }
      }
    }
  }
}
