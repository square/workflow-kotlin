package com.squareup.workflow1.ui.container

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.View
import androidx.lifecycle.LifecycleOwner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.container.DialogHolder.KeyAndBundle

/**
 * Does the bulk of the work of maintaining a set of [Dialog][android.app.Dialog]s
 * to reflect lists of [Overlay]. Can be used to create custom [Overlay]-based
 * layouts if [BodyAndModalsScreen] or the default [View] bound to it are too restrictive.
 * Provides a [LifecycleOwner] per managed dialog, and view persistence support.
 *
 * @param modal When true, only the top-most dialog is allowed to process touch and key events
 */
@WorkflowUiExperimentalApi
public class LayeredDialogs(
  private val context: Context,
  private val modal: Boolean,
  private val getParentLifecycleOwner: () -> LifecycleOwner?
) {
  /**
   * Builds a [LayeredDialogs] which looks through [view] to find its parent
   * [LifecycleOwner][getParentLifecycleOwner].
   *
   * @param modal When true, only the top-most dialog is allowed to process touch and key events
   */
  public constructor(
    view: View,
    modal: Boolean
  ) : this(view.context, modal, { WorkflowLifecycleOwner.get(view) })

  private var holders: List<DialogHolder<*>> = emptyList()

  /** True when any dialogs are visible, or becoming visible. */
  public val hasDialogs: Boolean = holders.isNotEmpty()

  /**
   * Updates the managed set of [Dialog][android.app.Dialog] instances to reflect
   * [overlays]. Opens new dialogs, updates existing ones, and dismisses those
   * that match no member of that list.
   *
   * Each dialog has its own [WorkflowLifecycleOwner], which starts when the dialog
   * is shown, and is destroyed when it is dismissed. Views nested in a managed dialog
   * can use [ViewTreeLifecycleOwner][androidx.lifecycle.ViewTreeLifecycleOwner] as
   * usual.
   */
  public fun update(
    overlays: List<Overlay>,
    viewEnvironment: ViewEnvironment
  ) {
    // Any nested back stacks have to provide saved state registries of their
    // own, but these things share a global namespace. To make that practical
    // we add uniquing strings to the ViewEnvironment for each dialog,
    // via withBackStackStateKeyPrefix.

    val overlayEnvironments = overlays.mapIndexed { index, _ ->
      viewEnvironment.withBackStackStateKeyPrefix("[${index + 1}]").let {
        // If we're in modal config, only the top dialog should accept events.
        if (modal && index < overlays.size - 1) it + (CoveredByModal to true) else it
      }
    }

    // On each update we build a new list of the running dialogs, both the
    // existing ones and any new ones. We need this so that we can compare
    // it with the previous list, and see what dialogs close.
    val newHolders = mutableListOf<DialogHolder<*>>()

    for ((i, overlay) in overlays.withIndex()) {
      val overlayEnvironment = overlayEnvironments[i]

      newHolders += if (i < holders.size && holders[i].canTakeRendering(overlay)) {
        // There is already a dialog at this index, and it is compatible
        // with the new Overlay at that index. Just update it.
        holders[i].also { it.takeRendering(overlay, overlayEnvironment) }
      } else {
        // We need a new dialog for this overlay. Time to build it.
        // We wrap our Dialog instances in DialogHolder to keep them
        // paired with their current overlay rendering and environment.
        // It would have been nice to keep those in tags on the Dialog's
        // decor view, more consistent with what ScreenViewFactory does,
        // but calling Window.getDecorView has side effects, and things
        // break if we call it to early. Need to store them somewhere else.
        overlay.toDialogFactory(overlayEnvironment).let { dialogFactory ->
          DialogHolder(
            overlay, overlayEnvironment, modal, context, dialogFactory
          ).also { newHolder ->
            // Has the side effect of creating the dialog if necessary.
            newHolder.takeRendering(overlay, overlayEnvironment)

            // Show the dialog. We use the container-provided LifecycleOwner
            // to host an androidx ViewTreeLifecycleOwner and SavedStateRegistryOwner
            // for each dialog. These are generally expected these days, and absolutely
            // required by Compose.
            newHolder.show(getParentLifecycleOwner())
          }
        }
      }
    }

    (holders - newHolders.toSet()).forEach { it.dismiss() }
    holders = newHolders
    // TODO Smarter diffing, and Z order. Maybe just hide and show everything on every update?
  }

  /** To be called from a container view's [View.onSaveInstanceState]. */
  public fun onSaveInstanceState(): SavedState {
    return SavedState(holders.mapNotNull { it.save() })
  }

  /** To be called from a container view's [View.onRestoreInstanceState]. */
  public fun onRestoreInstanceState(state: SavedState) {
    if (state.dialogBundles.size == holders.size) {
      state.dialogBundles.zip(holders) { viewState, holder -> holder.restore(viewState) }
    }
  }

  public class SavedState : Parcelable {
    internal val dialogBundles: List<KeyAndBundle>

    internal constructor(dialogBundles: List<KeyAndBundle>) {
      this.dialogBundles = dialogBundles
    }

    public constructor(source: Parcel) {
      dialogBundles = mutableListOf<KeyAndBundle>().apply {
        source.readTypedList(this, KeyAndBundle)
      }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      out.writeTypedList(dialogBundles)
    }

    public companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState =
        SavedState(source)

      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }
}
