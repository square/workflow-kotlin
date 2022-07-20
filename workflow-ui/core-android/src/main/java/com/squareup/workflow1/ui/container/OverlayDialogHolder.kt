package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.graphics.Rect
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
 * of [OverlayT]. Also holds a reference to the [ViewEnvironment][environment] that was
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
  public val runner: (rendering: OverlayT, environment: ViewEnvironment) -> Unit

  /**
   * Optional function called to report the bounds of the managing container view,
   * as reported by [OverlayArea]. Well behaved [Overlay] dialogs are expected to
   * be restricted to those bounds, to the extent practical -- you probably want to ignore
   * this for AlertDialog, e.g.
   *
   * Honoring this contract makes it easy to define areas of the display
   * that are outside of the "shadow" of a modal dialog. Imagine an app
   * with a status bar that should not be covered by modals.
   *
   * Default implementation provided by the factory function below calls [Dialog.setBounds].
   */
  public val onUpdateBounds: ((Rect) -> Unit)?

  public companion object {
    /**
     * Default value returned for the [InOverlay] [ViewEnvironmentKey], and therefore the
     * default value returned by the [showing] method. Indicates that [show] has not yet
     * been called, during the window between a [OverlayDialogHolder] being instantiated,
     * and the first call to [show].
     */
    public object NoOverlay : Overlay

    /**
     * Provides access to the [Overlay] instance most recently shown in an
     * [OverlayDialogHolder]'s [dialog] via [show]. Call [showing] for more convenient access.
     */
    public object InOverlay : ViewEnvironmentKey<Overlay>(Overlay::class) {
      override val default: Overlay = NoOverlay
    }
  }
}

/**
 * Returns true if [overlay] is [compatible] with the [Overlay] instance that
 * was last [shown][show] by the [dialog][OverlayDialogHolder.dialog] managed by the receiver.
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
  runner(overlay, environment + (InOverlay to overlay))
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
  onUpdateBounds: ((Rect) -> Unit)? = { dialog.setBounds(it) },
  runner: (rendering: OverlayT, environment: ViewEnvironment) -> Unit
): OverlayDialogHolder<OverlayT> {
  return RealOverlayDialogHolder(initialEnvironment, dialog, onUpdateBounds, runner)
}
