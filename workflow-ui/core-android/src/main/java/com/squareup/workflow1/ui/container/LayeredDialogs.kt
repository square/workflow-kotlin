package com.squareup.workflow1.ui.container

import android.content.Context
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.MotionEvent
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator
import com.squareup.workflow1.ui.container.DialogHolder.KeyAndBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Does the bulk of the work of maintaining a set of [Dialog][android.app.Dialog]s
 * to reflect lists of [Overlay]. Can be used to create custom [Overlay]-based
 * layouts if [BodyAndOverlaysScreen] or the default [View] bound to it are too restrictive.
 * Provides a [LifecycleOwner] per managed dialog, and view persistence support.
 *
 * @param bounds made available to managed dialogs via the [OverlayArea]
 * [ViewEnvironmentKey][com.squareup.workflow1.ui.ViewEnvironmentKey],
 * which drives [ScreenOverlayDialogFactory.updateBounds].
 *
 * @param cancelEvents function to be called when a modal session starts -- that is,
 * when [update] is first called with a [ModalOverlay] member, or called again with
 * one after calls with none.
 *
 * @param getParentLifecycleOwner provides the [LifecycleOwner] to serve as
 * an ancestor to those created for managed [Dialog][android.app.Dialog]s.
 *
 */
@WorkflowUiExperimentalApi
public class LayeredDialogs private constructor(
  private val context: Context,
  private val bounds: StateFlow<Rect>,
  private val cancelEvents: () -> Unit,
  private val getParentLifecycleOwner: () -> LifecycleOwner
) {
  /**
   * Provides a new `ViewTreeSavedStateRegistryOwner` for each dialog,
   * which will save to the `ViewTreeSavedStateRegistryOwner` of this container view.
   */
  private val stateRegistryAggregator = WorkflowSavedStateRegistryAggregator()

  private var holders: List<DialogHolder<*>> = emptyList()

  public var allowEvents: Boolean = true
    private set(value) {
      val was = field
      field = value
      if (value != was) cancelEvents()
    }

  /**
   * Updates the managed set of [Dialog][android.app.Dialog] instances to reflect
   * [overlays]. Opens new dialogs, updates existing ones, and dismisses those
   * that match no member of that list.
   *
   * Each dialog has its own [WorkflowLifecycleOwner], which starts when the dialog
   * is shown, and is destroyed when it is dismissed. Views nested in a managed dialog
   * can use [ViewTreeLifecycleOwner][androidx.lifecycle.ViewTreeLifecycleOwner] as
   * usual.
   *
   * @param updateBase function to be called before updating the dialogs.
   * The [ViewEnvironment] passed to that function is enhanced with a [CoveredByModal]
   * as appropriate, to ensure proper behavior of [allowEvents] of nested containers.
   */
  public fun update(
    overlays: List<Overlay>,
    viewEnvironment: ViewEnvironment,
    updateBase: (environment: ViewEnvironment) -> Unit
  ) {
    val modalIndex = overlays.indexOfFirst { it is ModalOverlay }
    val showingModals = modalIndex > -1

    allowEvents = !showingModals

    updateBase(
      if (showingModals) viewEnvironment + (CoveredByModal to true) else viewEnvironment
    )

    val envPlusBounds = viewEnvironment + OverlayArea(bounds)

    // On each update we build a new list of the running dialogs, both the
    // existing ones and any new ones. We need this so that we can compare
    // it with the previous list, and see what dialogs close.
    val newHolders = mutableListOf<DialogHolder<*>>()

    for ((i, overlay) in overlays.withIndex()) {
      val dialogEnv =
        if (i < modalIndex) envPlusBounds + (CoveredByModal to true) else envPlusBounds

      newHolders += if (i < holders.size && holders[i].canTakeRendering(overlay)) {
        // There is already a dialog at this index, and it is compatible
        // with the new Overlay at that index. Just update it.
        holders[i].also { it.takeRendering(overlay, dialogEnv) }
      } else {
        // We need a new dialog for this overlay. Time to build it.
        // We wrap our Dialog instances in DialogHolder to keep them
        // paired with their current overlay rendering and environment.
        // It would have been nice to keep those in tags on the Dialog's
        // decor view, more consistent with what ScreenViewFactory does,
        // but calling Window.getDecorView has side effects, and things
        // break if we call it to early. Need to store them somewhere else.
        overlay.toDialogFactory(dialogEnv).let { dialogFactory ->
          DialogHolder(overlay, dialogEnv, i, context, dialogFactory).also { newHolder ->
            newHolder.takeRendering(overlay, dialogEnv)

            // Show the dialog, creating it if necessary.
            newHolder.show(getParentLifecycleOwner(), stateRegistryAggregator)
          }
        }
      }
    }

    (holders - newHolders.toSet()).forEach { it.dismiss() }
    // Drop the state registries for any keys that no longer exist since the last save.
    // Or really, drop everything except the remaining ones.
    stateRegistryAggregator.pruneAllChildRegistryOwnersExcept(
      keysToKeep = newHolders.map { it.savedStateRegistryKey }
    )
    holders = newHolders
    // TODO Smarter diffing, and Z order. Maybe just hide and show everything on every update?
  }

  /**
   * Must be called whenever the owning view is [attached to a window][View.onAttachedToWindow].
   * Must eventually be matched with a call to [onDetachedFromWindow].
   *
   * @param savedStateParentKey Unique identifier for this view for SavedStateRegistry purposes.
   * Typically based on the [Compatible.keyFor] the current rendering. Taking this approach
   * allows feature developers to take control over naming, e.g. by wrapping renderings
   * with [NamedScreen][com.squareup.workflow1.ui.NamedScreen].
   */
  public fun onAttachedToWindow(
    savedStateParentKey: String,
    view: View
  ) {
    stateRegistryAggregator.attachToParentRegistry(
      savedStateParentKey,
      WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext(view)
    )
  }

  /**
   * Must be called whenever the owning view is
   * [detached from a window][View.onDetachedFromWindow].
   * Must be matched with a call to [onAttachedToWindow].
   */
  public fun onDetachedFromWindow() {
    stateRegistryAggregator.detachFromParentRegistry()
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

  public companion object {
    /**
     * Creates a [LayeredDialogs] instance based on the given [view], which will
     * serve as the source for a [LifecycleOwner], and whose bounds will be reported
     * via [OverlayArea].
     *
     * - The [view]'s [dispatchTouchEvent][View.dispatchTouchEvent] and
     *   [dispatchKeyEvent][View.dispatchKeyEvent] methods should be overridden
     *   to honor [LayeredDialogs.allowEvents].
     *
     * - The [view]'s [onAttachedToWindow][View.onAttachedToWindow] and
     *   [onDetachedFromWindow][View.onDetachedFromWindow] methods must call
     *   through to the like named methods of the returned [LayeredDialogs]
     *   ([onAttachedToWindow], [onDetachedFromWindow]).
     *
     * - The [view]'s [onSaveInstanceState][View.onSaveInstanceState] and
     *   [onRestoreInstanceState][View.onRestoreInstanceState] methods must call
     *   through to the like named methods of the returned [LayeredDialogs]
     *   ([onSaveInstanceState], [onRestoreInstanceState]).
     */
    public fun forView(
      view: View,
      superDispatchTouchEvent: (MotionEvent) -> Unit
    ): LayeredDialogs {
      val boundsRect = Rect()
      if (view.isAttachedToWindow) view.getGlobalVisibleRect(boundsRect)
      val bounds = MutableStateFlow(Rect(boundsRect))

      return LayeredDialogs(
        context = view.context,
        bounds = bounds,
        cancelEvents = {
          // Note similar code in DialogHolder.

          // https://stackoverflow.com/questions/2886407/dealing-with-rapid-tapping-on-buttons
          // If any motion events were enqueued on the main thread, cancel them.
          dispatchCancelEvent { superDispatchTouchEvent(it) }
          // When we cancel, have to warn things like RecyclerView that handle streams
          // of motion events and eventually dispatch input events (click, key pressed, etc.)
          // based on them.
          view.cancelPendingInputEvents()
        }
      ) {
        checkNotNull(ViewTreeLifecycleOwner.get(view)) {
          "Expected a ViewTreeLifecycleOwner on $view"
        }
      }.also { dialogs ->

        val boundsListener = OnGlobalLayoutListener {
          if (view.getGlobalVisibleRect(boundsRect) && boundsRect != bounds.value) {
            bounds.value = Rect(boundsRect)
          }
          // Should we close the dialogs if getGlobalVisibleRect returns false?
          // https://github.com/square/workflow-kotlin/issues/599
        }

        val attachStateChangeListener = object : OnAttachStateChangeListener {
          override fun onViewAttachedToWindow(v: View) {
            boundsListener.onGlobalLayout()
            v.viewTreeObserver.addOnGlobalLayoutListener(boundsListener)
          }

          override fun onViewDetachedFromWindow(v: View) {
            // Don't leak the dialogs if we're suddenly yanked out of view.
            // https://github.com/square/workflow-kotlin/issues/314
            dialogs.update(emptyList(), ViewEnvironment.EMPTY) {}
            v.viewTreeObserver.removeOnGlobalLayoutListener(boundsListener)
            bounds.value = Rect()
          }
        }

        view.addOnAttachStateChangeListener(attachStateChangeListener)
      }
    }
  }
}
