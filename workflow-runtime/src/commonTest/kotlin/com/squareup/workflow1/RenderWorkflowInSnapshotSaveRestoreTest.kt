package com.squareup.workflow1

import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfigOptions.COMPOSE_RUNTIME
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions.NONE
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * This only contains the single test ([saves_to_and_restores_from_snapshot]) from
 * [RenderWorkflowInTest] that needs a second runtime config parameter (`runtime2`). It was split
 * out because Kotlin 2.3.10 crashed when `runtime2` was a test method parameter. With the
 * constraints below it may be possible to merge this test back into the main suite.
 *
 * `runtime2` MUST stay a test method parameter rather than a class parameter. Burst generates a
 * class per combination of class parameters, and Kotlin/Native's generated test registration code
 * for a file reserves stack space for every test class in that file (about 32 bytes per class). As
 * a class parameter this file expands to 2 × 2 × N² classes, where N is the number of
 * [RuntimeOptions] entries, which overflows the 512KB stack of the worker threads that
 * Kotlin/Native runs module initializers on once N passes ~64, crashing the whole test binary with
 * SIGBUS.
 *
 * The test function MUST call [runTest] directly, and MUST NOT pass [runTest] any argument that
 * refers to `this` (such as `runTest(dispatcherUsed)`):
 * - Burst only generates specializations that return the `TestResult` when the test function itself
 *   calls [runTest]. If [runTest] is hidden behind a helper function, the generated per-value test
 *   functions call the original and discard its result. On JS that result is the Promise Mocha has
 *   to await, so every one of those tests would complete immediately and pass without its body ever
 *   having run to completion.
 * - Burst copies the [runTest] arguments into each generated specialization, and in Kotlin 2.4.0
 *   the Kotlin/Native backend crashes with a `NativeCodeGeneratorException` when a copied argument
 *   expression refers to `this`.
 *
 * So instead of running the test body itself on the dispatcher under test, the body launches both
 * runtimes in explicit [TestScope]s that use it.
 */
