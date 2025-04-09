package com.squareup.workflow1.ui.navigation

import android.app.Dialog
import android.graphics.Rect
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.show

/**
 * Associates a [dialog] with a function ([runner]) that can update it to display instances
 * of [OverlayT]. Also holds a reference to the [ViewEnvironment][environment] that was
 * most recently used to update the [dialog].
 */
public interface OverlayDialogHolder<in OverlayT : Overlay> {
  /** The [Dialog] managed by this holder, and updated via [runner] */
  public val dialog: Dialog

  /** The [ViewEnvironment] that was provided the last time [dialog] was updated by [runner]. */
  public val environment: ViewEnvironment

  /**
   * The function that is run by [show] to update [dialog] with a new [Overlay] rendering and
   * [ViewEnvironment].
   *
   * Prefer calling [show] to using this directly, to ensure that [overlayOrNull] is
   * maintained correctly, and [showing] keeps working.  Or most commonly,
   * allow `WorkflowViewStub.show` to call it for you.
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
    public operator fun <OverlayT : Overlay> invoke(
      initialEnvironment: ViewEnvironment,
      dialog: Dialog,
      onUpdateBounds: ((Rect) -> Unit)? = { dialog.setBounds(it) },
      runner: (rendering: OverlayT, environment: ViewEnvironment) -> Unit
    ): OverlayDialogHolder<OverlayT> {
      return RealOverlayDialogHolder(
        initialEnvironment,
        dialog,
        onUpdateBounds,
        runner
      )
    }
  }
}

/**
 * Returns true if [overlay] is [compatible] with the [Overlay] instance that
 * was last [shown][show] by the [dialog][OverlayDialogHolder.dialog] managed by the receiver.
 */
public fun OverlayDialogHolder<*>.canShow(overlay: Overlay): Boolean {
  // The null case covers bootstrapping, during the first call to show().
  return dialog.overlayOrNull?.let { compatible(it, overlay) } ?: true
}

/**
 * Updates the [dialog][OverlayDialogHolder.dialog] managed by the receiver to
 * display [overlay], and updates the receiver's [environment] as well.
 */
public fun <OverlayT : Overlay> OverlayDialogHolder<OverlayT>.show(
  overlay: OverlayT,
  environment: ViewEnvironment
) {
  // Why is this an extension rather than part of the interface?
  // When wrapping, we need to prevent recursive calls from clobbering
  // `overlayOrNull` with the nested rendering type.

  if (dialog.decorViewOrNull != null) {
    // https://github.com/square/workflow-kotlin/issues/863
    // This writes to the decor view, which is not safe to do before the
    // dialog has been shown, because why would it be? (Note that DialogSession
    // primes the pump for us, setting the first `overlay` value right
    // after the first call to Dialog.show.)
    dialog.overlay = overlay
  }
  runner(overlay, environment)
}

/**
 * Returns the [Overlay] most recently used to update the receiver's
 * [dialog][OverlayDialogHolder.dialog] via a call to [show].
 *
 * Note that the exact type of the returned [Overlay] is likely not to match that of
 * the receiver's `OverlayT` type parameter, e.g. if a wrapping dialog factory is in use.
 */
public val OverlayDialogHolder<*>.showing: Overlay
  get() = dialog.overlay
