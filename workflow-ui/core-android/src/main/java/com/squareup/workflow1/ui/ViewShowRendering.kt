package com.squareup.workflow1.ui

import android.view.View

/**
 * Function attached to a view created by [ViewRegistry.buildView], to allow the view
 * to respond to [View.showRendering].
 */
@WorkflowUiExperimentalApi
public typealias ViewShowRendering<RenderingT> =
  (@UnsafeVariance RenderingT, ViewEnvironment) -> Unit

/**
 * View tag that holds the [current rendering][showing] and [ViewEnvironment][environment].
 *
 * @param showing the current rendering. Used by [canShowRendering] to decide if the
 * view can be updated with the next rendering.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal data class ShowingTag<out RenderingT : Any>(
  val showing: RenderingT,
  val environment: ViewEnvironment
)

/**
 * Establishes [showRendering] as the implementation of [View.showRendering]
 * for the receiver, possibly replacing the existing one. Likewise sets / updates
 * the values returned by [View.getRendering] and [View.environment].
 *
 * Intended for use by implementations of [ViewFactory.buildView].
 *
 * @see DecorativeViewFactory
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> View.bindShowRendering(showRendering: ViewShowRendering<RenderingT>) {
  setTag(
    R.id.view_show_rendering_function,
    showRendering
  )
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * True if this view is able to show [rendering].
 *
 * Returns `false` if [bindShowRendering] has not been called, so it is always safe to
 * call this method. Otherwise returns the [compatibility][compatible] of the initial
 * [rendering] and the new one.
 */
@WorkflowUiExperimentalApi
public fun View.canShowRendering(rendering: Any): Boolean {
  return getRendering<Any>()?.matches(rendering) == true
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Sets the workflow rendering associated with this view, and displays it by
 * invoking the [ViewShowRendering] function previously set by [bindShowRendering].
 *
 * @throws IllegalStateException if [bindShowRendering] has not been called.
 */
@WorkflowUiExperimentalApi
public fun View.showRendering(
  rendering: Any,
  viewEnvironment: ViewEnvironment
) {
  showingTag?.let { tag ->
    check(unwrap(tag.showing).matches(unwrap(rendering))) {
      "Expected $this to be able to show rendering $rendering, but that did not match " +
        "previous rendering ${tag.showing}. " +
        "Consider using ${WorkflowViewStub::class.java.simpleName} to display arbitrary types."
    }
  }

  // Update the tag's rendering and viewEnvironment.
  setTag(R.id.view_showing, ShowingTag(rendering, viewEnvironment))

  getShowRendering()
    ?.invoke(unwrap(rendering), viewEnvironment)
    ?: error(
      "Expected $this to have a showRendering function to show $rendering. " +
        "Perhaps it was not built by a ${ViewFactory::class.java.simpleName}, " +
        "or perhaps the factory did not call View.bindShowRendering."
    )
}

/**
 * Returns the most recent rendering shown by this view, or null if [showRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
public inline fun <reified RenderingT : Any> View.getRendering(): RenderingT? {
  return when (val showing = showingTag?.showing) {
    null -> null
    else -> showing as RenderingT
  }
}

/**
 * Returns the most recent [ViewEnvironment] that applies to this view, or null if [showRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
public val View.environment: ViewEnvironment?
  get() = showingTag?.environment

/**
 * Returns the function set by the most recent call to [bindShowRendering], or null
 * if that method has never been called.
 */
@WorkflowUiExperimentalApi
public fun View.getShowRendering(): ViewShowRendering<Any>? {
  @Suppress("UNCHECKED_CAST")
  return getTag(R.id.view_show_rendering_function) as? ViewShowRendering<Any>
}

/**
 * Returns the [ShowingTag] established by the last call to [View.showRendering],
 * or null if that method has never been called.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal val View.showingTag: ShowingTag<*>?
  get() = getTag(R.id.view_showing) as? ShowingTag<*>

@WorkflowUiExperimentalApi
private fun Any.matches(other: Any) = compatible(this, other)
