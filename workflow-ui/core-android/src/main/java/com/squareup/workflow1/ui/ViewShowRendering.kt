package com.squareup.workflow1.ui

import android.view.View

@WorkflowUiExperimentalApi
public typealias ViewShowRendering<RenderingT> =
  (@UnsafeVariance RenderingT, ViewEnvironment) -> Unit

/**
` * View tag that holds the function to make the view show instances of [RenderingT], and
 * the [current rendering][showing].
 *
 * @param showing the current rendering. Used by [canShowRendering] to decide if the
 * view can be updated with the next rendering.
 */
@WorkflowUiExperimentalApi
public data class ShowRenderingTag<out RenderingT : Any>(
  val showing: RenderingT,
  val environment: ViewEnvironment,
  val showRendering: ViewShowRendering<RenderingT>
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
public fun <RenderingT : Any> View.bindShowRendering(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  showRendering: ViewShowRendering<RenderingT>
) {
  setTag(
    R.id.view_show_rendering_function,
    ShowRenderingTag(initialRendering, initialViewEnvironment, showRendering)
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
public fun <RenderingT : Any> View.showRendering(
  rendering: RenderingT,
  viewEnvironment: ViewEnvironment
) {
  showRenderingTag
    ?.let { tag ->
      check(tag.showing.matches(rendering)) {
        "Expected $this to be able to show rendering $rendering, but that did not match " +
          "previous rendering ${tag.showing}. " +
          "Consider using ${WorkflowViewStub::class.java.simpleName} to display arbitrary types."
      }

      // Update the tag's rendering and viewEnvironment.
      bindShowRendering(rendering, viewEnvironment, tag.showRendering)
      // And do the actual showRendering work.
      tag.showRendering.invoke(rendering, viewEnvironment)
    }
    ?: error(
      "Expected $this to have a showRendering function to show $rendering. " +
        "Perhaps it was not built by a ${ScreenViewFactory::class.java.simpleName}, " +
        "or perhaps the factory did not call View.bindShowRendering."
    )
}

/**
 * Returns the most recent rendering shown by this view, or null if [bindShowRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> View.getRendering(): RenderingT? {
  // Can't use a val because of the parameter type.
  @Suppress("UNCHECKED_CAST")
  return when (val showing = showRenderingTag?.showing) {
    null -> null
    else -> showing as RenderingT
  }
}

/**
 * Returns the most recent [ViewEnvironment] that apply to this view, or null if [bindShowRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
public val View.environment: ViewEnvironment?
  get() = showRenderingTag?.environment

/**
 * Returns the function set by the most recent call to [bindShowRendering], or null
 * if that method has never been called.
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> View.getShowRendering(): ViewShowRendering<RenderingT>? {
  return showRenderingTag?.showRendering
}

/**
 * Returns the [ShowRenderingTag] established by the last call to [View.bindShowRendering],
 * or null if that method has never been called.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal val View.showRenderingTag: ShowRenderingTag<*>?
  get() = getTag(R.id.view_show_rendering_function) as? ShowRenderingTag<*>

@WorkflowUiExperimentalApi
private fun Any.matches(other: Any) = compatible(this, other)
