package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.compose.SampleComposeWorkflow.Rendering
import kotlinx.coroutines.flow.StateFlow

/**
 * A [Workflow]-like interface that participates in a workflow tree via its [produceRendering]
 * composable. See the docs on [produceRendering] for more information on writing composable
 * workflows.
 *
 * @sample SampleComposeWorkflow
 */
@WorkflowExperimentalApi
@Stable
public abstract class ComposeWorkflow<
  in PropsT,
  out OutputT,
  out RenderingT
  > : Workflow<PropsT, OutputT, RenderingT> {

  /**
   * The main composable of this workflow that consumes some [props] from its parent and may emit
   * an output via [emitOutput]. Equivalent to [StatefulWorkflow.render].
   *
   * To render child workflows (composable or otherwise) from this method, call [renderWorkflow].
   *
   * Any compose snapshot state that is read in this method or any methods it calls, that is later
   * changed, will trigger a re-render of the workflow tree. See
   * [BaseRenderContext.renderComposable] for more details on how composition is tied to the
   * workflow lifecycle.
   *
   * To save state when the workflow tree is restored, use [rememberSaveable]. This is equivalent
   * to implementing [StatefulWorkflow.snapshotState].
   *
   * @param props The [PropsT] value passed in from the parent workflow.
   * @param emitOutput A function that can be called to emit an [OutputT] value to the parent
   * workflow. Calling this method is analogous to sending an action to
   * [BaseRenderContext.actionSink] that calls
   * [setOutput][com.squareup.workflow1.WorkflowAction.Updater.setOutput]. If this function is
   * called from the `onOutput` callback of a [renderWorkflow], then it is equivalent to returning
   * an action from [BaseRenderContext.renderChild]'s `handler` parameter.
   *
   * @sample SampleComposeWorkflow.produceRendering
   */
  @WorkflowComposable
  @Composable
  protected abstract fun produceRendering(
    props: PropsT,
    emitOutput: (OutputT) -> Unit
  ): RenderingT

  /**
   * Render this workflow as a child of another [WorkflowComposable], ensuring that the workflow's
   * [produceRendering] method is a separate recompose scope from the caller.
   */
  @Composable
  internal fun renderWithRecomposeBoundary(
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?
  ): RenderingT {
    // Since this function returns a value, it can't restart without also restarting its parent.
    // IsolateRecomposeScope allows the subtree to restart and only restarts us if the rendering
    // value actually changed.
    val renderingState = remember { mutableStateOf<RenderingT?>(null) }
    RecomposeScopeIsolator(
      props = props,
      onOutput = onOutput,
      result = renderingState
    )

    // The value is guaranteed to have been set at least once by RecomposeScopeIsolator so this cast
    // will never fail. Note we can't use !! since RenderingT itself might nullable, so null is
    // still a potentially valid rendering value.
    @Suppress("UNCHECKED_CAST")
    return renderingState.value as RenderingT
  }

  /**
   * Creates an isolated recompose scope that separates a non-restartable caller ([render]) from
   * a non-restartable function call ([produceRendering]). This is accomplished simply by this
   * function having a [Unit] return type and being not inline.
   *
   * **It MUST have a [Unit] return type to do its job.**
   */
  @Composable
  private fun RecomposeScopeIsolator(
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?,
    result: MutableState<RenderingT?>,
  ) {
    result.value = produceRendering(props, onOutput ?: {})
  }

  private var statefulImplCache: ComposeWorkflowWrapper? = null
  final override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, OutputT, RenderingT> =
    statefulImplCache ?: ComposeWorkflowWrapper().also { statefulImplCache = it }

  /**
   * Exposes this [ComposeWorkflow] as a [StatefulWorkflow].
   */
  private inner class ComposeWorkflowWrapper :
    StatefulWorkflow<PropsT, Unit, OutputT, RenderingT>() {

    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?
    ) {
      // Noop
    }

    override fun render(
      renderProps: PropsT,
      renderState: Unit,
      context: RenderContext
    ): RenderingT = context.renderComposable {
      // Explicitly remember the output function since we know that actionSink is stable even though
      // Compose might not know that.
      val emitOutput: (OutputT) -> Unit = remember(context.actionSink) {
        { output -> context.actionSink.send(OutputAction(output)) }
      }

      // Since we're composing directly from renderComposable, we don't need to isolate the
      // recompose boundary again. This root composable is already a recompose boundary, and we
      // don't need to create a redundant rendering state holder.
      return@renderComposable produceRendering(
        props = renderProps,
        emitOutput = emitOutput
      )
    }

    override fun snapshotState(state: Unit): Snapshot? = null

    private inner class OutputAction(
      private val output: OutputT
    ) : WorkflowAction<PropsT, Unit, OutputT>() {
      override fun Updater.apply() {
        setOutput(output)
      }
    }
  }
}

@OptIn(WorkflowExperimentalApi::class)
private class SampleComposeWorkflow
// In real code, this constructor would probably be injected by Dagger or something.
constructor(
  private val injectedService: Service,
  private val child: Workflow<String, String, String>
) : ComposeWorkflow<
  /* PropsT */
  String,
  /* OutputT */
  String,
  /* RenderingT */
  Rendering
  >() {

  // In real code, this would not be defined in the workflow itself but somewhere else in the
  // codebase.
  interface Service {
    val values: StateFlow<String>
  }

  data class Rendering(
    val label: String,
    val onClick: () -> Unit
  )

  @Composable
  override fun produceRendering(
    props: String,
    emitOutput: (String) -> Unit
  ): Rendering {
    // ComposeWorkflows use native compose idioms to manage state, including saving state to be
    // restored later.
    var clickCount by rememberSaveable { mutableIntStateOf(0) }

    // They also use native compose idioms to work with Flows and perform effects.
    val serviceValue by injectedService.values.collectAsState()

    // And they can render child workflows, just like traditional workflows. This is equivalent to
    // calling BaseRenderContext.renderChild().
    // Note that there's no explicit key: the child key is tied to where it's called in the
    // composition, the same way other composable state is keyed.
    val childRendering = renderWorkflow(
      workflow = child,
      props = "child props",
      // This is equivalent to the handler parameter on renderChild().
      onOutput = {
        emitOutput("child emitted output: $it")
      }
    )

    return Rendering(
      // Reading clickCount and serviceValue here mean that when those values are changed, it will
      // trigger a render pass in the hosting workflow tree, which will recompose this method.
      label = "props=$props, " +
        "clickCount=$clickCount, " +
        "serviceValue=$serviceValue, " +
        "childRendering=$childRendering",
      onClick = {
        // Instead of using WorkflowAction's state property, you can just update snapshot state
        // objects directly.
        clickCount++

        // This is equivalent to calling setOutput from a WorkflowAction.
        emitOutput("clicked!")
      }
    )
  }
}
