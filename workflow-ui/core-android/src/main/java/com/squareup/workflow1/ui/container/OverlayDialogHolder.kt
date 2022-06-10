package com.squareup.workflow1.ui.container

import android.app.Dialog
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.container.OverlayDialogHolder.Companion.InOverlay
import com.squareup.workflow1.ui.container.OverlayDialogHolder.Companion.NoOverlay
import com.squareup.workflow1.ui.show

/**
 * Associates a [dialog] with a function ([runner]) that can update it to display instances
 * of [ScreenT]. Also holds a reference to the [ViewEnvironment][environment] that was
 * most recently used to update the [dialog].
 */
@WorkflowUiExperimentalApi
public interface OverlayDialogHolder<in OverlayT : Overlay> {
  /** The [Dialog] managed by this holder, and updated via [runner] */
  public val dialog: Dialog

  /** The [ViewEnvironment] that was provided the last time [dialog] was updated by [runner]. */
  public val environment: ViewEnvironment

  /**
   * The function that is run by [show] to update [dialog] with a new [Screen] rendering and
   * [ViewEnvironment].
   */
  public val runner: OverlayDialogRunner<OverlayT>

  public companion object {
    /**
     * Default value returned for the [InOverlay] [ViewEnvironmentKey], and therefore the
     * default value returned by the [showing] method. Indicates that [show] has not yet
     * been called, during the window between a [OverlayDialogHolder] being instantiated,
     * and the first call to [show].
     */
    public object NoOverlay : Overlay

    /**
     * Provides access to the [OverlayScreen] instance most recently shown in an
     * [OverlayDialogHolder]'s [dialog] via [show]. Call [showing] for more convenient access.
     */
    public object InOverlay : ViewEnvironmentKey<Overlay>(Overlay::class) {
      override val default: Overlay = NoOverlay
    }
  }
}

/**
 * The function that updates a [Dialog] instance built by a [OverlayDialogFactory].
 * Each [OverlayDialogRunner] instance is paired with a single [Dialog] instance,
 * its neighbor in a [OverlayDialogHolder].
 *
 * This is the interface you'll implement directly to update Android dialog code
 * from your [Overlay] renderings. An [OverlayDialogRunner] serves as the strategy
 * object of an [OverlayDialogHolder] instantiated by a [OverlayDialogFactory] -- the
 * runner provides the implementation for the holder's [OverlayDialogHolder.show]
 * method.
 */
@WorkflowUiExperimentalApi
public fun interface OverlayDialogRunner<in OverlayT : Overlay> {
  public fun showRendering(
    rendering: OverlayT,
    viewEnvironment: ViewEnvironment
  )
}

/**
 * Returns true if [overlay] is [compatible] with the [Overlay] instance that
 * was last [shown][show] by the [dialog] managed by the receiver.
 */
@WorkflowUiExperimentalApi
public fun OverlayDialogHolder<*>.canShow(overlay: Overlay): Boolean {
  // The ShowingNothing case covers bootstrapping, during the first call to show()
  // from OverlayDialogFactory.start().
  return showing.let { it is NoOverlay || compatible(it, overlay) }
}

/**
 * Updates the [dialog][OverlayDialogHolder.dialog] managed by the receiver to
 * display [overlay], and updates the receiver's [environment] as well.
 * The new [environment] will hold a reference to [overlay] with key [InOverlay].
 */
@WorkflowUiExperimentalApi
public fun <OverlayT : Overlay> OverlayDialogHolder<OverlayT>.show(
  overlay: OverlayT,
  environment: ViewEnvironment
) {
  // Why is this an extension rather than part of the interface?
  // When wrapping, we need to prevent recursive calls from clobbering
  // `environment[InOverlay]` with the nested rendering type.
  runner.showRendering(overlay, environment + (InOverlay to overlay))
}

/**
 * Returns the [Overlay] most recently used to update the receiver's
 * [dialog][OverlayDialogHolder.dialog] via a call to [show].
 */
@WorkflowUiExperimentalApi
public val OverlayDialogHolder<*>.showing: Overlay
  get() = environment[InOverlay]

@WorkflowUiExperimentalApi
public fun <OverlayT : Overlay> OverlayDialogHolder(
  initialEnvironment: ViewEnvironment,
  dialog: Dialog,
  runner: OverlayDialogRunner<OverlayT>
): OverlayDialogHolder<OverlayT> {
  return RealOverlayDialogHolder(initialEnvironment, dialog, runner)
}
