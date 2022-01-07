package com.squareup.workflow1.ui

import android.view.View
import com.squareup.workflow1.ui.WorkflowViewState.New
import com.squareup.workflow1.ui.WorkflowViewState.Started

/**
 * Function attached to a view created by [ViewFactory], to allow it
 * to respond to [View.showRendering].
 */
@WorkflowUiExperimentalApi
public typealias ViewShowRendering<RenderingT> =
    (@UnsafeVariance RenderingT, ViewEnvironment) -> Unit
// Unsafe because typealias ViewShowRendering<in RenderingT> is not supported, can't
// declare variance on a typealias. If I recall correctly.

/**
 * For use by implementations of [ViewFactory.buildView]. Establishes [showRendering]
 * as the implementation of [View.showRendering] for the receiver, possibly replacing
 * the existing one.
 *
 * - After this method is called, [View.start] must be called exactly
 *   once before [View.showRendering] can be called.
 * - If this method is called again _after_ [View.start] (e.g. if a [View] is reused),
 *   the receiver is reset to its initialized state, and [View.start] must
 *   be called again.
 *
 * @see ViewFactory
 * @see DecorativeViewFactory
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@WorkflowUiExperimentalApi
@Deprecated("Use ScreenViewHolder")
public fun <RenderingT : Any> View.bindShowRendering(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  showRendering: ViewShowRendering<RenderingT>
) {
  workflowViewState = when (workflowViewStateOrNull) {
    is New<*> -> New(initialRendering, initialViewEnvironment, showRendering, starter)
    else -> New(initialRendering, initialViewEnvironment, showRendering)
  }

  // Note that if there is already a `New<*>` tag, we have to take care to propagate
  // the starter. Repeated calls happen whenever one ViewFactory delegates to another.
  //
  //  - We render `NamedScreen(FooScreen())`
  //  - The view is built by `FooScreenFactory`, which calls `bindShowRendering<FooScreen>()`
  //  - `NamedScreenFactory` invokes `FooScreenFactory.buildView`, and calls
  //    `bindShowRendering<NamedScreen<*>>()` on the view that `FooScreenFactory` built.
}

/**
 * Note that [WorkflowViewStub] calls this method for you.
 *
 * Makes the initial call to [View.showRendering], along with any wrappers that have been
 * added via [ViewRegistry.buildView], or [DecorativeViewFactory.viewStarter].
 *
 * - It is an error to call this method more than once.
 * - It is an error to call [View.showRendering] without having called this method first.
 */
@WorkflowUiExperimentalApi
public fun View.start() {
  val current = workflowViewStateAsNew
  workflowViewState = Started(current.showing, current.environment, current.showRendering)
  current.starter(this)
}

/**
 * Note that [WorkflowViewStub.showRendering] makes this check for you.
 *
 * True if this view is able to show [rendering].
 *
 * Returns `false` if [View.bindShowRendering] has not been called, so it is always safe to
 * call this method. Otherwise returns the [compatibility][compatible] of the current
 * [View.getRendering] and the new one.
 */
@WorkflowUiExperimentalApi
public fun View.canShowRendering(rendering: Any): Boolean {
  return getRendering<Any>()?.let { compatible(it, rendering) } == true
}

/**
 * It is usually more convenient to call [WorkflowViewStub.showRendering]
 * than to call this method directly.
 *
 * Shows [rendering] in this View by invoking the [ViewShowRendering] function
 * previously set by [bindShowRendering].
 *
 * @throws IllegalStateException if [bindShowRendering] has not been called.
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> View.showRendering(
  rendering: RenderingT,
  viewEnvironment: ViewEnvironment
) {
  workflowViewStateAsStarted.let { viewState ->
    check(compatible(viewState.showing, rendering)) {
      "Expected $this to be able to show rendering $rendering, but that did not match " +
        "previous rendering ${viewState.showing}. " +
        "Consider using WorkflowViewStub to display arbitrary types."
    }

    // Update the tag's rendering and viewEnvironment before calling
    // the actual showRendering function.
    workflowViewState = Started(rendering, viewEnvironment, viewState.showRendering)
    viewState.showRendering.invoke(rendering, viewEnvironment)
  }
}

/**
 * Returns the most recent rendering shown by this view cast to [RenderingT],
 * or null if [bindShowRendering] has never been called.
 *
 * @throws ClassCastException if the current rendering is not of type [RenderingT]
 */
@WorkflowUiExperimentalApi
public inline fun <reified RenderingT : Any> View.getRendering(): RenderingT? {
  // Can't use a val because of the parameter type.
  return when (val showing = workflowViewStateOrNull?.showing) {
    null -> null
    else -> showing as RenderingT
  }
}

/**
 * Returns the most recent [ViewEnvironment] applied to this view, or null if [bindShowRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
public val View.environment: ViewEnvironment?
  get() = workflowViewStateOrNull?.environment

/**
 * Returns the function set by the most recent call to [bindShowRendering], or null
 * if that method has never been called.
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> View.getShowRendering(): ViewShowRendering<RenderingT>? {
  return workflowViewStateOrNull?.showRendering
}

@WorkflowUiExperimentalApi
internal var View.starter: (View) -> Unit
  get() = workflowViewStateAsNew.starter
  set(value) {
    workflowViewState = workflowViewStateAsNew.copy(starter = value)
  }
