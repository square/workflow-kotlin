package com.squareup.workflow1.compose.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Sink
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.compose.renderChild
import com.squareup.workflow1.identifier
import com.squareup.workflow1.trace
import com.squareup.workflow1.traceNoFinally
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KType

@OptIn(WorkflowExperimentalApi::class)
@Composable
internal fun <P, S, O, R> renderTraditionalWorkflow(
  workflow: StatefulWorkflow<P, S, O, R>,
  props: P,
  onOutput: ((O) -> Unit)?,
  runtimeConfig: RuntimeConfig,
  workflowTracer: WorkflowTracer?,
): R {
  val workflowScope = rememberCoroutineScope()
  val state = rememberSaveable(
    stateSaver = SnapshotSaver(
      initialProps = props,
      statefulWorkflow = workflow,
      workflowTracer = workflowTracer,
    )
  ) {
    mutableStateOf(
      workflowTracer.trace(TraceLabels.InitialState) {
        workflow.initialState(
          props = props,
          snapshot = null,
          workflowScope = workflowScope
        )
      }
    )
  }

  val baseContext = remember {
    ComposeRenderContext<P, S, O>(
      initialProps = props,
      state = state,
      runtimeConfig = runtimeConfig,
      workflowTracer = workflowTracer,
    )
  }
  baseContext.updateProps(props, workflow)
  baseContext.onOutput = onOutput

  return baseContext.use {
    workflow.render(
      renderProps = props,
      renderState = state.value,
      context = RenderContext(baseContext, workflow)
    )
  }
}

@OptIn(WorkflowExperimentalApi::class)
private class ComposeRenderContext<P, S, O>(
  initialProps: P,
  private val state: MutableState<S>,
  override val runtimeConfig: RuntimeConfig,
  override val workflowTracer: WorkflowTracer?,
) : BaseRenderContext<P, S, O>,
  Sink<WorkflowAction<P, S, O>> {
  override val actionSink: Sink<WorkflowAction<P, S, O>>
    get() = this

  var lastProps by mutableStateOf(initialProps)
  var onOutput: ((O) -> Unit)? = null

  private var trapdoor: Trapdoor? = null

  fun updateProps(
    props: P,
    workflow: StatefulWorkflow<P, S, O, *>
  ) {
    Snapshot.withoutReadObservation {
      if (props != lastProps) {
        workflowTracer.traceNoFinally(TraceLabels.OnPropsChanged) {
          state.value = workflow.onPropsChanged(
            old = lastProps,
            new = props,
            state = state.value
          )
        }
        lastProps = props
      }
    }
  }

  override fun send(value: WorkflowAction<P, S, O>) {
    workflowTracer.trace(TraceLabels.SendAction) {
      // Send can be called from any thread so wrap non-atomic reads/writes in a snapshot.
      Snapshot.withMutableSnapshot {
        val (newState, applied) = value.applyTo(lastProps, state.value)
        state.value = newState
        onOutput?.let { onOutput ->
          applied.output?.value?.let(onOutput)
        }
      }
    }
  }

  @Composable
  inline fun <R> use(block: () -> R): R {
    Trapdoor.open { trapdoor ->
      this.trapdoor = trapdoor
      val result = block()
      this.trapdoor = null
      return result
    }
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    requireTrapdoor("runningSideEffect").composeReturning {
      key(key) {
        // We use the key function instead of passing key to LaunchedEffect since we want
        // this group to be movable, not just restartable.
        LaunchedEffect(sideEffect, block = sideEffect)
      }
    }
  }

  override fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT = requireTrapdoor("remember").composeReturning {
    // TODO RememberStore also keys off inputs, but i don't think we need to make that part of
    //  the movable group's dataKey?
    key(key, resultType) {
      remember(*inputs, calculation = calculation)
    }
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<P, S, O>
  ): ChildRenderingT = requireTrapdoor("renderChild").composeReturning {
    // renderChild creates a movable group based on child.identifier, but does not include
    // key.
    key(child.identifier, key) {
      renderChild(child, props, onOutput = { output ->
        val action = handler(output)
        actionSink.send(action)
      })
    }
  }

  private fun requireTrapdoor(operationName: String): Trapdoor =
    trapdoor ?: error("Cannot perform $operationName on RenderContext outside of render pass.")
}

