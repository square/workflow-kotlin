package com.squareup.sample.thingy

import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Creates a [BackStackWorkflow]. See the docs on [BackStackWorkflow.runBackStack] for more
 * information about what [block] can do.
 */
public inline fun <PropsT, OutputT> backStackWorkflow(
  crossinline block: suspend BackStackScope.(
    props: StateFlow<PropsT>,
    emitOutput: (OutputT) -> Unit
  ) -> Unit
): Workflow<PropsT, OutputT, BackStackScreen<Screen>> =
  object : BackStackWorkflow<PropsT, OutputT>() {
    override suspend fun BackStackScope.runBackStack(
      props: StateFlow<PropsT>,
      emitOutput: (OutputT) -> Unit
    ) {
      block(props, emitOutput)
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
   * [BackStackScope.showWorkflow]. Emit outputs by calling [emitOutput].
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
  abstract suspend fun BackStackScope.runBackStack(
    props: StateFlow<PropsT>,
    emitOutput: (OutputT) -> Unit
  )

  final override fun asStatefulWorkflow():
    StatefulWorkflow<PropsT, *, OutputT, BackStackScreen<Screen>> =
    BackStackWorkflowImpl(this)
}

@DslMarker
annotation class BackStackWorkflowDsl

@BackStackWorkflowDsl
public sealed interface BackStackParentScope {

  /**
   * Starts rendering [workflow] and pushes its rendering onto the top of the backstack.
   *
   * Whenever [workflow] emits an output, [onOutput] is launched into a new coroutine. If one call
   * doesn't finish before another output is emitted, multiple callbacks can run concurrently.
   *
   * When [onOutput] returns a value, this workflow stops rendering, its rendering is removed from
   * the backstack, and any running output handlers are cancelled. The calling coroutine is resumed
   * with the value.
   *
   * When [onOutput] calls [BackStackWorkflowScope.goBack], if this [showWorkflow] call is nested in
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
    onOutput: suspend BackStackWorkflowScope.(output: ChildOutputT) -> R
  ): R

  /**
   * Shows the screen produced by [screenFactory]. Suspends untilBackStackNestedScope.goBack] is
   * called.
   */
  suspend fun <R> showScreen(
    screenFactory: BackStackScreenScope<R>.() -> Screen
  ): R
}

@BackStackWorkflowDsl
public sealed interface BackStackScope : BackStackParentScope, CoroutineScope

/**
 * Scope receiver used for all [showWorkflow] calls. This has all the capabilities of
 * [BackStackScope] with the additional ability to [go back][goBack] to its outer workflow.
 */
@BackStackWorkflowDsl
public sealed interface BackStackWorkflowScope : BackStackScope {

  /**
   * Removes all workflows started by the parent workflow's handler that invoked this [showWorkflow]
   * from the stack, and cancels that parent output handler coroutine (and thus all child workflow
   * coroutines as well).
   */
  suspend fun goBack(): Nothing
}

@BackStackWorkflowDsl
public sealed interface BackStackScreenScope<R> : BackStackScope {
  fun continueWith(value: R)
  fun goBack()
}

public suspend inline fun <ChildOutputT, R> BackStackParentScope.showWorkflow(
  workflow: Workflow<Unit, ChildOutputT, Screen>,
  noinline onOutput: suspend BackStackWorkflowScope.(output: ChildOutputT) -> R
): R = showWorkflow(workflow, props = flowOf(Unit), onOutput)

public suspend inline fun <ChildPropsT> BackStackParentScope.showWorkflow(
  workflow: Workflow<ChildPropsT, Nothing, Screen>,
  props: Flow<ChildPropsT>,
): Nothing = showWorkflow(workflow, props = props) { error("Cannot call") }

public suspend inline fun BackStackParentScope.showWorkflow(
  workflow: Workflow<Unit, Nothing, Screen>,
): Nothing = showWorkflow(workflow, props = flowOf(Unit)) { error("Cannot call") }
