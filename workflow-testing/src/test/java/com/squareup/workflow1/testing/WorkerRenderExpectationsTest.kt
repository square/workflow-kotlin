package com.squareup.workflow1.testing

import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateless
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class WorkerRenderExpectationsTest {

  @Test fun `expectWorkerOutputting() works`() {
    val stringWorker = object : Worker<String> {
      override fun run(): Flow<String> = emptyFlow()
    }
    val intWorker = object : Worker<Int> {
      override fun run(): Flow<Int> = emptyFlow()
    }
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(stringWorker) { action("") { setOutput(it) } }
      runningWorker(intWorker) { action("") { setOutput(it.toString()) } }
    }

    // Exact string match
    workflow.testRender(Unit)
      .expectWorkerOutputting(typeOf<String>())
      .render()
    workflow.testRender(Unit)
      .expectWorkerOutputting(typeOf<String>(), output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Supertype match
    workflow.testRender(Unit)
      .expectWorkerOutputting(typeOf<CharSequence>())
      .render()
    workflow.testRender(Unit)
      .expectWorkerOutputting(typeOf<String>(), output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Other type match
    workflow.testRender(Unit)
      .expectWorkerOutputting(typeOf<Int>())
      .render()
    workflow.testRender(Unit)
      .expectWorkerOutputting(typeOf<Int>(), output = WorkflowOutput(42))
      .render()
      .verifyActionResult { _, output -> assertEquals("42", output?.value) }
  }

  @Test fun `expectWorker(worker instance) works`() {
    val stringWorker = object : Worker<String> {
      override fun run(): Flow<String> = emptyFlow()
    }
    val intWorker = object : Worker<Int> {
      override fun run(): Flow<Int> = emptyFlow()
    }
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(stringWorker) { action("") { setOutput(it) } }
      runningWorker(intWorker) { action("") { setOutput(it.toString()) } }
    }

    // Exact string match
    workflow.testRender(Unit)
      .expectWorker(stringWorker)
      .render()
    workflow.testRender(Unit)
      .expectWorker(stringWorker, output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Other type match
    workflow.testRender(Unit)
      .expectWorker(intWorker)
      .render()
    workflow.testRender(Unit)
      .expectWorker(intWorker, output = WorkflowOutput(42))
      .render()
      .verifyActionResult { _, output -> assertEquals("42", output?.value) }
  }

  @Test fun `expectWorker(worker class) works`() {
    abstract class EmptyWorkerCovariant<out T> : Worker<T> {
      final override fun run(): Flow<T> = emptyFlow()
    }

    open class EmptyWorker<T> : EmptyWorkerCovariant<T>()

    class EmptyStringWorker : EmptyWorker<String>()
    class EmptyIntWorker : EmptyWorker<Int>()

    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(EmptyStringWorker()) { action("") { setOutput(it) } }
      runningWorker(EmptyIntWorker()) { action("") { setOutput(it.toString()) } }
    }

    // Exact string match
    workflow.testRender(Unit)
      .expectWorker(EmptyStringWorker::class)
      .render()
    workflow.testRender(Unit)
      .expectWorker(EmptyStringWorker::class, output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Supertype match without type args
    workflow.testRender(Unit)
      .expectWorker(EmptyWorker::class)
      .render()
    workflow.testRender(Unit)
      .expectWorker(EmptyWorker::class, output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Invariant supertype match with exact type args
    workflow.testRender(Unit)
      .expectWorker(EmptyWorker::class)
      .render()
    workflow.testRender(Unit)
      .expectWorker(EmptyWorker::class, output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Covariant supertype match
    workflow.testRender(Unit)
      .expectWorker(EmptyWorkerCovariant::class)
      .render()
    workflow.testRender(Unit)
      .expectWorker(EmptyWorkerCovariant::class, output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Other type match
    workflow.testRender(Unit)
      .expectWorker(EmptyIntWorker::class)
      .render()
    workflow.testRender(Unit)
      .expectWorker(EmptyIntWorker::class, output = WorkflowOutput(42))
      .render()
      .verifyActionResult { _, output -> assertEquals("42", output?.value) }

    // Match with instance of parameterized workflow
    Workflow
      .stateless<Unit, String, Unit> {
        runningWorker(EmptyWorker<String>()) { action("") { setOutput(it) } }
      }
      .let {
        it.testRender(Unit)
          .expectWorker(EmptyWorker::class)
          .render()
        it.testRender(Unit)
          .expectWorker(EmptyWorker::class, output = WorkflowOutput("foo"))
          .render()
          .verifyActionResult { _, output -> assertEquals("foo", output?.value) }
      }
  }

  @Test fun `expectWorker(worker KType) works`() {
    abstract class EmptyWorkerCovariant<out T> : Worker<T> {
      final override fun run(): Flow<T> = emptyFlow()
    }

    open class EmptyWorker<T> : EmptyWorkerCovariant<T>()

    class EmptyStringWorker : EmptyWorker<String>()
    class EmptyIntWorker : EmptyWorker<Int>()

    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(EmptyStringWorker()) { action("") { setOutput(it) } }
      runningWorker(EmptyIntWorker()) { action("") { setOutput(it.toString()) } }
    }

    // Exact string match
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyStringWorker>())
      .render()
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyStringWorker>(), output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Supertype match without type args
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorker<*>>())
      .render()
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorker<*>>(), output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Invariant supertype match with exact type args
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorker<String>>())
      .render()
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorker<String>>(), output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Invariant supertype match with supertype args
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorker<CharSequence>>())
      .expectWorker(EmptyIntWorker::class)
      .apply {
        val error = assertFailsWith<AssertionError> { render() }
        assertEquals(
          "Expected 1 more workflows, workers, or side effects to be run:\n" +
            "  ${typeOf<EmptyWorker<CharSequence>>()}",
          error.message
        )
      }

    // Covariant supertype match with exact type args
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorkerCovariant<String>>())
      .render()
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorkerCovariant<String>>(), output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Covariant supertype match with supertype args
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorkerCovariant<CharSequence>>())
      .render()
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyWorkerCovariant<CharSequence>>(), output = WorkflowOutput("foo"))
      .render()
      .verifyActionResult { _, output -> assertEquals("foo", output?.value) }

    // Other type match
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyIntWorker>())
      .render()
    workflow.testRender(Unit)
      .expectWorker(typeOf<EmptyIntWorker>(), output = WorkflowOutput(42))
      .render()
      .verifyActionResult { _, output -> assertEquals("42", output?.value) }

    // Match with instance of parameterized workflow
    Workflow
      .stateless<Unit, String, Unit> {
        runningWorker(EmptyWorker<String>()) { action("") { setOutput(it) } }
      }
      .let {
        it.testRender(Unit)
          .expectWorker(typeOf<EmptyWorker<String>>())
          .render()
        it.testRender(Unit)
          .expectWorker(typeOf<EmptyWorker<String>>(), output = WorkflowOutput("foo"))
          .render()
          .verifyActionResult { _, output -> assertEquals("foo", output?.value) }
      }
  }

  @Test fun `expectWorker(instance) considers doesSameWorkAs`() {
    open class RequestWorker(private val request: String) : Worker<String> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
        otherWorker is RequestWorker && otherWorker.request == request

      override fun toString(): String = "RequestWorker(request=$request)"
      override fun run(): Flow<String> = fail()
    }

    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(RequestWorker("foo")) { action("") { setOutput(it) } }
    }

    // Matching input
    workflow.testRender(Unit)
      .expectWorker(RequestWorker("foo"))
      .render()

    // Non-matching input
    workflow.testRender(Unit)
      .expectWorker(RequestWorker("bar"))
      .apply {
        val error = assertFailsWith<AssertionError> { render() }
        assertEquals(
          "Expected actual worker's doesSameWorkAs to return true for expected worker \n" +
            "  expected=RequestWorker(request=bar)\n" +
            "  actual=RequestWorker(request=foo)",
          error.message
        )
      }
  }

  @Test fun `expectWorker(instance) with default doesSameWorkAs() and different types`() {
    open class ParentWorker : Worker<String> {
      override fun run(): Flow<String> = fail()
      override fun toString(): String = "ParentWorker"
    }

    class ChildWorker : ParentWorker() {
      override fun toString(): String = "ChildWorker"
    }

    // Use an explicit type to trick the runtime.
    val trickWorker: ParentWorker = ChildWorker()
    val honestWorker = ParentWorker()

    // This shouldn't even render, since workers should be the same type?
    val multiWorkflow = Workflow.stateless<Unit, String, Unit> {
      // TODO(https://github.com/square/workflow-kotlin/issues/120) There's a major bug here,
      //  renderTester is allowing duplicate workflows to be rendered. This test should fail because
      //  of that, not because trick doesn't match the expectation for honest.
      runningWorker(trickWorker) { action("") { setOutput(it) } }
      runningWorker(honestWorker) { action("") { setOutput(it) } }
    }
    multiWorkflow.testRender(Unit)
      .expectWorker(trickWorker, description = "trick")
      .expectWorker(honestWorker, description = "honest")
      .apply {
        val error = assertFailsWith<AssertionError> { render() }
        assertEquals(
          "Expected actual worker's doesSameWorkAs to return true for " +
            "expected worker honest\n" +
            "  expected=ParentWorker\n" +
            "  actual=ChildWorker",
          error.message
        )
      }

    // Using keys clears up the ambiguity.
    val multiKeyedWorkflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(trickWorker, key = "trick") { action("") { setOutput(it) } }
      runningWorker(honestWorker, key = "honest") { action("") { setOutput(it) } }
    }
    multiKeyedWorkflow.testRender(Unit)
      .expectWorker(trickWorker, key = "trick", description = "trick")
      .expectWorker(honestWorker, key = "honest", description = "honest")
      .render()

    val trickWorkflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(trickWorker) { action("") { setOutput(it) } }
    }
    trickWorkflow.testRender(Unit)
      .expectWorker(honestWorker)
      .apply {
        val error = assertFailsWith<AssertionError> { render() }
        assertEquals(
          "Expected actual worker's doesSameWorkAs to return true for expected worker \n" +
            "  expected=ParentWorker\n" +
            "  actual=ChildWorker",
          error.message
        )
      }

    val honestWorkflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(honestWorker) { action("") { setOutput(it) } }
    }
    honestWorkflow.testRender(Unit)
      .expectWorker(trickWorker)
      .apply {
        val error = assertFailsWith<AssertionError> { render() }
        assertEquals(
          "Expected actual worker's doesSameWorkAs to return true for expected worker \n" +
            "  expected=ChildWorker\n" +
            "  actual=ParentWorker",
          error.message
        )
      }
  }

  @Test fun `expectWorker on TypedWorker`() {
    val trigger = emptyFlow<String>()
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(trigger.asWorker()) { WorkflowAction.noAction() }
    }

    workflow.testRender(Unit)
      .expectWorker(workerType = typeOf<Worker<String>>())
      .render()
  }
}