@OptIn(ExperimentalCoroutinesApi::class, WorkflowExperimentalRuntime::class)
@Burst
class RenderWorkflowInSnapshotSaveRestoreTest(
  useTracer: Boolean = false,
  private val useUnconfined: Boolean = true,
  private val runtime: RuntimeOptions = NONE,
) {

  private val runtimeConfig = runtime.runtimeConfig
  private val traces: StringBuilder = StringBuilder()
  private val testTracer: WorkflowTracer? =
    if (useTracer) {
      object : WorkflowTracer {
        var prefix: String = ""

        override fun beginSection(label: String) {
          traces.appendLine("${prefix}Starting$label")
          prefix += "  "
        }

        override fun endSection() {
          prefix = prefix.substring(0, prefix.length - 2)
          traces.appendLine("${prefix}Ending")
        }
      }
    } else {
      null
    }

  private val myStandardTestDispatcher = StandardTestDispatcher()
  private val dispatcherUsed =
    if (useUnconfined) UnconfinedTestDispatcher() else myStandardTestDispatcher

  private fun advanceIfStandard() {
    if (dispatcherUsed == myStandardTestDispatcher) {
      dispatcherUsed.scheduler.advanceUntilIdle()
      dispatcherUsed.scheduler.runCurrent()
    }
  }

  @BeforeTest
  public fun setup() {
    traces.clear()
    Dispatchers.setMain(dispatcherUsed)
  }

  @AfterTest
  public fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun saves_to_and_restores_from_snapshot(runtime2: RuntimeOptions = NONE) = runTest {
    if ((COMPOSE_RUNTIME in runtimeConfig) != (COMPOSE_RUNTIME in runtime2.runtimeConfig)) {
      // Snapshots created by the traditional runtime and the compose runtime are not compatible.
      return@runTest
    }

    val workflow =
      Workflow.stateful<Unit, String, Nothing, Pair<String, (String) -> Unit>>(
        initialState = { _, snapshot ->
          snapshot?.bytes?.parse { it.readUtf8WithLength() } ?: "initial state"
        },
        snapshot = { state -> Snapshot.write { it.writeUtf8WithLength(state) } },
        render = { _, renderState ->
          Pair(renderState, { newState -> actionSink.send(action("") { state = newState }) })
        },
      )
    val props = MutableStateFlow(Unit)
    // See the class KDoc for why this doesn't just use this test's own scope.
    val renderScope = TestScope(dispatcherUsed)
    val renderings =
      renderWorkflowIn(
        workflow = workflow,
        scope = renderScope,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
    advanceIfStandard()

    // Interact with the workflow to change the state.
    renderings.value.rendering.let { (state, updateState) ->
      assertEquals("initial state", state)
      updateState("updated state")
    }
    advanceIfStandard()

    val snapshot =
      renderings.value.let { (rendering, snapshot) ->
        val (state, updateState) = rendering
        assertEquals("updated state", state)
        updateState("ignored rendering")
        return@let snapshot
      }
    advanceIfStandard()

    // Create a new scope to launch a second runtime to restore.
    val restoreScope = TestScope(dispatcherUsed)
    val restoredRenderings =
      renderWorkflowIn(
        workflow = workflow,
        scope = restoreScope,
        props = props,
        initialSnapshot = snapshot,
        workflowTracer = null,
        runtimeConfig = runtime2.runtimeConfig,
      ) {}
    advanceIfStandard()
    assertEquals("updated state", restoredRenderings.value.rendering.first)

    renderScope.cancel()
    restoreScope.cancel()
  }

  /**
   * A saved [TreeSnapshot] can outlive a change to the runtime flag, e.g. across an app update or
   * process death. The runtimes' snapshot formats aren't compatible, so restoring with the other
   * runtime must fail cleanly: every workflow in the tree starts from its initial state exactly as
   * if there were no snapshot, without crashing and without being handed bytes it didn't write.
   */
  @Test
  fun does_not_restore_snapshot_saved_by_other_runtime(runtime2: RuntimeOptions = NONE) = runTest {
    if ((COMPOSE_RUNTIME in runtimeConfig) == (COMPOSE_RUNTIME in runtime2.runtimeConfig)) {
      // Covered by saves_to_and_restores_from_snapshot.
      return@runTest
    }

    val initialStateSnapshots = mutableListOf<Snapshot?>()
    val workflow =
      StringStateWorkflow(initialStateSnapshots, child = StringStateWorkflow(initialStateSnapshots))
    // Round-trip through bytes like a real app persisting the snapshot would.
    val savedBytes = renderAndSaveUpdatedState(workflow)

    initialStateSnapshots.clear()
    val restoreScope = TestScope(dispatcherUsed)
    val restoredRenderings =
      renderWorkflowIn(
        workflow = workflow,
        scope = restoreScope,
        props = MutableStateFlow(Unit),
        initialSnapshot = TreeSnapshot.parse(savedBytes),
        workflowTracer = null,
        runtimeConfig = runtime2.runtimeConfig,
      ) {}
    advanceIfStandard()

    // Both the parent and the child should have been initialized with no snapshot.
    assertEquals(listOf<Snapshot?>(null, null), initialStateSnapshots)
    assertEquals(INITIAL_STATE, restoredRenderings.value.rendering.state)
    assertEquals(INITIAL_STATE, restoredRenderings.value.rendering.child!!.state)

    restoreScope.cancel()
  }

  /**
   * Persisted snapshot bytes can be corrupted, truncated, or written by an incompatible version.
   * Whatever the bytes, the runtime must not crash, and must start every workflow in the tree from
   * its initial state exactly as if there were no snapshot.
   */
  @Test
  fun ignores_garbage_snapshot(garbage: GarbageSnapshot = GarbageSnapshot.EMPTY) = runTest {
    val initialStateSnapshots = mutableListOf<Snapshot?>()
    val workflow =
      StringStateWorkflow(initialStateSnapshots, child = StringStateWorkflow(initialStateSnapshots))
    // Some garbage variants are derived from real snapshot bytes written by this runtime.
    val validBytes = renderAndSaveUpdatedState(workflow)
    val garbageBytes = garbage.corrupt(validBytes)

    initialStateSnapshots.clear()
    val restoreScope = TestScope(dispatcherUsed)
    val restoredRenderings =
      renderWorkflowIn(
        workflow = workflow,
        scope = restoreScope,
        props = MutableStateFlow(Unit),
        initialSnapshot = TreeSnapshot.parse(garbageBytes),
        workflowTracer = null,
        runtimeConfig = runtimeConfig,
      ) {}
    advanceIfStandard()

    // Both the parent and the child should have been initialized with no snapshot.
    assertEquals(listOf<Snapshot?>(null, null), initialStateSnapshots)
    assertEquals(INITIAL_STATE, restoredRenderings.value.rendering.state)
    assertEquals(INITIAL_STATE, restoredRenderings.value.rendering.child!!.state)

    restoreScope.cancel()
  }

  /**
   * Runs [workflow] with this test's runtime config, updates the state of the root workflow and its
   * child to [UPDATED_STATE], and returns the serialized [TreeSnapshot] of that state.
   */
  private fun renderAndSaveUpdatedState(workflow: StringStateWorkflow): ByteString {
    // See the class KDoc for why this doesn't just use this test's own scope.
    val renderScope = TestScope(dispatcherUsed)
    val renderings =
      renderWorkflowIn(
        workflow = workflow,
        scope = renderScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
    advanceIfStandard()
    assertEquals(INITIAL_STATE, renderings.value.rendering.state)
    assertEquals(INITIAL_STATE, renderings.value.rendering.child!!.state)

    renderings.value.rendering.update(UPDATED_STATE)
    advanceIfStandard()
    renderings.value.rendering.child!!.update(UPDATED_STATE)
    advanceIfStandard()
    assertEquals(UPDATED_STATE, renderings.value.rendering.state)
    assertEquals(UPDATED_STATE, renderings.value.rendering.child!!.state)

    val bytes = renderings.value.snapshot.toByteString()
    renderScope.cancel()
    return bytes
  }

  /** Ways in which persisted [TreeSnapshot] bytes can be invalid. */
  enum class GarbageSnapshot {
    /** Zero bytes. */
    EMPTY {
      override fun corrupt(validBytes: ByteString): ByteString = ByteString.EMPTY
    },

    /** Bytes that were never a snapshot at all. */
    RANDOM {
      override fun corrupt(validBytes: ByteString): ByteString = randomBytes(64)
    },

    /** A real snapshot that lost its second half. */
    TRUNCATED {
      override fun corrupt(validBytes: ByteString): ByteString =
        validBytes.substring(0, validBytes.size / 2)
    },

    /** A length prefix that claims a negative size. */
    NEGATIVE_LENGTH_PREFIX {
      override fun corrupt(validBytes: ByteString): ByteString =
        Buffer().writeInt(-1).write(randomBytes(16)).readByteString()
    },

    /** A length prefix that claims far more bytes than are present. */
    OVERSIZED_LENGTH_PREFIX {
      override fun corrupt(validBytes: ByteString): ByteString =
        Buffer().writeInt(Int.MAX_VALUE).write(randomBytes(16)).readByteString()
    },

    /** A real snapshot with its bytes shuffled, so the framing is plausible but wrong. */
    SCRAMBLED {
      override fun corrupt(validBytes: ByteString): ByteString =
        validBytes.toByteArray().apply { shuffle(Random(0)) }.toByteString()
    };

    abstract fun corrupt(validBytes: ByteString): ByteString

    protected fun randomBytes(count: Int): ByteString = Random(0).nextBytes(count).toByteString()
  }

  private companion object {
    const val INITIAL_STATE = "initial state"
    const val UPDATED_STATE = "updated state"
  }
}

private class StringStateRendering(
  val state: String,
  val child: StringStateRendering?,
  val update: (String) -> Unit,
)

/**
 * Keeps a [String] state that it snapshots, and records every [Snapshot] it's asked to restore from
 * in [initialStateSnapshots]. Optionally renders a [child] of the same type.
 *
 * Decodes snapshots leniently so that a runtime handing it bytes it didn't write shows up as a
 * clear assertion failure on [initialStateSnapshots] rather than an exception from [initialState].
 */
private class StringStateWorkflow(
  private val initialStateSnapshots: MutableList<Snapshot?>,
  private val child: StringStateWorkflow? = null,
) : StatefulWorkflow<Unit, String, Nothing, StringStateRendering>() {

  override fun initialState(props: Unit, snapshot: Snapshot?): String {
    initialStateSnapshots += snapshot
    if (snapshot == null) return "initial state"
    return runCatching { snapshot.bytes.parse { it.readUtf8WithLength() } }
      .getOrElse { "undecodable snapshot: ${snapshot.bytes.hex()}" }
  }

  override fun render(
    renderProps: Unit,
    renderState: String,
    context: RenderContext<Unit, String, Nothing>,
  ): StringStateRendering =
    StringStateRendering(
      state = renderState,
      child = child?.let { context.renderChild(it) },
      update = context.eventHandler("update") { newState: String -> state = newState },
    )

  override fun snapshotState(state: String): Snapshot = Snapshot.write {
    it.writeUtf8WithLength(state)
  }
}
