package com.squareup.sample.thingy

import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.experimental.ExperimentalTypeInference

/**
 * Creates a [BackStackWorkflow]. See the docs on [BackStackWorkflow.runBackStack] for more
 * information about what [block] can do.
 */
public inline fun <PropsT, OutputT> backStackWorkflow(
  crossinline block: suspend BackStackScope<OutputT>.(props: StateFlow<PropsT>) -> Unit
): Workflow<PropsT, OutputT, BackStackScreen<Screen>> =
  object : BackStackWorkflow<PropsT, OutputT>() {
    override suspend fun BackStackScope<OutputT>.runBackStack(props: StateFlow<PropsT>) {
      block(props)
    }
  }

/**
 * Returns a [Workflow] that renders a [BackStackScreen] whose frames are controlled by the code
 * in [runBackStack].
 *
 * [runBackStack] can show renderings and render child workflows, as well as emit outputs to this
 * workflow's parent. See the docs on that method for more info.
 */
public abstract class BackStackWorkflow<PropsT, OutputT> :
  Workflow<PropsT, OutputT, BackStackScreen<Screen>> {

  /**
   * Show renderings by calling [BackStackScope.showScreen]. Show child workflows by calling
   * [BackStackScope.showWorkflow]. Emit outputs by calling [BackStackScope.emitOutput].
   *
   * # Examples
   *
   * The backstack is represented by _nesting_ `showWorkflow` calls. Consider this example:
   * ```
   * backStackWorkflow {
   *   showWorkflow(child1) {
   *     showWorkflow(child2) {
   *       showWorkflow(child3) {
   *         // goBack()
   *       }
   *     }
   *   }
   * }
   * ```
   * This eventually represents a backstack of `[child1, child2, child3]`. `child2` will be pushed
   * onto the stack when `child1` emits an output, and `child3` pushed when `child2` emits. The
   * lambdas for `child2` and `child3` can call `goBack` to pop the stack and cancel the lambdas that
   * called their `showWorkflow`, until the next output is emitted.
   *
   * Contrast with calls in series:
   * ```
   * backStackWorkflow {
   *   showWorkflow(child1) { finishWith(Unit) }
   *   showWorkflow(child2) { finishWith(Unit) }
   *   showWorkflow(child3) { }
   * }
   * ```
   * `child1` will be shown immediately, but when it emits an output, instead of pushing `child2` onto
   * the stack, `child1` will be removed from the stack and replaced with `child2`.
   *
   * These can be combined:
   * ```
   * backStackWorkflow {
   *   showWorkflow(child1) {
   *     showWorkflow(child2) {
   *       // goBack(), or
   *       finishWith(Unit)
   *     }
   *     showWorkflow(child3) {
   *       // goBack()
   *     }
   *   }
   * }
   * ```
   * This code will show `child1` immediately, then when it emits an output show `child2`. When
   * `child2` emits an output, it can decide to call `goBack` to show `child1` again, or call
   * `finishWith` to replace itself with `child3`. `child3` can also call `goBack` to show `child`
   * again.
   */
  abstract suspend fun BackStackScope<OutputT>.runBackStack(props: StateFlow<PropsT>)

  final override fun asStatefulWorkflow():
    StatefulWorkflow<PropsT, *, OutputT, BackStackScreen<Screen>> =
    BackStackWorkflowImpl(this)
}

@DslMarker
annotation class BackStackWorkflowDsl

@BackStackWorkflowDsl
public sealed interface BackStackScope<OutputT> : CoroutineScope {

  /**
   * Emits an output to the [backStackWorkflow]'s parent.
   */
  fun emitOutput(output: OutputT)

  /**
   * Starts rendering [workflow] and pushes its rendering onto the top of the backstack.
   *
   * Whenever [workflow] emits an output, [onOutput] is launched into a new coroutine. If one call
   * doesn't finish before another output is emitted, multiple callbacks can run concurrently.
   *
   * When [onOutput] calls [BackStackNestedScope.finishWith], this workflow stops rendering, its
   * rendering is removed from the backstack, and any running output handlers are cancelled.
   *
   * When [onOutput] calls [BackStackNestedScope.goBack], if this [showWorkflow] call is nested in
   * another, then this workflow will stop rendering, any of its still-running output handlers will
   * be cancelled, and the output handler that called this [showWorkflow] will be cancelled.
   * If this is a top-level workflow in the [BackStackWorkflow], the whole
   * [BackStackWorkflow.runBackStack] is cancelled and restarted, since "back" is only a concept
   * that is relevant within a backstack, and it's not possible to know whether the parent supports
   * back. What you probably want is to emit an output instead to tell the parent to go back.
   *
   * @param props The props passed to [workflow] when rendering it. [showWorkflow] will suspend
   * until the first value is emitted. Consider transforming the [BackStackWorkflow.runBackStack]
   * props [StateFlow] or using [flowOf].
   */
  suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    // TODO revert this back to a single value – can use the same trick to update props as for
    //  emitting new screens.
    props: Flow<ChildPropsT>,
    onOutput: suspend BackStackNestedScope<OutputT, R>.(output: ChildOutputT) -> Unit
  ): R

  /**
   * Shows the screen produced by [screenFactory]. Suspends until [BackStackNestedScope.finishWith]
   * or [BackStackNestedScope.goBack] is called.
   */
  suspend fun <R> showScreen(
    screenFactory: BackStackNestedScope<OutputT, R>.() -> Screen
  ): R
}

/**
 * Scope receiver used for all [showWorkflow] calls. This has all the capabilities of
 * [BackStackScope] with the additional ability to [finish][finishWith] a nested workflow or
 * [go back][goBack] to its outer workflow.
 */
@BackStackWorkflowDsl
public sealed interface BackStackNestedScope<OutputT, R> : BackStackScope<OutputT> {

  /**
   * Causes the [showWorkflow] call that ran the output handler that was passed this scope to return
   * [value] and cancels any output handlers still running for that workflow. The workflow is
   * removed from the stack and will no longer be rendered.
   */
  suspend fun finishWith(value: R): Nothing

  /**
   * Removes all workflows started by the parent workflow's handler that invoked this [showWorkflow]
   * from the stack, and cancels that parent output handler coroutine (and thus all child workflow
   * coroutines as well).
   */
  suspend fun goBack(): Nothing
}

@OptIn(ExperimentalTypeInference::class)
public suspend inline fun <OutputT, ChildOutputT, R> BackStackScope<OutputT>.showWorkflow(
  workflow: Workflow<Unit, ChildOutputT, Screen>,
  @BuilderInference noinline onOutput: suspend BackStackNestedScope<OutputT, R>.(output: ChildOutputT) -> Unit
): R = showWorkflow(workflow, props = flowOf(Unit), onOutput)

// public suspend inline fun <OutputT, ChildOutputT> BackStackScope<OutputT>.showWorkflow(
//   workflow: Workflow<Unit, ChildOutputT, Screen>,
//   noinline onOutput: suspend BackStackNestedScope<OutputT, Unit>.(output: ChildOutputT) -> Unit
// ): Unit = showWorkflow(workflow, props = flowOf(Unit), onOutput)

public suspend inline fun <ChildPropsT> BackStackScope<*>.showWorkflow(
  workflow: Workflow<ChildPropsT, Nothing, Screen>,
  props: Flow<ChildPropsT>,
): Nothing = showWorkflow(workflow, props = props) { error("Cannot call") }

public suspend inline fun BackStackScope<*>.showWorkflow(
  workflow: Workflow<Unit, Nothing, Screen>,
): Nothing = showWorkflow(workflow, props = flowOf(Unit)) { error("Cannot call") }
