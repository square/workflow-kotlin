package com.squareup.workflow1.testing

import com.squareup.workflow1.ImpostorWorkflow
import com.squareup.workflow1.LifecycleWorker
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.StatelessWorkflow.RenderContext
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.contraMap
import com.squareup.workflow1.identifier
import com.squareup.workflow1.remember
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.rendering
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.sessionWorkflow
import com.squareup.workflow1.stateful
import com.squareup.workflow1.stateless
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch.Matched
import com.squareup.workflow1.unsnapshottableIdentifier
import com.squareup.workflow1.workflowIdentifier
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.mock
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

internal class RealRenderTesterTest {

  private interface OutputWhateverChild : Workflow<Unit, Unit, Unit>
  private interface OutputNothingChild : Workflow<Unit, Nothing, Unit>

  private val expectedOneOrMore =
    "Expected 1 more workflows, workers, side effects, or remembers to be run:"

  @Test fun `renderChild throws when already expecting workflow output`() {
    val child1 = Workflow.stateless<Unit, Unit, Unit> {}
    val child2 = Workflow.stateless<Unit, Unit, Unit> {}
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      renderChild(child1) { noAction() }
      renderChild(child2) { noAction() }
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(child1.identifier, rendering = Unit, output = WorkflowOutput(Unit))
      .expectWorkflow(child2.identifier, rendering = Unit, output = WorkflowOutput(Unit))

    val failure = assertFailsWith<IllegalStateException> {
      tester.render()
    }
    assertEquals(
      "Expected only one output to be expected: child ${child2.identifier} " +
        "expected to emit kotlin.Unit but noAction() was already processed.",
      failure.message
    )
  }

  @Test fun `runningWorker throws when already expecting workflow output`() {
    val child = Workflow.stateless<Unit, Unit, Unit> {}
    val worker = Worker.finished<Unit>()
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      renderChild(child) { noAction() }
      runningWorker(worker) { noAction() }
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(child.identifier, rendering = Unit, output = WorkflowOutput(Unit))
      .expectWorker(typeOf<Worker<Unit>>(), output = WorkflowOutput(Unit))

    val failure = assertFailsWith<IllegalStateException> {
      tester.render()
    }

    assertEquals(
      "Expected only one output to be expected: " +
        "child ${typeOf<Worker<Unit>>()} expected to emit " +
        "kotlin.Unit but noAction() was already processed.",
      failure.message
    )
  }

  @Test fun `expectWorkflow without output doesn't throw when already expecting output`() {
    // Don't need an implementation, the test should fail before even calling render.
    val workflow = Workflow.stateless<Unit, Unit, Unit> {}
    val tester = workflow.testRender(Unit)
      .expectWorkflow(
        OutputWhateverChild::class,
        rendering = Unit,
        output = WorkflowOutput(Unit)
      )

    // Doesn't throw.
    tester.expectWorkflow(workflow::class, rendering = Unit)
  }

