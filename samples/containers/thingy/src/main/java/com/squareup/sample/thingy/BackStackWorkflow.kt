package com.squareup.sample.thingy

import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Creates a [BackStackWorkflow].
 *
 * @param getBackStackFactory See [BackStackWorkflow.getBackStackFactory]. If null, the default
 * implementation is used.
 * @param runBackStack See [BackStackWorkflow.runBackStack].
 */
public inline fun <PropsT, OutputT> backStackWorkflow(
  noinline getBackStackFactory: ((CoroutineContext) -> BackStackFactory)? = null,
  crossinline runBackStack: suspend BackStackScope.(
    props: StateFlow<PropsT>,
    emitOutput: (OutputT) -> Unit
  ) -> Unit
): Workflow<PropsT, OutputT, BackStackScreen<Screen>> =
  object : BackStackWorkflow<PropsT, OutputT>() {
    override suspend fun BackStackScope.runBackStack(
      props: StateFlow<PropsT>,
      emitOutput: (OutputT) -> Unit
    ) {
      runBackStack(props, emitOutput)
    }

    override fun getBackStackFactory(coroutineContext: CoroutineContext): BackStackFactory =
      if (getBackStackFactory != null) {
        getBackStackFactory(coroutineContext)
      } else {
        super.getBackStackFactory(coroutineContext)
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
   * # Showing a screen
   *
   * ```
   * backStackWorkflow { _, _ ->
   *   // Suspends until continueWith is called.
   *   val result = showScreen {
   *     MyScreenClass(
   *       // Returns "finished" from showScreen.
   *       onDoneClicked = { continueWith("finished") },
   *     )
   *   }
   * }
   * ```
   *
   * # Showing a workflow
   *
   * ```
   * backStackWorkflow { _, _ ->
   *   // Suspends until an onOutput lambda returns a value.
   *   val result = showWorkflow(
   *     childWorkflow,
   *     props = flowOf(childProps)
   *     onOutput = { output ->
   *       // Returns "finished: …" from showWorkflow.
   *       return@showWorkflow "finished: $output"
   *     }
   *   )
   * }
   * ```
   *
   * # Emitting output
   *
   * The second parameter to the [runBackStack] function is an [emitOutput] function that will send
   * whatever you pass to it to this workflow's parent as an output.
   * ```
   * backStackWorkflow { _, emitOutput ->
   *   showWorkflow(
   *     childWorkflow,
   *     props = flowOf(childProps)
   *     onOutput = { output ->
   *       // Forward the output to parent.
   *       emitOutput(output)
   *     }
   *   )
   * }
   * ```
   *
   * # Nested vs serial calls
   *
   * The backstack is represented by _nesting_ `showWorkflow` calls. Consider this example:
   * ```
   * backStackWorkflow { _, _ ->
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
   * backStackWorkflow { _, _ ->
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
   * backStackWorkflow { _, _ ->
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
   *
   * To push another screen on the backstack from a non-workflow screen, [launch] a coroutine:
   * ```
   * backStackScreen { _, _ ->
   *   showScreen {
   *     MyScreen(
   *       onEvent = {
   *         launch {
   *           showWorkflow(…)
   *         }
   *       }
   *     }
   *   }
   * }
   * ```
   *
   * # Cancelling screens
   *
   * Calling [BackStackScope.showScreen] or [BackStackScope.showWorkflow] suspends the caller until
   * that workflow/screen produces a result. They handle coroutine cancellation too: if the calling
   * coroutine is cancelled while they're showing, they are removed from the backstack.
   *
   * This can be used to, for example, update a screen based on a flow:
   * ```
   * backStackWorkflow { props, _ ->
   *   props.collectLatest { prop ->
   *     showScreen {
   *       MyScreen(message = prop)
   *     }
   *   }
   * }
   * ```
   * This example shows the props received from the parent to the user via `MyScreen`. Every time
   * the parent passes a new props, the `showScreen` call is cancelled and called again with the
   * new props, replacing the old instance of `MyScreen` in the backstack with a new one. Since
   * both instances of `MyScreen` are compatible, this is not a navigation event but just updates
   * the `MyScreen` view factory.
   *
   * # Factoring out code
   *
   * You don't have to keep all the logic for your backstack in a single function. You can pull out
   * functions, just make them extensions on [BackStackParentScope] to get access to `showScreen`
   * and `showRendering` calls.
   *
   * E.g. here's a helper that performs some suspending task and shows a retry screen if it fails:
   * ```
   * suspend fun <R> BackStackParentScope.userRetriable(
   *   action: suspend () -> R
   * ): R {
   *   var result = runCatching { action() }
   *   // runCatching can catch CancellationException, so check.
   *   ensureActive()
   *
   *   while (result.isFailure) {
   *     showScreen {
   *       RetryScreen(
   *         message = "Failed: ${result.exceptionOrNull()}",
   *         onRetryClicked = { continueWith(Unit) },
   *         onCancelClicked = { goBack() }
   *       )
   *     }
   *
   *     // Try again.
   *     result = runCatching { action() }
   *     ensureActive()
   *   }
   *
   *   // We only leave the loop when action succeeded.
   *   return result.getOrThrow()
   * }
   * ```
   */
  abstract suspend fun BackStackScope.runBackStack(
    props: StateFlow<PropsT>,
    emitOutput: (OutputT) -> Unit
  )

  /**
   * Return a [BackStackFactory] used to convert the stack of screens produced by this workflow to
   * a [BackStackScreen].
   *
   * The default implementation tries to find a [BackStackFactory] passed to the workflow runtime
   * via its [CoroutineScope], and if that fails, returns an implementation that will throw whenever
   * the stack is empty or the top screen is idle.
   */
  open fun getBackStackFactory(coroutineContext: CoroutineContext): BackStackFactory =
    coroutineContext.backStackFactory ?: BackStackFactory.ThrowOnIdle

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
   * When [onOutput] calls [BackStackWorkflowScope.cancelWorkflow], if this [showWorkflowImpl] call is nested in
   * another, then this workflow will stop rendering, any of its still-running output handlers will
   * be cancelled, and the output handler that called this [showWorkflowImpl] will be cancelled.
   * If this is a top-level workflow in the [BackStackWorkflow], the whole
   * [BackStackWorkflow.runBackStack] is cancelled and restarted, since "back" is only a concept
   * that is relevant within a backstack, and it's not possible to know whether the parent supports
   * back. What you probably want is to emit an output instead to tell the parent to go back.
   *
   * If the coroutine calling [showWorkflowImpl] is cancelled, the workflow stops being rendered and its
   * rendering will be removed from the backstack.
   *
   * See [BackStackWorkflow.runBackStack] for high-level documentation about how to use this method
   * to implement a backstack workflow.
   *
   * @param props The props passed to [workflow] when rendering it. [showWorkflowImpl] will suspend
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
   *
   * If the coroutine calling [showScreen] is cancelled, the rendering will be removed from the
   * backstack.
   *
   * See [BackStackWorkflow.runBackStack] for high-level documentation about how to use this method
   * to implement a backstack workflow.
   */
  suspend fun <R> showScreen(
    screenFactory: BackStackScreenScope<R>.() -> Screen
  ): R
}

@BackStackWorkflowDsl
public sealed interface BackStackScope : BackStackParentScope, CoroutineScope

/**
 * Scope receiver used for all [showWorkflow] calls. This has all the capabilities of
 * [BackStackScope] with the additional ability to [go back][cancelWorkflow] to its outer workflow.
 */
@BackStackWorkflowDsl
public sealed interface BackStackWorkflowScope : BackStackScope {

  /**
   * Removes all workflows started by the parent workflow's handler that invoked this [showWorkflowImpl]
   * from the stack, and cancels that parent output handler coroutine (and thus all child workflow
   * coroutines as well).
   */
  suspend fun cancelWorkflow(): Nothing
}

/**
 * Scope receiver used for all [showScreen] calls. This has all the capabilities of
 * [BackStackScope] with the additional ability to [go back][cancelScreen] to its outer workflow and
 * to return from [showScreen] by calling [continueWith].
 */
@BackStackWorkflowDsl
public sealed interface BackStackScreenScope<R> : BackStackScope {
  /**
   * Causes [showScreen] to return with [value].
   */
  fun continueWith(value: R)

  fun cancelScreen()
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
