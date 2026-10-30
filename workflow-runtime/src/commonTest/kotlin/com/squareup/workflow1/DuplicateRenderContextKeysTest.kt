package com.squareup.workflow1

import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions.NONE
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Checks that every runtime rejects duplicate keys passed to [BaseRenderContext.renderChild],
 * [BaseRenderContext.runningSideEffect], and [BaseRenderContext.remember] within a single render
 * pass, and that the keys are forgotten between render passes.
 *
 * Test functions call [runTest] directly, without arguments that refer to `this`. See the docs on
 * [RenderWorkflowInSnapshotSaveRestoreTest] for why.
 */
@OptIn(ExperimentalCoroutinesApi::class, WorkflowExperimentalRuntime::class)
@Burst
class DuplicateRenderContextKeysTest(private val runtime: RuntimeOptions = NONE) {

  private val childA = Workflow.stateless<Unit, Nothing, String> { "a" }
  private val childB = Workflow.stateless<Unit, Nothing, String> { "b" }

  @BeforeTest
  fun setUp() {
    // runTest shares the scheduler of a test Main dispatcher.
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun renderChild_throws_when_type_and_key_are_duplicated() = runTest {
    val workflow =
      Workflow.stateless<Unit, Nothing, Unit> {
        renderChild(childA, key = "foo")
        renderChild(childA, key = "foo")
      }

    val error = assertFailsWith<IllegalArgumentException> { start(workflow) }
    assertEquals("Expected keys to be unique for ${childA.identifier}: key=\"foo\"", error.message)
  }

  @Test
  fun renderChild_throws_when_type_is_duplicated_with_default_key() = runTest {
    val workflow =
      Workflow.stateless<Unit, Nothing, Unit> {
        renderChild(childA)
        renderChild(childA)
      }

    val error = assertFailsWith<IllegalArgumentException> { start(workflow) }
    assertEquals("Expected keys to be unique for ${childA.identifier}: key=\"\"", error.message)
  }

  @Test
  fun renderChild_allows_same_type_with_different_keys() = runTest {
    val workflow =
      Workflow.stateless<Unit, Nothing, String> {
        renderChild(childA, key = "foo") + renderChild(childA, key = "bar")
      }

    assertEquals("aa", start(workflow))
  }

  @Test
  fun renderChild_allows_different_types_with_same_key() = runTest {
    val workflow =
      Workflow.stateless<Unit, Nothing, String> {
        renderChild(childA, key = "foo") + renderChild(childB, key = "foo")
      }

    assertEquals("ab", start(workflow))
  }

  @Test
  fun runningSideEffect_throws_when_key_is_duplicated() = runTest {
    val workflow =
      Workflow.stateless<Unit, Nothing, Unit> {
        runningSideEffect("same") {}
        runningSideEffect("same") {}
      }

    val error = assertFailsWith<IllegalArgumentException> { start(workflow) }
    assertEquals("Expected side effect keys to be unique: \"same\"", error.message)
  }

  @Test
  fun runningSideEffect_allows_different_keys() = runTest {
    val workflow =
      Workflow.stateless<Unit, Nothing, String> {
        runningSideEffect("one") {}
        runningSideEffect("two") {}
        "ok"
      }

    assertEquals("ok", start(workflow))
  }

  @Test
  fun remember_throws_when_key_type_and_inputs_are_duplicated() = runTest {
    val workflow =
      Workflow.stateless<Unit, Nothing, String> {
        remember("same", typeOf<String>(), "input") { "1" }
        remember("same", typeOf<String>(), "input") { "2" }
      }

    val error = assertFailsWith<IllegalArgumentException> { start(workflow) }
    assertEquals(
      "Expected unique combination of key, input types and result type: \"same\"",
      error.message,
    )
  }

  @Test
  fun remember_allows_same_key_with_different_inputs_or_types() = runTest {
    val workflow =
      Workflow.stateless<Unit, Nothing, String> {
        remember("same", typeOf<String>(), "input1") { "a" } +
          remember("same", typeOf<String>(), "input2") { "b" } +
          remember("same", typeOf<String>()) { "c" } +
          remember("same", typeOf<CharSequence>(), "input1") { "d" }
      }

    assertEquals("abcd", start(workflow))
  }

  @Test
  fun keys_are_forgotten_between_render_passes() = runTest {
    val props = MutableStateFlow(0)
    val workflow =
      Workflow.stateless<Int, Nothing, String> { props ->
        runningSideEffect("effect") {}
        remember("remember", typeOf<String>()) { "" } + renderChild(childA, key = "child") + props
      }

    val renderings =
      renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope,
        props = props,
        runtimeConfig = runtime.runtimeConfig,
      ) {}
    assertEquals("a0", renderings.value.rendering)

    props.value = 1
    testScheduler.advanceUntilIdle()
    testScheduler.runCurrent()
    assertEquals("a1", renderings.value.rendering)

    props.value = 2
    testScheduler.advanceUntilIdle()
    testScheduler.runCurrent()
    assertEquals("a2", renderings.value.rendering)
  }

  private fun <RenderingT> TestScope.start(
    workflow: Workflow<Unit, Nothing, RenderingT>
  ): RenderingT =
    renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtime.runtimeConfig,
      ) {}
      .value
      .rendering
}
