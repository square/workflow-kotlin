@file:Suppress("NOTHING_TO_INLINE")

package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.internal.TraceLabels
import com.squareup.workflow1.compose.internal.renderTraditionalWorkflow
import com.squareup.workflow1.identifier
import com.squareup.workflow1.traceNoFinally

/**
 * Renders a child [Workflow] with the given [props] and returns its rendering.
 *
 * Example:
 * ```kotlin
 * data class CounterRendering(val count: Int)
 *
 * @Composable
 * override fun produceRendering(
 *   props: Unit,
 *   emitOutput: (Nothing) -> Unit
 * ): CounterRendering {
 *   val count = renderChild(counterWorkflow)
 *   return CounterRendering(count)
 * }
 * ```
 *
 * ## Output handling
 *
 * When the child emits an output, the [onOutput] callback will be invoked. [onOutput] may change
 * snapshot state and/or emit output to this workflow's parent by calling the `emitOutput` function
 * passed to its [produceRendering][ComposeWorkflow.produceRendering] method. When [onOutput] calls
 * `emitOutput` once, the output will be propagated to the parent synchronously. It is not
 * recommended to call `emitOutput` more than once from the same [onOutput] call, but it is allowed:
 * subsequent calls will queue up the outputs to be handled asynchronously.
 *
 * ## Recomposition
 *
 * This function will always render the child synchronously and return the rendering produced. It is
 * appropriate to call if your [produceRendering][ComposeWorkflow.produceRendering] method needs to
 * access the child's rendering directly in composition, e.g. to place (parts of) it in its own
 * rendering or make decisions about what other rendering code to run.
 *
 * However, this means that the composable that calls this function will recompose any time the
 * child needs to be re-rendered, even if only for internal state changes that end up returning the
 * same rendering value (i.e. this function is not independently "restartable"). It also means that
 * any time the composable that calls this function recomposes, this function will be called again
 * (i.e. it is not "skippable"). For these reasons, if you do not need to access the child's
 * rendering in composition, it's better to use [renderChildAsState], which returns a [State] of the
 * rendering and is both skippable and restartable.
 *
 * @param workflow The child [Workflow] to render. This can be any [Workflow] type, it does not need
 * to be a [ComposeWorkflow].
 */
@WorkflowExperimentalApi
@Composable
public fun <PropsT, OutputT, RenderingT> renderChild(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?
): RenderingT = withFixedWorkflowComposableRuntimeConfig { config ->
  key(workflow.identifier, config) {
    // Allow the runtime to intercept composable render calls.
    if (config.interceptor != null) {
      config.workflowTracer.traceNoFinally(TraceLabels.InterceptRenderWorkflow) {
        // renderChildSafely ensures the interceptor doesn't try to do anything weird with its
        // proceed calls.
        config.interceptor.renderChildSafely(
          childWorkflow = workflow,
          props = props,
          onOutput = onOutput,
          proceed = { innerWorkflow, innerProps, innerOnOutput ->
            // The interceptor can pass a different workflow instance, so we need to key again.
            key(innerWorkflow.identifier) {
              renderChildImpl(
                workflow = innerWorkflow,
                props = innerProps,
                onOutput = innerOnOutput,
                runtimeConfig = config.runtimeConfig,
                workflowTracer = config.workflowTracer
              )
            }
          }
        )
      }
    } else {
      // This whole function is keyed on the config, so the fact that there are two callsites of
      // this function is fine since the only way a different branch can be used is if the entire
      // workflow tree is being reset anyway.
      renderChildImpl(
        workflow = workflow,
        props = props,
        onOutput = onOutput,
        runtimeConfig = config.runtimeConfig,
        workflowTracer = config.workflowTracer
      )
    }
  }
}

/** @see renderChild */
@WorkflowExperimentalApi
@Composable
inline fun <OutputT, RenderingT> renderChild(
  workflow: Workflow<Unit, OutputT, RenderingT>,
  noinline onOutput: ((OutputT) -> Unit)?
): RenderingT = renderChild(workflow, props = Unit, onOutput)

/** @see renderChild */
@WorkflowExperimentalApi
@Composable
inline fun <PropsT, RenderingT> renderChild(
  workflow: Workflow<PropsT, Nothing, RenderingT>,
  props: PropsT,
): RenderingT = renderChild(workflow, props, onOutput = null)

/** @see renderChild */
@WorkflowExperimentalApi
@Composable
inline fun <RenderingT> renderChild(
  workflow: Workflow<Unit, Nothing, RenderingT>,
): RenderingT = renderChild(workflow, props = Unit, onOutput = null)

