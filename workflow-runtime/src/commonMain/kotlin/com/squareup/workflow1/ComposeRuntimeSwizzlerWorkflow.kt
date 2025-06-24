package com.squareup.workflow1

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot.Companion as ComposeSnapshot
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.squareup.workflow1.StatefulWorkflow.RenderContext
import com.squareup.workflow1.internal.WorkStealingDispatcher
import com.squareup.workflow1.internal.compose.WorkflowComposableRuntimeConfig
import com.squareup.workflow1.internal.compose.createSaveableStateRegistryForSnapshot
import com.squareup.workflow1.internal.compose.renderWorkflow
import com.squareup.workflow1.internal.compose.savedValuesToSnapshot
import com.squareup.workflow1.internal.compose.withCompositionLocals
import com.squareup.workflow1.internal.requireSend
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

/**
 * A special workflow that renders the entire subtree below it with the Compose runtime. This is a
 * finer-grained way to turn on the [RuntimeConfigOptions.COMPOSE_RUNTIME] flag.
 */
public class ComposeRuntimeSwizzlerWorkflow<P, O, R>(public val child: Workflow<P, O, R>) :
  Workflow<P, O, R> {
  override fun asStatefulWorkflow(): StatefulWorkflow<P, *, O, R> {
    throw UnsupportedOperationException(
      "This workflow is handled directly by the workflow runtime.",
    )
  }
}

/**
 * A holder for the real [State] that only supports referential equality. This allows a workflow
 * action to set the state to a "new" value while still referring to the same actual state.
 *
 * This MUST NOT be a data class or implement equals by comparing state values.
 */
private class StateHolder<P, O, R>(val state: State<P, O, R>)

private class State<P, O, R>(
  initialProps: P,
  snapshot: Snapshot?,
  private val scope: CoroutineScope,
) {
  private val dispatcher =
    WorkStealingDispatcher(
      scope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Unconfined,
    )
  private var rendering: R? = null
  private val recomposeRequests = Channel<Unit>(capacity = 1)
  private val outputs = Channel<O>(capacity = 1000)
  private val clock = BroadcastFrameClock(onNewAwaiters = { recomposeRequests.trySend(Unit) })
  private var props by mutableStateOf(initialProps)
  private val saveableStateRegistry = createSaveableStateRegistryForSnapshot(snapshot)

  fun start(child: Workflow<P, O, R>) {
    scope.launchMolecule(
      mode = RecompositionMode.ContextClock,
      context = clock + dispatcher,
      emitter = { rendering = it },
    ) {
      withCompositionLocals(LocalSaveableStateRegistry provides saveableStateRegistry) {
        renderWorkflow(
          workflow = child,
          props = props,
          onOutput = outputs::requireSend,
          renderKey = "",
          parentSession = null,
          config = WorkflowComposableRuntimeConfig(),
        )
      }
    }
  }

  fun render(props: P, context: RenderContext<P, StateHolder<P, O, R>, O>): R {
    context.runningSideEffect("events") {
      while (true) {
        select<Unit> {
          outputs.onReceive { output ->
            context.actionSink.send(action("sendOutput") { setOutput(output) })
          }

          recomposeRequests.onReceive {
            context.actionSink.send(
              action("recomposeRequest") {
                // Set the state to a new holder that will return false from equals so the workflow
                // runtime invalidates this node.
                this.state = StateHolder(this@State)
              },
            )
          }
        }
      }
    }

    this.props = props
    // Since we just changed props, pump that into the Compose runtime so it can resume its
    // recomposition loop if it needs to.
    ComposeSnapshot.sendApplyNotifications()
    // Pump the dispatcher to force the recomposition loop to continue and request a frame.
    dispatcher.advanceUntilIdle()
    // Consume the requested frame so it doesn't get fired again from the side effect.
    recomposeRequests.tryReceive()
    // Send the frame to perform recomposition and effects.
    // Hard-code unchanging frame time since there's no actual frame time code shouldn't rely on
    // this value.
    clock.sendFrame(0L)
    return rendering!!
  }

  fun snapshot(): Snapshot {
    val savedValues = saveableStateRegistry.performSave()
    return savedValuesToSnapshot(savedValues)
  }
}
