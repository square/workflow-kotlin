package com.squareup.workflow1.ui

import android.app.Dialog
import android.view.View

/**
 * Function attached to [View] and [Dialog] instances created by [ViewRegistry], to allow them
 * to respond to [View.showRendering] or [Dialog.display].
 */
@WorkflowUiExperimentalApi
typealias DisplayRendering<RenderingT> = (@UnsafeVariance RenderingT, ViewEnvironment) -> Unit

/**
 * View tag that holds the function to show instances of [RenderingT], and
 * the [current rendering][showing].
 *
 * @param showing the current rendering. Used by [canDisplay] to decide if the
 * view can be updated with the next rendering.
 */
@WorkflowUiExperimentalApi
data class ShowRenderingTag<out RenderingT : Any>(
  val showing: RenderingT,
  val environment: ViewEnvironment,
  val displayRendering: DisplayRendering<RenderingT>
)

/**
 * Establishes [displayRendering] as the implementation of [View.showRendering]
 * for the receiver, possibly replacing the existing one. Immediately invokes [displayRendering]
 * to display [initialRendering].
 *
 * Intended for use by implementations of [ViewBuilder.buildView].
 */
@WorkflowUiExperimentalApi
fun <RenderingT : ViewRendering> View.bindDisplayFunction(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  displayRendering: DisplayRendering<RenderingT>
) {
  setTag(
      R.id.view_show_rendering_function,
      ShowRenderingTag(initialRendering, initialViewEnvironment, displayRendering)
  )
  displayRendering.invoke(initialRendering, initialViewEnvironment)
}

/**
 * Establishes [displayRendering] as the implementation of [Dialog.display]
 * for the receiver, possibly replacing the existing one. Immediately invokes [displayRendering]
 * to display [initialRendering].
 *
 * Intended for use by implementations of [DialogBuilder.buildDialog].
 */
@WorkflowUiExperimentalApi
fun <RenderingT : ModalRendering> Dialog.bindDisplayFunction(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  displayRendering: DisplayRendering<RenderingT>
) {
  window!!.decorView.setTag(
      R.id.view_show_rendering_function,
      ShowRenderingTag(initialRendering, initialViewEnvironment, displayRendering)
  )
  displayRendering.invoke(initialRendering, initialViewEnvironment)
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
fun View.canDisplay(rendering: ViewRendering): Boolean {
  return getDisplaying<ViewRendering>()?.matches(rendering) == true
}

/**
 * True if this [Dialog] is able to display [rendering].
 *
 * Returns `false` if [Dialog.bindDisplayFunction] has not been called, so it is always safe to
 * call this method. Otherwise returns the [compatibility][compatible] of the initial
 * [rendering] and the new one.
 */
@WorkflowUiExperimentalApi
fun Dialog.canDisplay(rendering: ModalRendering): Boolean {
  return getDisplaying<ModalRendering>()?.matches(rendering) == true
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Sets the workflow rendering associated with this [View], and displays it by
 * invoking the [DisplayRendering] function previously set by [View.bindShowRendering].
 *
 * @throws IllegalStateException if [View.bindShowRendering] has not been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : ViewRendering> View.display(
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
              "Perhaps it was not built by a ${ViewBuilder::class.java.simpleName}, " +
              "or perhaps its ${ViewBuilder::class.java.simpleName} did not call" +
              "View.bindShowRendering."
      )
}

/**
 * Sets the workflow rendering associated with this [Dialog], and displays it by
 * invoking the [DisplayRendering] function previously set by [Dialog.bindDisplayFunction].
 *
 * @throws IllegalStateException if [Dialog.bindDisplayFunction] has not been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : ModalRendering> Dialog.display(
  rendering: RenderingT,
  viewEnvironment: ViewEnvironment
) {
  window!!.decorView.showRenderingTag
      ?.let { tag ->
        check(tag.showing.matches(rendering)) {
          "Expected $this to be able to display rendering $rendering, but that did not match " +
              "previous rendering ${tag.showing}. "
        }

        bindDisplayFunction(rendering, viewEnvironment, tag.displayRendering)
      }
      ?: error(
          "Expected $this to have a showRendering function to display $rendering. " +
              "Perhaps it was not built by a ${DialogBuilder::class.java.simpleName}, " +
              "or perhaps its ${DialogBuilder::class.java.simpleName} did not call" +
              "Dialog.bindShowRendering."
      )
}

/**
 * Returns the most recent rendering displayed by this [View], or null if [View.bindShowRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : ViewRendering> View.getDisplaying(): RenderingT? {
  // Can't use a val because of the parameter type.
  @Suppress("UNCHECKED_CAST")
  return when (val displaying = showRenderingTag?.showing) {
    null -> null
    else -> displaying as RenderingT
  }
}

/**
 * Returns the most recent rendering displayed by this [Dialog], or null if [Dialog.bindDisplayFunction]
 * has never been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : ModalRendering> Dialog.getDisplaying(): RenderingT? {
  // Can't use a val because of the parameter type.
  @Suppress("UNCHECKED_CAST")
  return when (val displaying = showRenderingTag?.showing) {
    null -> null
    else -> displaying as RenderingT
  }
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
fun <RenderingT : Any> View.getShowRendering(): DisplayRendering<RenderingT>? {
  return showRenderingTag?.displayRendering
}

/**
 * Returns the function set by the most recent call to [Dialog.bindDisplayFunction], or null
 * if that method has never been called.
 */
@WorkflowUiExperimentalApi
fun <RenderingT : Any> Dialog.getShowRendering(): DisplayRendering<RenderingT>? {
  return showRenderingTag?.displayRendering
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
 * Returns the [ShowRenderingTag] established by the last call to [Dialog.bindDisplayFunction],
 * or null if that method has never been called.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal val Dialog.showRenderingTag: ShowRenderingTag<*>?
  get() = window?.decorView?.showRenderingTag

@WorkflowUiExperimentalApi
private fun Any.matches(other: Any) = compatible(this, other)