/**
 * Renders a child [Workflow] with the given [props] and returns a [State] that holds the rendering.
 *
 * The same [State] instance will be returned for every recomposition of this function, even if the
 * lifecycle of the [workflow] itself restarts. Thus, it is safe to capture the returned [State]
 * object and reference it for as long as this function is in the composition.
 *
 * Example:
 * ```kotlin
 * data class CounterRendering(val getCount: () -> Int)
 *
 * @Composable
 * override fun produceRendering(
 *   props: Unit,
 *   emitOutput: (Nothing) -> Unit
 * ): CounterRendering {
 *   val count by renderChildAsState(counterWorkflow)
 *   return CounterRendering(getCount = { count })
 * }
 * ```
 *
 * ## Output handling
 *
 * When the child emits an output, the [onOutput] callback will be invoked. [onOutput] may change
 * snapshot state and/or emit output to this workflow's parent by calling the `emitOutput` function
 * passed to its [produceRendering][ComposeWorkflow.produceRendering] method. When [onOutput] calls
 * `emitOutput` once, the output will be propagated to the parent synchronously. It is not
 * recommended to call `emitOutput` more than once from the same [onOutput] call, but it is allowed:
 * subsequent calls will queue up the outputs to be handled asynchronously.
 *
 * ## Recomposition
 *
 * This function will initially render the child synchronously and return the rendering produced. It
 * is appropriate to call if your [produceRendering][ComposeWorkflow.produceRendering] method does
 * not need to access the child's rendering directly in composition, e.g. it only reads the
 * rendering value inside an effect, or from a Composable function returned in your own rendering.
 *
 * This function makes the child both restartable and skippable:
 *
 *  - If [workflow] needs to be rerendered but the calling composable does not, the child will
 *    rerender without recomposing the caller.
 *  - If the calling composable recomposes but passes the same arguments to this function, then the
 *    child will not rerender and its previous rendering will be returned.
 *
 * If you need to access the rendering value directly in composition, it's more efficient to call
 * [renderChild] instead.
 *
 * @param workflow The child [Workflow] to render. This can be any [Workflow] type, it does not need
 * to be a [ComposeWorkflow].
 */
@WorkflowExperimentalApi
@Composable
public fun <PropsT, OutputT, RenderingT> renderChildAsState(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?
): State<RenderingT> {
  val renderingHolder: MutableState<RenderingT?> = remember { mutableStateOf(null) }

  ChildWorkflowRecomposeIsolator(workflow, props, onOutput, renderingHolder)

  // This cast is safe since above will always set the state to a value of RenderingT on the initial
  // composition.
  @Suppress("UNCHECKED_CAST")
  return renderingHolder as State<RenderingT>
}

/** @see renderChildAsState */
@WorkflowExperimentalApi
@Composable
inline fun <OutputT, RenderingT> renderChildAsState(
  workflow: Workflow<Unit, OutputT, RenderingT>,
  noinline onOutput: ((OutputT) -> Unit)?
): State<RenderingT> = renderChildAsState(workflow, props = Unit, onOutput)

/** @see renderChildAsState */
@WorkflowExperimentalApi
@Composable
inline fun <PropsT, RenderingT> renderChildAsState(
  workflow: Workflow<PropsT, Nothing, RenderingT>,
  props: PropsT,
): State<RenderingT> = renderChildAsState(workflow, props, onOutput = null)

/** @see renderChildAsState */
@WorkflowExperimentalApi
@Composable
inline fun <RenderingT> renderChildAsState(
  workflow: Workflow<Unit, Nothing, RenderingT>,
): State<RenderingT> = renderChildAsState(workflow, props = Unit, onOutput = null)

/**
 * This is a function that only exists to create an isolated restartable, skippable recompose scope
 * from its caller for rendering [workflow] from [renderChildAsState].
 *
 * In order to do this, it MUST return Unit and not be inline — never change this!
 */
@OptIn(WorkflowExperimentalApi::class)
@Composable
private fun <PropsT, OutputT, RenderingT> ChildWorkflowRecomposeIsolator(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?,
  renderingHolder: MutableState<RenderingT>
) {
  renderingHolder.value = renderChild(workflow, props, onOutput)
}

@WorkflowExperimentalApi
public inline fun <PropsT, OutputT, RenderingT> Workflow.Companion.composable(
  crossinline produceRendering: @Composable (
    props: PropsT,
    emitOutput: (OutputT) -> Unit
  ) -> RenderingT
): Workflow<PropsT, OutputT, RenderingT> = object : ComposeWorkflow<PropsT, OutputT, RenderingT>() {
  @Composable
  override fun produceRendering(
    props: PropsT,
    emitOutput: (OutputT) -> Unit
  ): RenderingT = produceRendering(props, emitOutput)
}

@OptIn(WorkflowExperimentalApi::class)
@Composable
private fun <PropsT, OutputT, RenderingT> renderChildImpl(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?,
  runtimeConfig: RuntimeConfig,
  workflowTracer: WorkflowTracer?,
): RenderingT =
  // Do not need to call key() again here since it's done in renderChild.
  workflowTracer.traceNoFinally(TraceLabels.RenderWorkflow) {
    if (workflow is ComposeWorkflow<*, *, *>) {
      workflow as ComposeWorkflow<PropsT, OutputT, RenderingT>
      workflow.invokeProduceRendering(
        props = props,
        emitOutput = onOutput ?: {}
      )
    } else {
      val statefulWorkflow = remember {
        @Suppress("UNCHECKED_CAST")
        workflow.asStatefulWorkflow() as StatefulWorkflow<PropsT, Any?, OutputT, RenderingT>
      }
      renderTraditionalWorkflow(
        workflow = statefulWorkflow,
        props = props,
        onOutput = onOutput,
        runtimeConfig = runtimeConfig,
        workflowTracer = workflowTracer,
      )
    }
  }
