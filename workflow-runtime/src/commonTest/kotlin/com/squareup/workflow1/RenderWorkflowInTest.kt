package com.squareup.workflow1
//
// import androidx.compose.runtime.Composable
// import com.squareup.workflow1.RuntimeConfig.FrameTimeout
// import com.squareup.workflow1.RuntimeConfig.RenderPerAction
// import com.squareup.workflow1.internal.ParameterizedTestRunner
// import kotlinx.coroutines.CancellationException
// import kotlinx.coroutines.CompletableDeferred
// import kotlinx.coroutines.CoroutineExceptionHandler
// import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.Dispatchers.Unconfined
// import kotlinx.coroutines.ExperimentalCoroutinesApi
// import kotlinx.coroutines.FlowPreview
// import kotlinx.coroutines.cancel
// import kotlinx.coroutines.channels.Channel
// import kotlinx.coroutines.flow.MutableStateFlow
// import kotlinx.coroutines.flow.StateFlow
// import kotlinx.coroutines.flow.consumeAsFlow
// import kotlinx.coroutines.flow.launchIn
// import kotlinx.coroutines.flow.map
// import kotlinx.coroutines.flow.onEach
// import kotlinx.coroutines.flow.produceIn
// import kotlinx.coroutines.isActive
// import kotlinx.coroutines.launch
// import kotlinx.coroutines.plus
// import kotlinx.coroutines.suspendCancellableCoroutine
// import kotlinx.coroutines.test.TestScope
// import kotlinx.coroutines.test.UnconfinedTestDispatcher
// import kotlinx.coroutines.test.advanceTimeBy
// import kotlinx.coroutines.test.advanceUntilIdle
// import kotlinx.coroutines.test.runCurrent
// import okio.ByteString
// import kotlin.test.Test
//
// @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, WorkflowExperimentalRuntime::class)
// class RenderWorkflowInTest {
//
//   /**
//    * A [TestScope] that will not run until explicitly told to.
//    */
//   private lateinit var pausedTestScope: TestScope
//
//   /**
//    * A [TestScope] that will automatically dispatch enqueued routines.
//    */
//   private lateinit var testScope: TestScope
//
//   private val runtimeOptions = arrayOf(
//     RenderPerAction,
//     FrameTimeout()
//   ).asSequence()
//
//   private val runtimeTestRunner = ParameterizedTestRunner<RuntimeConfig>()
//
//   private fun setup() {
//     pausedTestScope = TestScope()
//     testScope = TestScope(UnconfinedTestDispatcher())
//   }
//
//   @Test fun `initial rendering is calculated synchronously`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val props = MutableStateFlow("foo")
//       val render = @Composable fun BaseRenderContext<String, Nothing, Nothing>.(props: String): String {
//         return "props: $props"
//       }
//       val workflow = Workflow.stateless<String, Nothing, String>(
//         render = render
//       )
//
//       // Don't allow the workflow runtime to actually start.
//
//       val renderings = renderWorkflowIn(
//         workflow = workflow,
//         scope = pausedTestScope,
//         props = props,
//         runtimeConfig = runtimeConfig
//       ) {}
//       assertEquals("props: foo", renderings.value.rendering)
//     }
//   }
//
//   @Test fun `initial rendering is calculated when scope cancelled before start`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val props = MutableStateFlow("foo")
//       val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }
//
//       pausedTestScope.cancel()
//       val renderings = renderWorkflowIn(
//         workflow = workflow,
//         scope = pausedTestScope,
//         props = props,
//         runtimeConfig = runtimeConfig
//       ) {}
//       assertEquals("props: foo", renderings.value.rendering)
//     }
//   }
//
//   @Test
//   fun `side effects from initial rendering in root workflow are never started when scope cancelled before start`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       var sideEffectWasRan = false
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningSideEffect("test") {
//           sideEffectWasRan = true
//         }
//       }
//
//       testScope.cancel()
//       renderWorkflowIn(
//         workflow,
//         testScope,
//         MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//       testScope.advanceUntilIdle()
//
//       assertFalse(sideEffectWasRan)
//     }
//   }
//
//   @Test
//   fun `side effects from initial rendering in non-root workflow are never started when scope cancelled before start`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       var sideEffectWasRan = false
//       val childWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningSideEffect("test") {
//           sideEffectWasRan = true
//         }
//       }
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         renderChild(childWorkflow)
//       }
//
//       testScope.cancel()
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//       testScope.advanceUntilIdle()
//
//       assertFalse(sideEffectWasRan)
//     }
//   }
//
//   @Test fun `new renderings are emitted on update`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val props = MutableStateFlow("foo")
//       val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }
//       val renderings = renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = props,
//         runtimeConfig = runtimeConfig
//       ) {}
//
//       assertEquals("props: foo", renderings.value.rendering)
//
//       props.value = "bar"
//
//       assertEquals("props: bar", renderings.value.rendering)
//     }
//   }
//
//   private val runtimeMatrix = arrayOf(
//     Pair(RenderPerAction, RenderPerAction),
//     Pair(RenderPerAction, FrameTimeout()),
//     Pair(FrameTimeout(), RenderPerAction),
//     Pair(FrameTimeout(), FrameTimeout())
//   ).asSequence()
//   private val runtimeMatrixTestRunner =
//     ParameterizedTestRunner<Pair<RuntimeConfig, RuntimeConfig>>()
//
//   @Test fun `saves to and restores from snapshot`() {
//     runtimeMatrixTestRunner.runParametrizedTest(
//       paramSource = runtimeMatrix,
//       before = ::setup,
//     ) { (runtimeConfig1, runtimeConfig2) ->
//       val workflow = Workflow.stateful<Unit, String, Nothing, Pair<String, (String) -> Unit>>(
//         initialState = { _, snapshot ->
//           snapshot?.bytes?.parse { it.readUtf8WithLength() } ?: "initial state"
//         },
//         snapshot = { state ->
//           Snapshot.write { it.writeUtf8WithLength(state) }
//         },
//         render = { _, renderState ->
//           Pair(
//             renderState,
//             { newState -> actionSink.send(action { state = newState }) }
//           )
//         }
//       )
//       val props = MutableStateFlow(Unit)
//       val renderings = renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = props,
//         runtimeConfig = runtimeConfig1
//       ) {}
//
//       // Interact with the workflow to change the state.
//       renderings.value.rendering.let { (state, updateState) ->
//         runtimeMatrixTestRunner.assertEquals("initial state", state)
//         updateState("updated state")
//       }
//
//       if (runtimeConfig1 is FrameTimeout) {
//         // Get past frame timeout to ensure snapshot saved.
//         testScope.advanceTimeBy(runtimeConfig1.frameTimeoutMs + 1)
//       }
//       val snapshot = renderings.value.let { (rendering, snapshot) ->
//         val (state, updateState) = rendering
//         runtimeMatrixTestRunner.assertEquals("updated state", state)
//         updateState("ignored rendering")
//         return@let snapshot
//       }
//
//       // Create a new scope to launch a second runtime to restore.
//       val restoreScope = TestScope()
//       val restoredRenderings =
//         renderWorkflowIn(
//           workflow = workflow,
//           scope = restoreScope,
//           props = props,
//           initialSnapshot = snapshot,
//           runtimeConfig = runtimeConfig2
//         ) {}
//       runtimeMatrixTestRunner.assertEquals(
//         "updated state",
//         restoredRenderings.value.rendering.first
//       )
//     }
//   }
//
//   // https://github.com/square/workflow-kotlin/issues/223
//   @Test fun `snapshots are lazy`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       lateinit var sink: Sink<String>
//       var snapped = false
//
//       val workflow = Workflow.stateful<Unit, String, Nothing, String>(
//         initialState = { _, _ -> "unchanging state" },
//         snapshot = {
//           Snapshot.of {
//             snapped = true
//             ByteString.of(1)
//           }
//         },
//         render = { _, renderState ->
//           sink = actionSink.contraMap { action { state = it } }
//           renderState
//         }
//       )
//       val props = MutableStateFlow(Unit)
//       val renderings = renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = props,
//         runtimeConfig = runtimeConfig
//       ) {}
//
//       val emitted = mutableListOf<RenderingAndSnapshot<String>>()
//       val scope = CoroutineScope(Unconfined)
//       scope.launch {
//         renderings.collect { emitted += it }
//       }
//       sink.send("unchanging state")
//
//       if (runtimeConfig is FrameTimeout) {
//         // Get past frame timeout to ensure snapshot saved.
//         testScope.advanceTimeBy(runtimeConfig.frameTimeoutMs + 1)
//       }
//
//       sink.send("unchanging state")
//
//       if (runtimeConfig is FrameTimeout) {
//         // Get past frame timeout to ensure snapshot saved.
//         testScope.advanceTimeBy(runtimeConfig.frameTimeoutMs + 1)
//       }
//
//       scope.cancel()
//
//       assertFalse(snapped)
//       assertNotSame(
//         emitted[0].snapshot.workflowSnapshot,
//         emitted[1].snapshot.workflowSnapshot
//       )
//       assertNotSame(
//         emitted[1].snapshot.workflowSnapshot,
//         emitted[2].snapshot.workflowSnapshot
//       )
//     }
//   }
//
//   @Test fun `onOutput called when output emitted`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val trigger = Channel<String>()
//       val workflow = Workflow.stateless<Unit, String, Unit> {
//         runningWorker(
//           trigger.consumeAsFlow()
//             .asWorker()
//         ) { action { setOutput(it) } }
//       }
//       val receivedOutputs = mutableListOf<String>()
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {
//         receivedOutputs += it
//       }
//       assertTrue(receivedOutputs.isEmpty())
//
//       trigger.trySend("foo").isSuccess
//       assertEquals(listOf("foo"), receivedOutputs)
//
//       trigger.trySend("bar").isSuccess
//       assertEquals(listOf("foo", "bar"), receivedOutputs)
//     }
//   }
//
//   @Test fun `onOutput is not called when no output emitted`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val workflow = Workflow.stateless<Int, String, Int> { props -> props }
//       var onOutputCalls = 0
//       val props = MutableStateFlow(0)
//       val renderings = renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = props,
//         runtimeConfig = runtimeConfig
//       ) { onOutputCalls++ }
//       assertEquals(0, renderings.value.rendering)
//       assertEquals(0, onOutputCalls)
//
//       props.value = 1
//       assertEquals(1, renderings.value.rendering)
//       assertEquals(0, onOutputCalls)
//
//       props.value = 2
//       assertEquals(2, renderings.value.rendering)
//       assertEquals(0, onOutputCalls)
//     }
//   }
//
//   /**
//    * Since the initial render occurs before launching the coroutine, an exception thrown from it
//    * doesn't implicitly cancel the scope. If it did, the reception would be reported twice: once to
//    * the caller, and once to the scope.
//    */
//   @Test fun `exception from initial render doesn't fail parent scope`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         throw ExpectedException()
//       }
//       assertFailsWith<ExpectedException> {
//         renderWorkflowIn(
//           workflow = workflow,
//           scope = testScope,
//           props = MutableStateFlow(Unit),
//           runtimeConfig = runtimeConfig
//         ) {}
//       }
//       assertTrue(testScope.isActive)
//     }
//   }
//
//   @Test
//   fun `side effects from initial rendering in root workflow are never started when initial render of root workflow fails`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       var sideEffectWasRan = false
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningSideEffect("test") {
//           sideEffectWasRan = true
//         }
//         throw ExpectedException()
//       }
//
//       assertFailsWith<ExpectedException> {
//         renderWorkflowIn(
//           workflow = workflow,
//           scope = testScope,
//           props = MutableStateFlow(Unit),
//           runtimeConfig = runtimeConfig
//         ) {}
//       }
//       assertFalse(sideEffectWasRan)
//     }
//   }
//
//   @Test
//   fun `side effects from initial rendering in non-root workflow are cancelled when initial render of root workflow fails`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       var sideEffectWasRan = false
//       var cancellationException: Throwable? = null
//       val childWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningSideEffect("test") {
//           sideEffectWasRan = true
//           suspendCancellableCoroutine { continuation ->
//             continuation.invokeOnCancellation { cause -> cancellationException = cause }
//           }
//         }
//       }
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         renderChild(childWorkflow)
//         throw ExpectedException()
//       }
//
//       assertFailsWith<ExpectedException> {
//         renderWorkflowIn(
//           workflow = workflow,
//           scope = testScope,
//           props = MutableStateFlow(Unit),
//           runtimeConfig = runtimeConfig
//         ) {}
//       }
//       assertTrue(sideEffectWasRan)
//       assertNotNull(cancellationException)
//       val realCause = generateSequence(cancellationException) { it.cause }
//         .firstOrNull { it !is CancellationException }
//       assertTrue(realCause is ExpectedException)
//     }
//   }
//
//   @Test
//   fun `side effects from initial rendering in non-root workflow are never started when initial render of non-root workflow fails`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       var sideEffectWasRan = false
//       val childWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningSideEffect("test") {
//           sideEffectWasRan = true
//         }
//         throw ExpectedException()
//       }
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         renderChild(childWorkflow)
//       }
//
//       assertFailsWith<ExpectedException> {
//         renderWorkflowIn(
//           workflow = workflow,
//           scope = testScope,
//           props = MutableStateFlow(Unit),
//           runtimeConfig = runtimeConfig
//         ) {}
//       }
//       assertFalse(sideEffectWasRan)
//     }
//   }
//
//   @Test fun `exception from non-initial render fails parent scope`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val trigger = CompletableDeferred<Unit>()
//       // Throws an exception when trigger is completed.
//       val workflow = Workflow.stateful<Unit, Boolean, Nothing, Unit>(
//         initialState = { false },
//         render = { _, throwNow ->
//           runningWorker(Worker.from { trigger.await() }) { action { state = true } }
//           if (throwNow) {
//             throw ExpectedException()
//           }
//         }
//       )
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//
//       assertTrue(testScope.isActive)
//
//       trigger.complete(Unit)
//       if (runtimeConfig is FrameTimeout) {
//         testScope.advanceTimeBy(runtimeConfig.frameTimeoutMs + 1)
//       }
//       assertFalse(testScope.isActive)
//     }
//   }
//
//   @Test fun `exception from action fails parent scope`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val trigger = CompletableDeferred<Unit>()
//       // Throws an exception when trigger is completed.
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningWorker(Worker.from { trigger.await() }) {
//           action {
//             throw ExpectedException()
//           }
//         }
//       }
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//
//       assertTrue(testScope.isActive)
//
//       trigger.complete(Unit)
//       assertFalse(testScope.isActive)
//     }
//   }
//
//   @Test fun `cancelling scope cancels runtime`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       var cancellationException: Throwable? = null
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningSideEffect(key = "test1") {
//           suspendCancellableCoroutine { continuation ->
//             continuation.invokeOnCancellation { cause -> cancellationException = cause }
//           }
//         }
//       }
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//       assertNull(cancellationException)
//       assertTrue(testScope.isActive)
//
//       testScope.cancel()
//       assertTrue(cancellationException is CancellationException)
//       assertNull(cancellationException!!.cause)
//     }
//   }
//
//   @Test fun `cancelling scope in action cancels runtime and does not render again`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val trigger = CompletableDeferred<Unit>()
//       var renderCount = 0
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         renderCount++
//         runningWorker(Worker.from { trigger.await() }) {
//           action {
//             testScope.cancel()
//           }
//         }
//       }
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//       assertTrue(testScope.isActive)
//       assertTrue(renderCount == 1)
//
//       trigger.complete(Unit)
//       testScope.advanceUntilIdle()
//       assertFalse(testScope.isActive)
//       assertEquals(
//         1,
//         renderCount,
//         "Should not render after CoroutineScope is canceled."
//       )
//     }
//   }
//
//   @Test fun `failing scope cancels runtime`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       var cancellationException: Throwable? = null
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningSideEffect(key = "failing") {
//           suspendCancellableCoroutine { continuation ->
//             continuation.invokeOnCancellation { cause -> cancellationException = cause }
//           }
//         }
//       }
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//       assertNull(cancellationException)
//       assertTrue(testScope.isActive)
//
//       testScope.cancel(CancellationException("fail!", ExpectedException()))
//       assertTrue(cancellationException is CancellationException)
//       assertTrue(cancellationException!!.cause is ExpectedException)
//     }
//   }
//
//   @Test fun `error from renderings collector doesn't fail parent scope`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
//       val renderings = renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//
//       // Collect in separate scope so we actually test that the parent scope is failed when it's
//       // different from the collecting scope.
//       val collectScope = TestScope(UnconfinedTestDispatcher())
//       collectScope.launch {
//         renderings.collect { throw ExpectedException() }
//       }
//       assertTrue(testScope.isActive)
//       assertFalse(collectScope.isActive)
//     }
//   }
//
//   @Test fun `error from renderings collector cancels runtime`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       var cancellationException: Throwable? = null
//       val workflow = Workflow.stateless<Unit, Nothing, Unit> {
//         runningSideEffect(key = "test") {
//           suspendCancellableCoroutine { continuation ->
//             continuation.invokeOnCancellation { cause ->
//               cancellationException = cause
//             }
//           }
//         }
//       }
//       val renderings = renderWorkflowIn(
//         workflow = workflow,
//         scope = pausedTestScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {}
//
//       pausedTestScope.launch {
//         renderings.collect { throw ExpectedException() }
//       }
//       assertNull(cancellationException)
//
//       pausedTestScope.advanceUntilIdle()
//       assertTrue(cancellationException is CancellationException)
//       assertTrue(cancellationException!!.cause is ExpectedException)
//     }
//   }
//
//   @Test fun `exception from onOutput fails parent scope`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val trigger = CompletableDeferred<Unit>()
//       // Emits a Unit when trigger is completed.
//       val workflow = Workflow.stateless<Unit, Unit, Unit> {
//         runningWorker(Worker.from { trigger.await() }) { action { setOutput(Unit) } }
//       }
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = pausedTestScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig
//       ) {
//         throw ExpectedException()
//       }
//       assertTrue(pausedTestScope.isActive)
//
//       trigger.complete(Unit)
//       assertTrue(pausedTestScope.isActive)
//
//       pausedTestScope.advanceUntilIdle()
//       assertFalse(pausedTestScope.isActive)
//     }
//   }
//
//   @Test fun `output is emitted before next render pass`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val outputTrigger = CompletableDeferred<String>()
//       // A workflow whose state and rendering is the last output that it emitted.
//       val workflow = Workflow.stateful<Unit, String, String, String>(
//         initialState = { "{no output}" },
//         render = { _, renderState ->
//           runningWorker(Worker.from { outputTrigger.await() }) { output ->
//             action {
//               setOutput(output)
//               state = output
//             }
//           }
//           return@stateful renderState
//         }
//       )
//       val events = mutableListOf<String>()
//
//       renderWorkflowIn(
//         workflow = workflow,
//         scope = pausedTestScope,
//         props = MutableStateFlow(Unit),
//         runtimeConfig = runtimeConfig,
//         onOutput = { events += "output($it)" }
//       )
//         .onEach { events += "rendering(${it.rendering})" }
//         .launchIn(pausedTestScope)
//       pausedTestScope.runCurrent()
//       assertEquals(listOf("rendering({no output})"), events)
//
//       outputTrigger.complete("output")
//       pausedTestScope.runCurrent()
//       assertEquals(
//         listOf(
//           "rendering({no output})",
//           "output(output)",
//           "rendering(output)",
//         ),
//         events
//       )
//     }
//   }
//
//   // https://github.com/square/workflow-kotlin/issues/224
//   @Test fun `exceptions from Snapshots don't fail runtime`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       val workflow = Workflow.stateful<Int, Unit, Nothing, Unit>(
//         snapshot = {
//           Snapshot.of {
//             throw ExpectedException()
//           }
//         },
//         initialState = { _, _ -> },
//         render = { _, _ -> }
//       )
//       val props = MutableStateFlow(0)
//       val uncaughtExceptions = mutableListOf<Throwable>()
//       val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
//         uncaughtExceptions += throwable
//       }
//       val snapshot = renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope + exceptionHandler,
//         props = props,
//         runtimeConfig = runtimeConfig
//       ) {}
//         .value
//         .snapshot
//
//       assertFailsWith<ExpectedException> { snapshot.toByteString() }
//       assertTrue(uncaughtExceptions.isEmpty())
//
//       props.value += 1
//       assertFailsWith<ExpectedException> { snapshot.toByteString() }
//     }
//   }
//
//   // https://github.com/square/workflow-kotlin/issues/224
//   @Test fun `exceptions from renderings' equals methods don't fail runtime`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       @Suppress("EqualsOrHashCode", "unused")
//       class FailRendering(val value: Int) {
//         override fun equals(other: Any?): Boolean {
//           throw ExpectedException()
//         }
//       }
//
//       val workflow = Workflow.stateless<Int, Nothing, FailRendering> { props ->
//         FailRendering(props)
//       }
//       val props = MutableStateFlow(0)
//       val uncaughtExceptions = mutableListOf<Throwable>()
//       val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
//         uncaughtExceptions += throwable
//       }
//       val ras = renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope + exceptionHandler,
//         props = props,
//         runtimeConfig = runtimeConfig
//       ) {}
//       val renderings = ras.map { it.rendering }
//         .produceIn(testScope)
//
//       @Suppress("UnusedEquals")
//       assertFailsWith<ExpectedException> {
//         renderings.tryReceive()
//           .getOrNull()!!
//           .equals(Unit)
//       }
//       assertTrue(uncaughtExceptions.isEmpty())
//
//       // Trigger another render pass.
//       props.value += 1
//     }
//   }
//
//   // https://github.com/square/workflow-kotlin/issues/224
//   @Test fun `exceptions from renderings' hashCode methods don't fail runtime`() {
//     runtimeTestRunner.runParametrizedTest(
//       paramSource = runtimeOptions,
//       before = ::setup,
//     ) { runtimeConfig: RuntimeConfig ->
//       @Suppress("EqualsOrHashCode")
//       data class FailRendering(val value: Int) {
//         override fun hashCode(): Int {
//           throw ExpectedException()
//         }
//       }
//
//       val workflow = Workflow.stateless<Int, Nothing, FailRendering> { props ->
//         FailRendering(props)
//       }
//       val props = MutableStateFlow(0)
//       val uncaughtExceptions = mutableListOf<Throwable>()
//       val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
//         uncaughtExceptions += throwable
//       }
//       val ras = renderWorkflowIn(
//         workflow = workflow,
//         scope = testScope + exceptionHandler,
//         props = props,
//         runtimeConfig = runtimeConfig
//       ) {}
//       val renderings = ras.map { it.rendering }
//         .produceIn(testScope)
//
//       @Suppress("UnusedEquals")
//       assertFailsWith<ExpectedException> {
//         renderings.tryReceive()
//           .getOrNull()
//           .hashCode()
//       }
//       assertTrue(uncaughtExceptions.isEmpty())
//
//       props.value += 1
//       @Suppress("UnusedEquals")
//       assertFailsWith<ExpectedException> {
//         renderings.tryReceive()
//           .getOrNull()
//           .hashCode()
//       }
//     }
//   }
//
//   private class ExpectedException : RuntimeException()
// }
