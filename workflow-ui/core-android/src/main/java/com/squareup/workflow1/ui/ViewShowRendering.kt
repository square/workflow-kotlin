package com.squareup.workflow1.ui

import android.view.View

// TODO: deprecate everything in here

@WorkflowUiExperimentalApi
@Suppress("unused")
@Deprecated(
    "Use ShowRendering.",
    ReplaceWith("ShowRendering<RenderingT>", "com.squareup.workflow1.ui.ShowRendering")
)
typealias ViewShowRendering<RenderingT> = (@UnsafeVariance RenderingT, ViewEnvironment) -> Unit

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Establishes [showRendering] as the implementation of [View.showRendering]
 * for the receiver, possibly replacing the existing one. Immediately invokes [showRendering]
 * to display [initialRendering].
 *
 * Intended for use by implementations of [ViewFactory.buildView].
 */
@WorkflowUiExperimentalApi
// TODO: replace with bindDisplayRendering
fun <RenderingT : Any> View.bindShowRendering(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  showRendering: ViewShowRendering<RenderingT>
) {
  setTag(
      R.id.view_show_rendering_function,
      ShowRenderingTag(initialRendering, initialViewEnvironment, showRendering)
  )
  showRendering.invoke(initialRendering, initialViewEnvironment)
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
fun View.canShowRendering(rendering: Any): Boolean {
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
fun <RenderingT : Any> View.showRendering(
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

        bindShowRendering(rendering, viewEnvironment, tag.displayRendering)
      }
      ?: error(
          "Expected $this to have a showRendering function to show $rendering. " +
              "Perhaps it was not built by a ${ViewRegistry::class.java.simpleName}, " +
              "or perhaps its ${ViewFactory::class.java.simpleName} did not call" +
              "View.bindShowRendering."
      )
}

/**
 * Returns the most recent rendering shown by this view, or null if [bindShowRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : Any> View.getRendering(): RenderingT? {
  // Can't use a val because of the parameter type.
  @Suppress("UNCHECKED_CAST")
  return when (val showing = showRenderingTag?.showing) {
    null -> null
    else -> showing as RenderingT
  }
}

@WorkflowUiExperimentalApi
private fun Any.matches(other: Any) = compatible(this, other)
