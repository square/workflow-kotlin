package com.squareup.workflow1.ui

import android.view.View

/**
 * Associates a [view] with a function ([runner]) that can update it to display instances
 * of [ScreenT]. Also holds a reference to the [ViewEnvironment][environment] that was
 * most recently used to update the [view].
 *
 * Do not call [runner] directly. Use [ScreenViewHolder.show] instead. Or most commonly,
 * allow [WorkflowViewStub.show] to call it for you.
 */
@WorkflowUiExperimentalApi
public interface ScreenViewHolder<in ScreenT : Screen> {
  /** The [View] managed by this holder, and updated via [runner] */
  public val view: View

  /** The [ViewEnvironment] that was provided the last time [view] was updated by [runner]. */
  public val environment: ViewEnvironment

  /**
   * The function that is run by [show] to update [view] with a new [Screen] rendering and
   * [ViewEnvironment].
   *
   * Prefer calling [show] to using this directly, to ensure that [screenOrNull] is
   * maintained correctly, and [showing] keeps working.
   */
  public val runner: ScreenViewRunner<ScreenT>
}

/**
 * The function that updates a [View] instance built by a [ScreenViewFactory].
 * Each [ScreenViewRunner] instance is paired with a single [View] instance,
 * its neighbor in a [ScreenViewHolder].
 *
 * This is the interface you'll implement directly to update Android view code
 * from your [Screen] renderings. A [ScreenViewRunner] serves as the strategy
 * object of a [ScreenViewHolder] instantiated by a [ScreenViewFactory] -- the
 * runner provides the implementation for the holder's [ScreenViewHolder.show]
 * method.
 */
@WorkflowUiExperimentalApi
public fun interface ScreenViewRunner<in ScreenT : Screen> {
  public fun showRendering(
    rendering: ScreenT,
    environment: ViewEnvironment
  )
}

/**
 * Returns true if [screen] is [compatible] with the [Screen] instance that
 * was last [shown][show] by the [view][ScreenViewHolder.view] managed by the receiver.
 */
@WorkflowUiExperimentalApi
public fun ScreenViewHolder<*>.canShow(screen: Screen): Boolean {
  // The null case covers bootstrapping, during the first call to show()
  // from ScreenViewFactory.start().
  return view.screenOrNull?.let { compatible(it, screen) } ?: true
}

/**
 * Updates the [view][ScreenViewHolder.view] managed by the receiver to
 * display [screen], and updates the receiver's [environment] as well.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder<ScreenT>.show(
  screen: ScreenT,
  environment: ViewEnvironment
) {
  // Why is this an extension rather than part of the interface?
  // When wrapping, we need to prevent recursive calls from clobbering
  // `screenOrNull` with the nested rendering type.
  view.screen = screen
  runner.showRendering(screen, environment)
}

/**
 * Returns the [Screen] most recently used to update the receiver's [view][ScreenViewHolder.view]
 * via a call to [show].
 *
 * Note that the exact type of the returned [Screen] is likely not to match that of
 * the receiver's `ScreenT` type parameter, e.g. if a
 * [wrapping view factory][ScreenViewFactory.forWrapper] is in use.
 */
@WorkflowUiExperimentalApi
public val ScreenViewHolder<*>.showing: Screen
  get() = view.screen

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder(
  initialEnvironment: ViewEnvironment,
  view: View,
  viewRunner: ScreenViewRunner<ScreenT>
): ScreenViewHolder<ScreenT> {
  return RealScreenViewHolder(initialEnvironment, view, viewRunner)
}
