package com.squareup.workflow1.ui

import android.app.Dialog
import android.view.View

/**
 * Function attached to [View] and [Dialog] instances created by [ViewRegistry], to allow them
 * to respond to [View.showRendering] or [Dialog.showRendering].
 */
@WorkflowUiExperimentalApi
typealias ShowRendering<RenderingT> = (@UnsafeVariance RenderingT, ViewEnvironment) -> Unit

@WorkflowUiExperimentalApi
@Suppress("unused")
@Deprecated(
    "Use ShowRendering.",
    ReplaceWith("ShowRendering<RenderingT>", "com.squareup.workflow1.ui.ShowRendering")
)
typealias ViewShowRendering<RenderingT> = ShowRendering<RenderingT>

/**
 * View tag that holds the function to show instances of [RenderingT], and
 * the [current rendering][showing].
 *
 * @param showing the current rendering. Used by [canShowRendering] to decide if the
 * view can be updated with the next rendering.
 */
@WorkflowUiExperimentalApi
data class ShowRenderingTag<out RenderingT : Any>(
  val showing: RenderingT,
  val environment: ViewEnvironment,
  val showRendering: ShowRendering<RenderingT>
)

/**
 * Establishes [showRendering] as the implementation of [View.showRendering]
 * for the receiver, possibly replacing the existing one. Immediately invokes [showRendering]
 * to display [initialRendering].
 *
 * Intended for use by implementations of [ViewFactory.buildView].
 */
@WorkflowUiExperimentalApi
fun <RenderingT : Any> View.bindShowRendering(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  showRendering: ShowRendering<RenderingT>
) {
  setTag(
      R.id.view_show_rendering_function,
      ShowRenderingTag(initialRendering, initialViewEnvironment, showRendering)
  )
  showRendering.invoke(initialRendering, initialViewEnvironment)
}

/**
 * Establishes [showRendering] as the implementation of [Dialog.showRendering]
 * for the receiver, possibly replacing the existing one. Immediately invokes [showRendering]
 * to display [initialRendering].
 *
 * Intended for use by implementations of [DialogFactory.buildDialog].
 */
@WorkflowUiExperimentalApi
fun <RenderingT : Any> Dialog.bindShowRendering(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  showRendering: ShowRendering<RenderingT>
) {
  window!!.decorView.bindShowRendering(initialRendering, initialViewEnvironment, showRendering)
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * True if this [View] is able to show [rendering].
 *
 * Returns `false` if [View.bindShowRendering] has not been called, so it is always safe to
 * call this method. Otherwise returns the [compatibility][compatible] of the initial
 * [rendering] and the new one.
 */
@WorkflowUiExperimentalApi
fun View.canShowRendering(rendering: Any): Boolean {
  return getRendering<Any>()?.matches(rendering) == true
}

/**
 * True if this [Dialog] is able to show [rendering].
 *
 * Returns `false` if [Dialog.bindShowRendering] has not been called, so it is always safe to
 * call this method. Otherwise returns the [compatibility][compatible] of the initial
 * [rendering] and the new one.
 */
@WorkflowUiExperimentalApi
fun Dialog.canShowRendering(rendering: Any): Boolean {
  return getRendering<Any>()?.matches(rendering) == true
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Sets the workflow rendering associated with this [View], and displays it by
 * invoking the [ShowRendering] function previously set by [View.bindShowRendering].
 *
 * @throws IllegalStateException if [View.bindShowRendering] has not been called.
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

        bindShowRendering(rendering, viewEnvironment, tag.showRendering)
      }
      ?: error(
          "Expected $this to have a showRendering function to show $rendering. " +
              "Perhaps it was not built by a ${ViewFactory::class.java.simpleName}, " +
              "or perhaps its ${ViewFactory::class.java.simpleName} did not call" +
              "View.bindShowRendering."
      )
}

/**
 * Sets the workflow rendering associated with this [Dialog], and displays it by
 * invoking the [ShowRendering] function previously set by [Dialog.bindShowRendering].
 *
 * @throws IllegalStateException if [Dialog.bindShowRendering] has not been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : Any> Dialog.showRendering(
  rendering: RenderingT,
  viewEnvironment: ViewEnvironment
) {
  window!!.decorView.showRenderingTag
      ?.let { tag ->
        check(tag.showing.matches(rendering)) {
          "Expected $this to be able to show rendering $rendering, but that did not match " +
              "previous rendering ${tag.showing}. "
        }

        bindShowRendering(rendering, viewEnvironment, tag.showRendering)
      }
      ?: error(
          "Expected $this to have a showRendering function to show $rendering. " +
              "Perhaps it was not built by a ${DialogFactory::class.java.simpleName}, " +
              "or perhaps its ${DialogFactory::class.java.simpleName} did not call" +
              "Dialog.bindShowRendering."
      )
}

/**
 * Returns the most recent rendering shown by this [View], or null if [View.bindShowRendering]
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


/**
 * Returns the most recent rendering shown by this [Dialog], or null if [Dialog.bindShowRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : Any> Dialog.getRendering(): RenderingT? {
  return window?.decorView?.getRendering()
}

/**
 * Returns the most recent [ViewEnvironment] applied to this [View], or null if
 * [View.bindShowRendering] has never been called.
 */
@WorkflowUiExperimentalApi
val View.environment: ViewEnvironment?
  get() = showRenderingTag?.environment

/**
 * Returns the most recent [ViewEnvironment] applied to this [Dialog], or null if
 * [View.bindShowRendering] has never been called.
 */
@WorkflowUiExperimentalApi
val Dialog.environment: ViewEnvironment?
  get() = showRenderingTag?.environment

/**
 * Returns the function set by the most recent call to [View.bindShowRendering], or null
 * if that method has never been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : Any> View.getShowRendering(): ShowRendering<RenderingT>? {
  return showRenderingTag?.showRendering
}

/**
 * Returns the function set by the most recent call to [Dialog.bindShowRendering], or null
 * if that method has never been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : Any> Dialog.getShowRendering(): ShowRendering<RenderingT>? {
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

/**
 * Returns the [ShowRenderingTag] established by the last call to [Dialog.bindShowRendering],
 * or null if that method has never been called.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal val Dialog.showRenderingTag: ShowRenderingTag<*>?
  get() = window?.decorView?.showRenderingTag

@WorkflowUiExperimentalApi
private fun Any.matches(other: Any) = compatible(this, other)
