package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.IdCacheable
import com.squareup.workflow1.ImpostorWorkflow
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier

/**
 * TODO
 */
@WorkflowExperimentalApi
public abstract class ComposeWorkflow<PropsT, OutputT, out RenderingT> :
  Workflow<PropsT, OutputT, RenderingT>,
  IdCacheable {

  /**
   * Use a lazy delegate so that any [ImpostorWorkflow.realIdentifier] will have been computed
   * before this is initialized and cached.
   */
  override var cachedIdentifier: WorkflowIdentifier? = null

  /**
   * Override this method to produce the rendering value from this workflow.
   *
   * When a parent workflow renders this one, it must provide a [props] value that is passed to this
   * method. This workflow can emit outputs to its parent by calling [emitOutput]. Calling
   * [emitOutput] from most callbacks will either synchronously or asynchronously (depending on the
   * workflow dispatcher) send the output to the parent, which may in turn emit its own output, etc
   * bubbling all the way up to the root workflow.
   *
   * This method can render other workflows (of any [Workflow] type, not just other
   * [ComposeWorkflow]s) by calling [renderChild] or [renderChildAsState].
   *
   * For unit testability, it's recommended to factor out your logic into stateless composables and
   * call them from here. This allows your tests to pass in whatever state they wish to test with.
   *
   * Example:
   * ```kotlin
   * data class Greeting(
   *   val message: String,
   *   val onClick: () -> Unit
   * )
   *
   * class GreetingWorkflow: ComposeWorkflow<String, Unit, Greeting>() {
   *   @Composable
   *   override fun produceRendering(
   *     props: String,
   *     emitOutput: (Unit) -> Unit
   *   ): Greeting {
   *     var welcome by remember { mutableStateOf(true) }
   *     // return Greeting(
   *     //   message = if (welcome) "Hello $props" else "Goodbye $props",
   *     //   onClick = {
   *     //     welcome = !welcome
   *     //     emitOutput(Unit)
   *     //   }
   *     // )
   *     return produceGreeting(
   *       name = props,
   *       welcome = welcome,
   *       onClick = {
   *         welcome = !welcome
   *         emitOutput(Unit)
   *       }
   *     )
   *   }
   * }
   *
   * @Composable
   * internal fun produceGreeting(
   *   name: String,
   *   welcome: Boolean,
   *   onClick: () -> Unit
   * ): Greeting = Greeting(
   *   message = if (welcome) "Hello $props" else "Goodbye $props",
   *   onClick = onClick
   * )
   * ```
   *
   * @param emitOutput Function to emit output to the parent workflow. This will be the same
   * instance for the entire lifetime of this composable, so it's safe to capture in state that
   * lives beyond a single recomposition.
   */
  @Composable
  protected abstract fun produceRendering(
    props: PropsT,
    emitOutput: (OutputT) -> Unit
  ): RenderingT

  final override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, OutputT, RenderingT> {
    throw UnsupportedOperationException(
      "This version of the Compose runtime does not support ComposeWorkflow. " +
        "Please upgrade your workflow-runtime."
    )
  }

  /** Helper to expose [produceRendering] to code outside this class. Do not call directly! */
  @Composable
  internal inline fun invokeProduceRendering(
    props: PropsT,
    noinline emitOutput: (OutputT) -> Unit
  ): RenderingT = produceRendering(props, emitOutput)
}