  @Test fun `expectSideEffect throws when already expecting side effect with same key`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("the key") {}
    }
    val tester = workflow.testRender(Unit)
      .requireExplicitSideEffectExpectations()
      .expectSideEffect(key = "the key")
      .expectSideEffect(description = "duplicate match") { it == "the key" }

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Multiple expectations matched side effect with key \"the key\":\n" +
        "  side effect with key \"the key\"\n" +
        "  duplicate match",
      error.message
    )
  }

  @Test fun `expectSideEffect matches on key`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("the key") {}
    }

    workflow.testRender(Unit)
      .expectSideEffect("the key")
      .render {}
  }

  @Test fun `expectSideEffect doesn't match key`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
    val tester = workflow.testRender(Unit)
      .expectSideEffect("the key")

    val error = assertFailsWith<AssertionError> {
      tester.render {}
    }
    assertEquals(
      """
          $expectedOneOrMore
            side effect with key "the key"
      """.trimIndent(),
      error.message
    )
  }

  @Test fun `remember runs and returns calculations`() {
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      val numOutput = remember("the key") { 36 }
      val stringInputs = remember("the key", "the", "inputs") { "string with string inputs" }
      val noInputs = remember("the key", 1, 2, 3) { "string with number inputs" }
      "$numOutput-$stringInputs-$noInputs"
    }
    workflow.testRender(Unit).render {
      assertEquals("36-string with string inputs-string with number inputs", it)
    }
  }

  @Test fun `expectRemember throws when already expecting remember with same key`() {
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      remember("the key", "the", "inputs") { "theOutput" }
    }
    val tester = workflow.testRender(Unit)
      .expectRemember("the key", typeOf<String>(), "the", "inputs")
      .expectRemember("the key", typeOf<String>(), "the", "inputs", description = "duplicate match")

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Multiple expectations matched remember with key \"the key\": \n" +
        "  remember key=the key, inputs=[the, inputs], resultType=kotlin.String\n" +
        "  duplicate match",
      error.message
    )
  }

  @Test fun `expectRemember matches on key input and result type`() {
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      val numOutput = remember("the key") { 36 }
      val stringInputs = remember("the key", "the", "inputs") { "string with string inputs" }
      val noInputs = remember("the key", 1, 2, 3) { "string with number inputs" }
      "$numOutput-$stringInputs-$noInputs"
    }

    workflow.testRender(Unit)
      .expectRemember("the key", typeOf<Int>())
      .expectRemember("the key", typeOf<String>(), "the", "inputs")
      .expectRemember("the key", typeOf<String>(), 1, 2, 3)
      .render()
  }

  @Test fun `expectRemember doesn't match key`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
    val tester = workflow.testRender(Unit)
      .expectRemember("the key", typeOf<String>(), "the", "inputs")

    val error = assertFailsWith<AssertionError> {
      tester.render {}
    }
    assertEquals(
      """
          $expectedOneOrMore
            remember key=the key, inputs=[the, inputs], resultType=kotlin.String
      """.trimIndent(),
      error.message
    )
  }

  @Test fun `expectRemember is strict`() {
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      remember("the key", "the", "inputs") { "theOutput" }
    }
    val tester = workflow.testRender(Unit)
      .requireExplicitRememberExpectations()

    val error = assertFailsWith<AssertionError> {
      tester.render {}
    }
    assertEquals("Unexpected remember with key \"the key\"", error.message)
  }

  @Test fun `assertInputs failures fail test`() {
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      remember("the key", "the", "inputs") { "theOutput" }
    }
    val tester = workflow.testRender(Unit)
      .expectRemember(
        key = "the key",
        resultType = typeOf<String>(),
        "the",
        "inputs",
        assertInputs = { inputs ->
          throw AssertionError("Actually they were just fine! $inputs")
        }
      )

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals("Actually they were just fine! [the, inputs]", error.message)
  }

  @Test fun `sending to sink throws when called multiple times`() {
    class TestAction(name: String) : WorkflowAction<Unit, Unit, Nothing>() {
      override fun Updater.apply() {}
      override val debuggingName: String = "TestAction($name)"
    }

    val workflow = Workflow.stateful<Unit, Nothing, Sink<TestAction>>(
      initialState = Unit,
      render = { actionSink.contraMap { it } }
    )
    val action1 = TestAction("1")
    val action2 = TestAction("2")

    workflow.testRender(Unit)
      .render { sink ->
        sink.send(action1)

        val error = assertFailsWith<IllegalStateException> {
          sink.send(action2)
        }
        assertEquals(
          "Tried to send action to sink after another action was already processed:\n" +
            "  processed action=${action1.debuggingName}\n" +
            "  attempted action=${action2.debuggingName}",
          error.message
        )
      }
  }

  @Test fun `sending to sink throws when child output expected`() {
    class TestAction : WorkflowAction<Unit, Unit, Nothing>() {
      override fun Updater.apply() {}
      override val debuggingName: String = "TestAction"
    }

    val workflow = Workflow.stateful<Unit, Nothing, Sink<TestAction>>(
      initialState = Unit,
      render = {
        // Need to satisfy the expectation.
        runningWorker(Worker.finished<Unit>()) { noAction() }
        return@stateful actionSink.contraMap { it }
      }
    )

    workflow.testRender(Unit)
      .expectWorker(typeOf<Worker<Unit>>(), output = WorkflowOutput(Unit))
      .render { sink ->
        val error = assertFailsWith<IllegalStateException> {
          sink.send(TestAction())
        }
        assertEquals(
          "Tried to send action to sink after another action was already processed:\n" +
            "  processed action=noAction()\n" +
            "  attempted action=TestAction",
          error.message
        )
      }
  }

  @Test fun `failures from render block escape`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> { }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render {
        throw AssertionError("expected failure")
      }
    }
    assertEquals("expected failure", error.message)
  }

  @Test fun `renderChild throws when none expected`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Tried to render unexpected child ${child.identifier}",
      error.message
    )
  }

  @Test fun `multiple matching inexact workflow matches are allowed`() {
    val child = Workflow.stateless<Unit, Nothing, String> { fail() }
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      renderChild(child)
    }

    workflow.testRender(Unit)
      .expectWorkflow(exactMatch = false, description = "expect1") { Matched("one") }
      .expectWorkflow(exactMatch = false, description = "expect2") { Matched("two") }
      .render { rendering ->
        assertEquals("one", rendering)
      }
  }

  @Test fun `matching inexact workflow matches are allowed with matching exact match`() {
    val child = Workflow.stateless<Unit, Nothing, String> { fail() }
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      renderChild(child)
    }

    workflow.testRender(Unit)
      .expectWorkflow(exactMatch = false, description = "expect1") { Matched("one") }
      .expectWorkflow(exactMatch = true, description = "expect2") { Matched("two") }
      .render { rendering ->
        assertEquals("two", rendering)
      }
  }

  @Test fun `unmatching inexact workflow matches are allowed`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}

    workflow.testRender(Unit)
      .expectWorkflow(exactMatch = false, description = "expect1") { Matched(Unit) }
      .expectWorkflow(exactMatch = false, description = "expect2") { Matched(Unit) }
      .render()
  }

  @Test fun `multiple matching inexact side effect matches are allowed`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("side effect") {}
    }

    workflow.testRender(Unit)
      .expectSideEffect(exactMatch = false, description = "expect1") { true }
      .expectSideEffect(exactMatch = false, description = "expect2") { true }
      .render()
  }

  @Test fun `matching inexact side effect matches are allowed with matching exact match`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("side effect") {}
    }

    workflow.testRender(Unit)
      .expectSideEffect(exactMatch = false, description = "expect1") { true }
      .expectSideEffect(exactMatch = true, description = "expect2") { true }
      .render()
  }

  @Test fun `non-matching inexact side effect is ignored`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}

    workflow.testRender(Unit)
      .expectSideEffect(exactMatch = false, description = "expect1") { false }
      .render()
  }

  @Test fun `distinct side effect expectations match individual side effects`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("effect1") {}
      runningSideEffect("effect2") {}
    }

    workflow.testRender(Unit)
      .expectSideEffect(exactMatch = true, description = "expect2") { it == "effect2" }
      .expectSideEffect(exactMatch = true, description = "expect1") { it == "effect1" }
      .render()
  }

  @Test fun `non-matched inexact side effect matches are allowed`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}

    workflow.testRender(Unit)
      .expectSideEffect(exactMatch = false, description = "expect1") { true }
      .expectSideEffect(exactMatch = false, description = "expect2") { true }
      .render()
  }

  @Test fun `runningSideEffect throws when no expectations match`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("effect") {}
    }
    val tester = workflow.testRender(Unit).requireExplicitSideEffectExpectations()

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals("Tried to run unexpected side effect with key \"effect\"", error.message)
  }

  @Test fun `runningSideEffect throws when no expectations match but other side effects matched`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("expected") {}
      runningSideEffect("unexpected") {}
    }
    val tester = workflow.testRender(Unit)
      .requireExplicitSideEffectExpectations()
      .expectSideEffect("expected")

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals("Tried to run unexpected side effect with key \"unexpected\"", error.message)
  }

  @Test fun `runningSideEffect throws when multiple expectations match`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("effect") {}
    }
    val tester = workflow.testRender(Unit)
      .requireExplicitSideEffectExpectations()
      .expectSideEffect("effect")
      .expectSideEffect(description = "custom", exactMatch = true) { key -> "effect" in key }

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Multiple expectations matched side effect with key \"effect\":\n" +
        "  side effect with key \"effect\"\n" +
        "  custom",
      error.message
    )
  }

  @Test fun `runningSideEffect throws on duplicate call`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("key") {}
      runningSideEffect("key") {}
    }

    val tester = workflow.testRender(Unit)
      .expectSideEffect("key")

    val error = assertFailsWith<IllegalArgumentException> {
      tester.render()
    }
    assertEquals("Expected side effect keys to be unique: \"key\"", error.message)
  }

  @Suppress("ktlint:standard:max-line-length")
  @Test
  fun `renderChild rendering non-Unit throws when none expected and unexpected children are allowed`() {
    val child = Workflow.stateless<Unit, Nothing, Int> { 42 }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Tried to render unexpected child ${child.identifier}",
      error.message
    )
  }

  @Test
  fun `renderChild rendering Unit throws when no expectations match`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Tried to render unexpected child ${child.identifier}",
      error.message
    )
  }

  @Test
  fun `renderChild rendering non-Unit throws when no expectations match`() {
    val child = Workflow.stateless<Unit, Nothing, Int> { 42 }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Tried to render unexpected child ${child.identifier}",
      error.message
    )
  }

  @Test fun `renderChild with key throws when none expected`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child, key = "key")
    }
    val tester = workflow.testRender(Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Tried to render unexpected child ${child.identifier} with key \"key\"",
      error.message
    )
  }

  @Test fun `renderChild with key throws when no expectations match`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child, key = "key")
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Tried to render unexpected child ${child.identifier} with key \"key\"",
      error.message
    )
  }

  @Test
  fun `renderChild with key throws when key doesn't match`() {
    val child = Workflow.stateless<Unit, Nothing, Unit> { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child, key = "key")
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(OutputNothingChild::class, rendering = Unit, key = "wrong key")

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Tried to render unexpected child ${child.identifier} with key \"key\"",
      error.message
    )
  }

  @Test fun `renderChild throws when multiple expectations match`() {
    class Child : OutputNothingChild, StatelessWorkflow<Unit, Nothing, Unit>() {
      override fun render(
        renderProps: Unit,
        context: RenderContext
      ) {
        // Nothing to do.
      }
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(Child())
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(OutputNothingChild::class, rendering = Unit)
      .expectWorkflow(Child::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      """
          Multiple expectations matched child ${Child::class.workflowIdentifier}:
            workflow identifier=${OutputNothingChild::class.workflowIdentifier}, key=, rendering=kotlin.Unit, output=null
            workflow identifier=${Child::class.workflowIdentifier}, key=, rendering=kotlin.Unit, output=null
      """.trimIndent(),
      error.message
    )
  }

  @Test fun `renderChild throws on duplicate call`() {
    val child = Workflow.rendering(Unit)
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
      renderChild(child)
    }

    val tester = workflow.testRender(Unit)
      .expectWorkflow(child.identifier, Unit)

    val error = assertFailsWith<IllegalArgumentException> {
      tester.render()
    }
    assertEquals("Expected keys to be unique for ${child.identifier}: key=\"\"", error.message)
  }

  @Test fun `runningWorker doesn't throw when none expected`() {
    val worker = object : Worker<Unit> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Unit> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker) {
        noAction()
      }
    }
    val tester = workflow.testRender(Unit)
    tester.render()
  }

  @Test fun `runningSideEffect doesn't throw when none expected`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect(key = "foo") { }
    }
    val tester = workflow.testRender(Unit)
    tester.render()
  }

  @Suppress("ktlint:standard:max-line-length")
  @Test
  fun `runningSideEffect does throw when none expected and require explicit side effect is set`() {
    val key = "foo"
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect(key = key) { }
    }
    val tester = workflow.testRender(Unit).requireExplicitSideEffectExpectations()
    val error = assertFailsWith<AssertionError> {
      tester.render()
    }

    assertEquals(
      "Tried to run unexpected side effect with key \"$key\"",
      error.message
    )
  }

  @Test fun `runningWorker does throw when none expected and require explicit workers is set`() {
    class MySpecialWorker : Worker<Unit> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Unit> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val worker = MySpecialWorker()

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker) {
        noAction()
      }
    }
    val tester = workflow.testRender(Unit).requireExplicitWorkerExpectations()
    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      "Tried to render unexpected child ${typeOf<MySpecialWorker>()}",
      error.message
    )
  }

  @Test fun `render throws when worker expectation doesn't match`() {
    val worker = object : Worker<String> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Nothing> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker) { fail() }
    }
    val tester = workflow.testRender(Unit)
      .expectWorker(typeOf<Worker<Unit>>())

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }

    assertTrue(
      error.message!!.startsWith(expectedOneOrMore)
    )
  }

  @Test fun `runningWorker with key does not throw when none expected`() {
    val worker = object : Worker<Unit> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Unit> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker, key = "key") {
        noAction()
      }
    }
    val tester = workflow.testRender(Unit)

    tester.render()
  }

  @Test fun `runningWorker with key throws when no key expected`() {
    val worker = object : Worker<Unit> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Unit> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker, key = "key") {
        noAction()
      }
    }
    val tester = workflow.testRender(Unit)
      .expectWorker(typeOf<Worker<Unit>>())

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
      error.message!!.startsWith(expectedOneOrMore)
    )
  }

  @Test fun `runningWorker with key throws when wrong key expected`() {
    val worker = object : Worker<Unit> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Unit> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker, key = "key") {
        noAction()
      }
    }
    val tester = workflow.testRender(Unit)
      .expectWorker(
        typeOf<Worker<Unit>>(),
        key = "wrong key"
      )

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
      error.message!!.startsWith(expectedOneOrMore)
    )
  }

  @Test fun `runningWorker throws when multiple expectations match`() {
    class EmptyWorker : Worker<Unit> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = true
      override fun run(): Flow<Unit> = emptyFlow()
      override fun toString(): String = "TestWorker"
    }

    val worker = EmptyWorker()
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker) {
        noAction()
      }
    }
    val tester = workflow.testRender(Unit)
      .expectWorker(worker)
      .expectWorker(worker, description = "duplicate expectation")

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals(
      """
          Multiple expectations matched child ${typeOf<EmptyWorker>()}:
            worker TestWorker
            duplicate expectation
      """.trimIndent(),
      error.message
    )
  }

  @Test fun `runningWorker can distinguish LifecycleWorker from Flow asWorker`() {
    val lifecycleWorker = object : LifecycleWorker() {}
    val stringWorker: Worker<String> = emptyFlow<String>().asWorker()

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(lifecycleWorker)
      runningWorker(stringWorker) { noAction() }
    }
    workflow.testRender(Unit)
      .expectWorker(lifecycleWorker)
      .expectWorker(stringWorker)
      .render()

    // No exception, no bug.
  }

  // Suppress runningWorker in this test as we are testing the
  // uniqueness of workers using similar objects as keys
  @Test
  fun `runningWorker distinguishes between specific Nothing workers`() {
    val workerA = object : LifecycleWorker() {}
    val workerB = object : LifecycleWorker() {}

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(workerA)
      runningWorker(workerB)
    }
    workflow.testRender(Unit)
      .expectWorker(workerA)
      .expectWorker(workerB)
      .render()

    // No exception, no bug.
  }

  @Test fun `runningWorker throws on duplicate call`() {
    val worker = Worker.from { }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker) {
        action("") { }
      }
      runningWorker(worker) {
        action("") { }
      }
    }

    val tester = workflow.testRender(Unit)
    val error = assertFailsWith<IllegalArgumentException> {
      tester.render()
    }
    assertEquals(
      "Expected keys to be unique for " +
        "com.squareup.workflow1.Worker<kotlin.Unit>: key=\"\"",
      error.message
    )
  }

  @Test fun `render throws when unconsumed workflow`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      // Do nothing.
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
      error.message!!.startsWith(expectedOneOrMore)
    )
  }

  @Test fun `render throws when unconsumed side effect`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      // Do nothing.
    }
    val tester = workflow.testRender(Unit)
      .expectSideEffect("expectation", exactMatch = true) { true }

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(error.message!!.startsWith(expectedOneOrMore))
  }

  @Test fun `render throws when unconsumed worker`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      // Do nothing.
    }
    val tester = workflow.testRender(Unit)
      .expectWorker(typeOf<Worker<Unit>>())

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertTrue(
      error.message!!.startsWith(expectedOneOrMore)
    )
  }

  @Test fun `expectWorkflow matches on workflow supertype`() {
    val child = object : OutputNothingChild, StatelessWorkflow<Unit, Nothing, Unit>() {
      override fun render(
        renderProps: Unit,
        context: RenderContext
      ) {
        // Do nothing.
      }
    }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child)
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(OutputNothingChild::class, rendering = Unit)

    tester.render()
  }

  @Test fun `expectWorkflow matches same ImpostorWorkflow class with same proxy identifiers`() {
    class TestWorkflow : Workflow<Unit, Nothing, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestImpostor(val proxy: Workflow<*, *, *>) :
      Workflow<Unit, Nothing, Unit>,
      ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier get() = proxy.identifier
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(TestImpostor(TestWorkflow()))
    }
    workflow.testRender(Unit)
      .expectWorkflow(
        TestImpostor(TestWorkflow()).identifier,
        Unit
      )
      .render {}
  }

  @Suppress("ktlint:standard:max-line-length")
  @Test
  fun `expectWorkflow doesn't match same ImpostorWorkflow class with different proxy identifiers`() {
    class TestWorkflowActual : Workflow<Unit, Nothing, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestWorkflowExpected : Workflow<Unit, Nothing, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestImpostor(val proxy: Workflow<*, *, *>) :
      Workflow<Unit, Nothing, Unit>,
      ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier get() = proxy.identifier
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(TestImpostor(TestWorkflowActual()))
    }
    val expectedId = TestImpostor(TestWorkflowExpected()).identifier
    val actualId = TestImpostor(TestWorkflowActual()).identifier

    val tester = workflow.testRender(Unit)
      .expectWorkflow(expectedId, Unit)

    val error = assertFailsWith<AssertionError> {
      tester.render {}
    }
    assertEquals(
      "Tried to render unexpected child $actualId",
      error.message
    )
  }

  @Test
  fun `expectWorkflow matches different ImpostorWorkflow classes with same proxy identifiers`() {
    class TestWorkflow : Workflow<Unit, Nothing, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestImpostorActual(val proxy: Workflow<*, *, *>) :
      Workflow<Unit, Nothing, Unit>,
      ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier get() = proxy.identifier
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    class TestImpostorExpected(val proxy: Workflow<*, *, *>) :
      Workflow<Unit, Nothing, Unit>,
      ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier get() = proxy.identifier
      override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> =
        throw NotImplementedError()
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(TestImpostorActual(TestWorkflow()))
    }
    val expectedId = TestImpostorExpected(TestWorkflow()).identifier

    workflow.testRender(Unit)
      .expectWorkflow(expectedId, Unit)
      .render {}
  }

  @Test fun `assertProps failure fails test`() {
    val child = Workflow.stateless<String, Nothing, Unit> {}
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(child, "wrong props")
    }
    val tester = workflow.testRender(Unit)
      .expectWorkflow(
        workflowType = child::class,
        rendering = Unit,
        assertProps = { props -> throw AssertionError("bad props: $props") }
      )

    val error = assertFailsWith<AssertionError> {
      tester.render()
    }
    assertEquals("bad props: wrong props", error.message)
  }

  private class TestAction(name: String) : WorkflowAction<Unit, Nothing, Nothing>() {
    override fun Updater.apply() {}
    override val debuggingName: String = name
  }

  @Test fun `verifyAction failure fails test`() {
    val workflow = Workflow.stateless<Unit, Nothing, Sink<TestAction>> {
      actionSink.contraMap { it }
    }
    val testResult = workflow.testRender(Unit)
      .render { it.send(TestAction("noop")) }

    val error = assertFailsWith<AssertionError> {
      testResult.verifyAction { throw AssertionError("action failed") }
    }
    assertEquals("action failed", error.message)
  }

  @Test fun `verifyAction verifies workflow output`() {
    val child = Workflow.stateless<Unit, String, Unit> {}
    val workflow = Workflow.stateless {
      renderChild(child) { TestAction(it) }
    }
    val testResult = workflow.testRender(Unit)
      .expectWorkflow(
        workflowType = child::class,
        rendering = Unit,
        output = WorkflowOutput("output")
      )
      .render()

    testResult.verifyAction {
      assertTrue(it is TestAction)
      assertEquals("output", it.debuggingName)
    }
  }

  @Test fun `verifyAction verifies worker output`() {
    val worker = Worker.finished<String>()
    val workflow = Workflow.stateless {
      runningWorker(worker) { TestAction(it) }
    }
    val testResult = workflow.testRender(Unit)
      .expectWorker(worker, output = WorkflowOutput("output"))
      .render()

    testResult.verifyAction {
      assertTrue(it is TestAction)
      assertEquals("output", it.debuggingName)
    }
  }

  @Test fun `verifyAction verifies sink send`() {
    val workflow = Workflow.stateless<Unit, Nothing, Sink<TestAction>> {
      actionSink.contraMap { it }
    }
    val testResult = workflow.testRender(Unit)
      .render { sink ->
        sink.send(TestAction("event"))
      }

    testResult.verifyAction {
      assertTrue(it is TestAction)
      assertEquals("event", it.debuggingName)
    }
  }

  @Test fun `verifyAction allows no action`() {
    val workflow = Workflow.stateless<Unit, Nothing, Sink<TestAction>> {
      actionSink.contraMap { it }
    }
    val testResult = workflow.testRender(Unit)
      .render {
        // Don't send to sink!
      }

    testResult.verifyAction { assertEquals(noAction(), it) }
    testResult.verifyActionResult { newState, output ->
      assertSame(Unit, newState)
      assertNull(output?.value)
    }
  }

  @Test fun `verifyActionResult allows no action`() {
    val workflow = Workflow.stateless<Unit, Nothing, Sink<TestAction>> {
      actionSink.contraMap { it }
    }
    val testResult = workflow.testRender(Unit)
      .render {
        // Don't send to sink!
      }

    testResult.verifyActionResult { newState, output ->
      assertSame(Unit, newState)
      assertNull(output?.value)
    }
  }

  @Test fun `verifyActionResult handles new state and output`() {
    class TestAction : WorkflowAction<Unit, String, String>() {
      override fun Updater.apply() {
        state = "new state"
        setOutput("output")
      }
    }

    val workflow = Workflow.stateful<Unit, String, String, Sink<TestAction>>(
      initialState = { "initial" },
      render = { _, _ -> actionSink.contraMap { it } }
    )
    val testResult = workflow.testRender(Unit)
      .render { sink ->
        sink.send(TestAction())
      }

    testResult.verifyActionResult { state, output ->
      assertEquals("new state", state)
      assertEquals("output", output?.value)
    }
  }

  @Test fun `testNextRender could daisy-chain consecutive renderings with verifyAction`() {
    data class TestAction(val add: Int) : WorkflowAction<Unit, Int, Int>() {
      override val debuggingName: String get() = "add:$add"
      override fun Updater.apply() {
        setOutput(state)
        state += add
      }
    }

    val workflow = Workflow.stateful<Unit, Int, Int, Sink<TestAction>>(
      initialState = { 0 },
      render = { _, _ -> actionSink.contraMap { it } }
    )

    workflow.testRender(Unit, 0)
      .render { sink ->
        sink.send(TestAction(1))
      }
      .verifyAction { action ->
        assertEquals(TestAction(1), action)
      }
      .testNextRender()
      .render { sink ->
        sink.send(TestAction(2))
      }
      .verifyAction { action ->
        assertEquals(TestAction(2), action)
      }
      .testNextRender()
      .render { sink ->
        sink.send(TestAction(3))
      }
      .verifyAction { action ->
        assertEquals(TestAction(3), action)
      }
  }

  @Test fun `testNextRender could daisy-chain consecutive renderings with verifyActionResult`() {
    data class TestAction(val add: Int) : WorkflowAction<Unit, Int, Int>() {
      override val debuggingName: String get() = "add:$add"

      override fun Updater.apply() {
        setOutput(state)
        state += add
      }
    }

    val workflow = Workflow.stateful<Unit, Int, Int, Sink<TestAction>>(
      initialState = { 0 },
      render = { _, _ -> actionSink.contraMap { it } }
    )

    workflow.testRender(Unit, 0)
      .render { sink ->
        sink.send(TestAction(1))
      }
      .verifyActionResult { state, output ->
        assertEquals(1, state)
        assertEquals(0, output?.value)
      }
      .testNextRender()
      .render { sink ->
        sink.send(TestAction(2))
      }
      .verifyActionResult { state, output ->
        assertEquals(3, state)
        assertEquals(1, output?.value)
      }
      .testNextRender()
      .render { sink ->
        sink.send(TestAction(3))
      }
      .verifyActionResult { state, output ->
        assertEquals(6, state)
        assertEquals(3, output?.value)
      }
  }

  @Test fun `testNextRenderWithProps respects new props`() {
    data class TestAction(val add: Int) : WorkflowAction<Int, Int, Int>() {
      override val debuggingName: String get() = "add:$add"

      override fun Updater.apply() {
        setOutput(state)
        state += props * add
      }
    }

    val workflow = Workflow.stateful<Int, Int, Int, Sink<TestAction>>(
      initialState = { 0 },
      render = { _, _ -> actionSink.contraMap { it } }
    )

    workflow.testRender(1, 0)
      .render { sink ->
        sink.send(TestAction(1))
      }
      .verifyActionResult { state, output ->
        assertEquals(1, state)
        assertEquals(0, output?.value)
      }
      .testNextRenderWithProps(2)
      .render { sink ->
        sink.send(TestAction(2))
      }
      .verifyActionResult { state, output ->
        assertEquals(5, state)
        assertEquals(1, output?.value)
      }
      .testNextRenderWithProps(3)
      .render { sink ->
        sink.send(TestAction(3))
      }
      .verifyActionResult { state, output ->
        assertEquals(14, state)
        assertEquals(5, output?.value)
      }
  }

  @Test fun `testNextRenderWithProps uses onPropsChanged`() {
    data class TestAction(val add: Int) : WorkflowAction<Int, Int, Int>() {
      override val debuggingName: String get() = "add:$add"

      override fun Updater.apply() {
        setOutput(state)
        state += props * add
      }
    }

    val workflow = Workflow.stateful<Int, Int, Int, Sink<TestAction>>(
      initialState = { 0 },
      render = { _, _ -> actionSink.contraMap { it } },
      onPropsChanged = { _, _, _ -> 0 }
    )

    workflow.testRender(1, 0)
      .render { sink ->
        sink.send(TestAction(1))
      }
      .verifyActionResult { state, output ->
        assertEquals(1, state)
        assertEquals(0, output?.value)
      }
      .testNextRenderWithProps(2)
      .render { sink ->
        sink.send(TestAction(2))
      }
      .verifyActionResult { state, output ->
        assertEquals(4, state)
        assertEquals(0, output?.value)
      }
      .testNextRenderWithProps(3)
      .render { sink ->
        sink.send(TestAction(3))
      }
      .verifyActionResult { state, output ->
        assertEquals(9, state)
        assertEquals(0, output?.value)
      }
  }

  @Test fun `testNextRender and verifyActionResult call action handler only once`() {
    val worker = Worker.from { }
    var actionCount = 0
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker) {
        action("") { actionCount++ }
      }
    }

    workflow.testRender(Unit)
      .expectWorker(typeOf<Worker<Unit>>(), output = WorkflowOutput(Unit))
      .render()
      .verifyActionResult { _, _ -> }
      .verifyActionResult { _, _ -> }
      .testNextRender()

    assertEquals(1, actionCount)
  }

  @Test fun `render is executed multiple times`() {
    var renderCount = 0
    val workflow = Workflow.stateless<Unit, Nothing, Unit> { renderCount++ }

    workflow.testRender(Unit)
      .render()

    assertEquals(2, renderCount)
  }

  @Test fun `enforces frozen failures on late renderChild call`() {
    lateinit var capturedContext: StatelessWorkflow<Unit, Nothing, Unit>.RenderContext
    val workflow = Workflow.stateless { capturedContext = this }

    workflow.testRender(Unit)
      .render()

    assertFailsWith<IllegalStateException> {
      capturedContext.renderChild(workflow)
    }
  }

  @Test fun `enforces frozen failures on late runningSideEffect call`() {
    lateinit var capturedContext: StatelessWorkflow<Unit, Nothing, Unit>.RenderContext
    val workflow = Workflow.stateless { capturedContext = this }

    workflow.testRender(Unit)
      .render()

    assertFailsWith<IllegalStateException> {
      capturedContext.runningSideEffect(key = "fnord") {}
    }
  }

  @Test fun `enforces frozen failures on late remember call`() {
    lateinit var capturedContext: StatelessWorkflow<Unit, Nothing, Unit>.RenderContext
    val workflow = Workflow.stateless { capturedContext = this }

    workflow.testRender(Unit)
      .render()

    assertFailsWith<IllegalStateException> {
      capturedContext.remember(key = "fnord") {}
    }
  }

  @Test fun `enforces failures on send while rendering`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      actionSink.send(action("fnord") {})
    }

    assertFailsWith<UnsupportedOperationException> {
      workflow.testRender(Unit).render()
    }
  }

  @OptIn(WorkflowExperimentalApi::class)
  @Test
  fun `testRender with SessionWorkflow throws exception`() {
    class TestAction : WorkflowAction<Unit, String, String>() {
      override fun Updater.apply() {
        state = "new state"
        setOutput("output")
      }
    }

    val workflow = Workflow.sessionWorkflow<Unit, String, String, Sink<TestAction>>(
      initialState = { _, _: CoroutineScope -> "initial" },
      render = { _, _ ->
        actionSink.contraMap { it }
      }
    )

    val exception = assertFailsWith<IllegalArgumentException> {
      workflow.testRender(Unit)
        .render { sink ->
          sink.send(TestAction())
        }
    }

    assertEquals(
      exception.message,
      "Called testRender on a SessionWorkflow without a CoroutineScope. Use the version that passes a CoroutineScope."
    )
  }

  @OptIn(WorkflowExperimentalApi::class)
  @Test
  fun `testRender with CoroutineScope works for SessionWorkflow`() = runTest {
    class TestAction : WorkflowAction<Unit, String, String>() {
      override fun Updater.apply() {
        state = "new state"
        setOutput("output")
      }
    }

    val workflow = Workflow.sessionWorkflow<Unit, String, String, Sink<TestAction>>(
      initialState = { _, _: CoroutineScope -> "initial" },
      render = { _, _ ->
        actionSink.contraMap { it }
      }
    )

    val testResult = workflow.testRender(Unit, this)
      .render { sink ->
        sink.send(TestAction())
      }

    testResult.verifyActionResult { state, output ->
      assertEquals("new state", state)
      assertEquals("output", output?.value)
    }
  }

  @OptIn(WorkflowExperimentalApi::class)
  @Test
  fun `testRender with CoroutineScope uses the correct scope`() = runTest {
    val signalMutex = Mutex(locked = true)

    class TestAction : WorkflowAction<Unit, String, String>() {
      override fun Updater.apply() {
        state = "new state"
        setOutput("output")
      }
    }

    val workflow = Workflow.sessionWorkflow<Unit, String, String, Sink<TestAction>>(
      initialState = { _, workflowScope: CoroutineScope ->
        assertEquals(workflowScope, this@runTest)
        signalMutex.unlock()
        "initial"
      },
      render = { _, _ ->
        actionSink.contraMap { it }
      }
    )

    workflow.testRender(Unit, this)
      .render { sink ->
        sink.send(TestAction())
      }

    // Assertion happens in the `initialState` call above.
    signalMutex.lock()
  }

  @Test fun `createRenderChildInvocation() for Workflow-stateless{}`() {
    val workflow = Workflow.stateless<String, Int, Unit> {}
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    // Broken due to https://youtrack.jetbrains.com/issue/KT-17103
    // assertEquals(typeOf<Int>(), invocation.outputType.type)
    // assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for Workflow-stateful{}`() {
    val workflow = Workflow.stateful<String, Int, Unit>(
      initialState = "",
      render = {}
    )
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    // Broken due to https://youtrack.jetbrains.com/issue/KT-17103
    // assertEquals(typeOf<Int>(), invocation.outputType.type)
    // assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for anonymous skeleton Workflow`() {
    val workflow = object : Workflow<String, Int, Unit> {
      override fun asStatefulWorkflow(): StatefulWorkflow<String, *, Int, Unit> =
        throw NotImplementedError()
    }
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for anonymous StatefulWorkflow`() {
    val workflow = object : StatefulWorkflow<String, Double, Int, Unit>() {
      override fun initialState(
        props: String,
        snapshot: Snapshot?
      ): Double = throw NotImplementedError()

      override fun render(
        renderProps: String,
        renderState: Double,
        context: RenderContext
      ) = throw NotImplementedError()

      override fun snapshotState(state: Double): Snapshot = throw NotImplementedError()
    }
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for anonymous StatelessWorkflow`() {
    val workflow = object : StatelessWorkflow<String, Int, Unit>() {
      override fun render(
        renderProps: String,
        context: RenderContext
      ) = throw NotImplementedError()
    }
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for non-anonymous StatefulWorkflow`() {
    class TestWorkflow : StatefulWorkflow<String, Double, Int, Unit>() {
      override fun initialState(
        props: String,
        snapshot: Snapshot?
      ): Double = throw NotImplementedError()

      override fun render(
        renderProps: String,
        renderState: Double,
        context: RenderContext
      ) = throw NotImplementedError()

      override fun snapshotState(state: Double): Snapshot = throw NotImplementedError()
    }

    val workflow = TestWorkflow()
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `createRenderChildInvocation() for non-anonymous StatelessWorkflow`() {
    class TestWorkflow : StatelessWorkflow<String, Int, Unit>() {
      override fun render(
        renderProps: String,
        context: RenderContext
      ) = throw NotImplementedError()
    }

    val workflow = TestWorkflow()
    val invocation = createRenderChildInvocation(workflow, "props", "key")

    assertSame(workflow, invocation.workflow)
    assertEquals("props", invocation.props)
    assertEquals(typeOf<Int>(), invocation.outputType.type)
    assertEquals(typeOf<Unit>(), invocation.renderingType.type)
    assertEquals("key", invocation.renderKey)
  }

  @Test fun `workflow rendered after worker matches workflow expectation`() {
    class ChildWorkflow : StatelessWorkflow<Unit, Nothing, Int>() {
      override fun render(
        renderProps: Unit,
        context: RenderContext
      ): Int = fail()
    }

    val childWorker = Worker.finished<Unit>()
    val workflow = Workflow.stateless<Unit, Nothing, Int> {
      runningWorker(childWorker) { fail() }
      renderChild(ChildWorkflow())
    }
    workflow.testRender(Unit)
      .expectWorkflow(ChildWorkflow::class, rendering = 42)
      .render { rendering ->
        assertEquals(42, rendering)
      }
  }

  @Test fun `worker ran after workflow matches workflow expectation`() {
    class ChildWorkflow : StatelessWorkflow<Unit, Nothing, Int>() {
      override fun render(
        renderProps: Unit,
        context: RenderContext
      ): Int = fail()
    }

    val childWorker = Worker.finished<Unit>()
    val workflow = Workflow.stateless<Unit, Nothing, Int> {
      renderChild(ChildWorkflow())
        .also { runningWorker(childWorker) { fail() } }
    }
    workflow.testRender(Unit)
      .expectWorkflow(ChildWorkflow::class, rendering = 42)
      .render { rendering ->
        assertEquals(42, rendering)
      }
  }

  @Test fun `realTypeMatchesExpectation() matches exact type`() {
    val expected = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() doesn't match unrelated type`() {
    val expected = unsnapshottableIdentifier(typeOf<String>())
    val actual = unsnapshottableIdentifier(typeOf<Int>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() doesn't match unrelated type parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<InvariantGenericType<Int>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() doesn't match exact invariant type with supertype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<InvariantGenericType<CharSequence>>())
    val actual = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() doesn't match exact invariant type with subtype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<InvariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<InvariantGenericType<CharSequence>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() matches exact covariant type with supertype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<CovariantGenericType<CharSequence>>())
    val actual = unsnapshottableIdentifier(typeOf<CovariantGenericType<String>>())
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() doesn't match exact covariant type with subtype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<CovariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<CovariantGenericType<CharSequence>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Suppress("ktlint:standard:max-line-length")
  @Test
  fun `realTypeMatchesExpectation() doesn't match exact contravariant type with supertype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<ContravariantGenericType<CharSequence>>())
    val actual = unsnapshottableIdentifier(typeOf<ContravariantGenericType<String>>())
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() matches exact contravariant type with subtype parameter`() {
    val expected = unsnapshottableIdentifier(typeOf<ContravariantGenericType<String>>())
    val actual = unsnapshottableIdentifier(typeOf<ContravariantGenericType<CharSequence>>())
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() matches exact class`() {
    val expected = TestWorkflow.identifier
    val actual = TestWorkflow.identifier
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() matches superclass`() {
    val expected = Workflow::class.workflowIdentifier
    val actual = TestWorkflow.identifier
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() doesn't match subclass`() {
    val expected = TestWorkflow.identifier
    val actual = Workflow::class.workflowIdentifier
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() doesn't match type with class`() {
    val classId = Workflow::class.workflowIdentifier
    val typeId = unsnapshottableIdentifier(typeOf<Worker<Unit>>())
    assertFalse(typeId.realTypeMatchesExpectation(classId))
    assertFalse(classId.realTypeMatchesExpectation(typeId))
  }

  @Test fun `realTypeMatchesExpectation() matches mockito mock of expected interface`() {
    val expected = TestWorkflowInterface::class.workflowIdentifier
    val actual = mock<TestWorkflowInterface>().identifier
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() matches mockito mock of expected abstract class`() {
    val expected = ExpectedWorkflowClass::class.workflowIdentifier
    val actual = mock<ExpectedWorkflowClass>().identifier
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() doesn't match mockito mock of unexpected interface`() {
    val expected = TestWorkflowInterface::class.workflowIdentifier
    val actual = mock<Workflow<Unit, Nothing, Unit>>().identifier
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test
  fun `realTypeMatchesExpectation() doesn't match mockito mock of unexpected abstract class`() {
    val expected = ExpectedWorkflowClass::class.workflowIdentifier
    val actual = mock<UnexpectedWorkflowClass>().identifier
    assertFalse(actual.realTypeMatchesExpectation(expected))
  }

  @Test fun `realTypeMatchesExpectation() handles mockk mocks`() {
    val expected = TestWorkflowInterface::class.workflowIdentifier
    val actual = mockk<TestWorkflowInterface>().identifier
    assertTrue(actual.realTypeMatchesExpectation(expected))
  }

  private object TestWorkflow : Workflow<Nothing, Nothing, Nothing> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  @Suppress("unused")
  private interface InvariantGenericType<T>

  @Suppress("unused")
  private interface CovariantGenericType<out T>

  @Suppress("unused")
  private interface ContravariantGenericType<in T>

  // For mocking tests. Interfaces can't be defined inside functions, and Mockito can't handle
  // the class names of local classes defined in functions named like these test functions are.
  private interface TestWorkflowInterface : Workflow<Unit, Nothing, Unit>
  private abstract class ExpectedWorkflowClass : Workflow<Unit, Nothing, Unit>
  private abstract class UnexpectedWorkflowClass : Workflow<Unit, Nothing, Unit>
}
