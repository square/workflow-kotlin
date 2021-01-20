package com.squareup.workflow1.ui

import android.view.View

/**
 * A single-method interface that can be passed to [buildView] to run logic after the [ViewFactory]
 * creates the view, but before it's [ViewShowRendering] is invoked for the first time.
 *
 * See the documentation on [onViewCreated] for more information about how to implement this
 * interface.
 */
@WorkflowUiExperimentalApi
public fun interface ViewInitializer {
  /**
   * Called by [bindShowRendering] the first time it's called for a particular [View], inside a
   * [ViewFactory.buildView] method. This function will run before the [ViewShowRendering], which
   * gives you the chance to initialize any properties of the view that need to be set before the
   * first [ViewShowRendering] call (e.g. [LayoutRunner.showRendering]).
   *
   * The [ShowRenderingTag] tag is set _before_ calling this function, so this function can use
   * [showRenderingTag], [getShowRendering], [getRendering], etc.
   *
   * This function must invoke the ViewFactory's initial [`showRendering`][ViewShowRendering] logic,
   * and it can do so in a number of ways:
   *
   *  - Call [getShowRendering] to get the [ViewShowRendering] and pass it the initial rendering
   *    and [ViewEnvironment] yourself. Eg.:
   *    ```
   *    view.getShowRendering()!!.showRendering(rendering, viewEnvironment)
   *    ```
   *  - Call [bindShowRendering], and pass it a [ViewShowRendering] that in turn invokes either of
   *    the above two functions. Eg.:
   *    ```
   *    val innerShowRendering = view.getShowRendering()!!
   *    view.bindShowRendering(initialRendering, initialViewEnvironment) { rendering, environment ->
   *      innerShowRendering(rendering, environment)
   *    }
   *    ```
   *
   * If none of these are done, or if the initial [ViewShowRendering] is invoked more than once,
   * an [IllegalStateException] will be thrown.
   *
   * @param view The [View] that was just created by the [ViewFactory].
   */
  public fun onViewCreated(view: View)
}

/**
 * A static [ViewInitializer] that acts as a sentinel meaning no [ViewInitializer] was specified.
 *
 * Its [onViewCreated] throws an exception, so code that needs to call [onViewCreated] should first
 * check if the instance is this object, and skip the call if so.
 */
@OptIn(WorkflowUiExperimentalApi::class)
public object NoopViewInitializer : ViewInitializer {
  public override fun onViewCreated(view: View) {
    throw UnsupportedOperationException("$this.onViewCreated should never be invoked.")
  }

  override fun toString(): String = javaClass.simpleName
}

@OptIn(WorkflowUiExperimentalApi::class)
internal object ViewInitializerKey :
  ViewEnvironmentKey<ViewInitializer>(ViewInitializer::class) {
  override val default: ViewInitializer get() = NoopViewInitializer
}
