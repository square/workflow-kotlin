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
import com.squareup.workflow1.ui.container.DialogSession.KeyAndBundle
import com.squareup.workflow1.ui.container.OverlayDialogHolder.Companion.InOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Does the bulk of the work of maintaining a set of [Dialog][android.app.Dialog]s
 * to reflect lists of [Overlay]. Can be used to create custom [Overlay]-based
 * layouts if [BodyAndOverlaysScreen] or the default [BodyAndOverlaysContainer] bound
 * to it are too restrictive.
 *
 * - Provides an [allowEvents] field that reflects the presence or absence of Dialogs driven
 *   by [ModalOverlay]
 *
 * - Makes [OverlayArea] available in the [ViewEnvironment],
 *   and uses it to drive calls to [OverlayDialogHolder.onUpdateBounds].
 *
 * - Provides a [ViewTreeLifecycleOwner] per managed Dialog, and view persistence support,
 *   both for classic [View.onSaveInstanceState] and
 *   Jetpack [SavedStateRegistry][androidx.savedstate.SavedStateRegistry].
 *
 * ## Lifecycle of a managed [Dialog][android.app.Dialog]
 *
 * When [update] is called with an [Overlay] that is not [Compatible] with an
 * existing Dialog at the same index, the appropriate [OverlayDialogFactory] instance
 * is fetched from the [ViewEnvironment]. That factory builds (but does not
 * [show][android.app.Dialog.show]) a new Dialog, wrapped in an [OverlayDialogHolder].
 * The holder in turn is held by a [DialogSession] instance. There is a 1:1:1 relationship
 * between the Dialog, the [OverlayDialogHolder] which can [update it][OverlayDialogHolder.show],
 * and the [DialogSession] that manages its [LifecycleOwner] and persistence.
 *
 * When a new [DialogSession] begins:
 *
 * - a [ViewTreeLifecycleOwner] is created as a child to the one provided
 *   by [getParentLifecycleOwner]
 *
 * - An appropriately scoped [SavedStateRegistry][androidx.savedstate.SavedStateRegistry]
 *   is put in place, provided that [onAttachedToWindow] and [onDetachedFromWindow] are
 *   called from the like named methods of the nearest container View
 *
 * - Any available classic View state is restored to the new Dialog's content View tree,
 *   provided that [onSaveInstanceState] and [onRestoreInstanceState] are called from the
 *   like named methods of that container View
 *
 * - And the Dialog is [shown][android.app.Dialog.show]
 *
 * The [DialogSession] is maintained (and its two flavors of View state are recorded
 * as prompted by the Android runtime) so long as [update] is called with a
 * [Compatible] [Overlay] rendering in the same position.
 *
 * When [update] is called without a matching [Overlay], or the
 * [parent Lifecycle][getParentLifecycleOwner] ends, the [DialogSession] ends,
 * its [ViewTreeLifecycleOwner] is destroyed, and the Dialog is
 * [dismissed][android.app.Dialog.dismiss].
 *
 * @param bounds made available to managed dialogs via the [OverlayArea]
 * [ViewEnvironmentKey][com.squareup.workflow1.ui.ViewEnvironmentKey],
 * which drives [OverlayDialogHolder.onUpdateBounds].
 *
 * @param cancelEvents function to be called when a modal session starts -- that is,
 * when [update] is first called with a [ModalOverlay] member, or called again with
 * one after calls with none.
 *
 * @param getParentLifecycleOwner provides the [LifecycleOwner] to serve as
 * an ancestor to those created for managed [Dialog][android.app.Dialog]s.
 */
@WorkflowUiExperimentalApi
public class LayeredDialogSessions private constructor(
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

  private var sessions: List<DialogSession> = emptyList()

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
    val updatedSessions = mutableListOf<DialogSession>()

    for ((i, overlay) in overlays.withIndex()) {
      val covered = i < modalIndex
      // Seed InOverlay before the Dialog is created, so that it's available to
      // DialogSession before the first call to OverlayDialogHolder.show (which is
      // what normally sets it).
      // https://github.com/square/workflow-kotlin/issues/825
      val envPlusInOverlay = envPlusBounds + (InOverlay to overlay)
      val dialogEnv =
        if (covered) envPlusInOverlay + (CoveredByModal to true) else envPlusInOverlay

      updatedSessions += if (i < sessions.size && sessions[i].holder.canShow(overlay)) {
        // There is already a dialog at this index, and it is compatible
        // with the new Overlay at that index. Just update it.
        sessions[i].also { it.holder.show(overlay, dialogEnv) }
      } else {
        overlay.toDialogFactory(dialogEnv)
          .buildDialog(overlay, dialogEnv, context)
          .let { holder ->
            holder.onUpdateBounds?.let { updateBounds ->
              holder.dialog.maintainBounds(holder.environment) { b -> updateBounds(b) }
            }

            DialogSession(i, holder).also { newSession ->
              // Prime the pump, make the first call to OverlayDialog.show to update
              // the new dialog to reflect the first rendering.
              newSession.holder.show(overlay, dialogEnv)
              // And now start the lifecycle machinery and show the dialog window itself.
              newSession.showDialog(getParentLifecycleOwner(), stateRegistryAggregator)
            }
          }
      }
    }

    (sessions - updatedSessions.toSet()).forEach { it.dismiss() }
    // Drop the state registries for any keys that no longer exist since the last save.
    // Or really, drop everything except the remaining ones.
    stateRegistryAggregator.pruneAllChildRegistryOwnersExcept(
      keysToKeep = updatedSessions.map { it.savedStateRegistryKey }
    )
    sessions = updatedSessions
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
    return SavedState(sessions.mapNotNull { it.save() })
  }

  /** To be called from a container view's [View.onRestoreInstanceState]. */
  public fun onRestoreInstanceState(state: SavedState) {
    if (state.dialogBundles.size == sessions.size) {
      state.dialogBundles.zip(sessions) { viewState, holder -> holder.restore(viewState) }
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
     * Creates a [LayeredDialogSessions] instance based on the given [view], which will
     * serve as the source for a [LifecycleOwner], and whose bounds will be reported
     * via [OverlayArea].
     *
     * - The [view]'s [dispatchTouchEvent][View.dispatchTouchEvent] and
     *   [dispatchKeyEvent][View.dispatchKeyEvent] methods should be overridden
     *   to honor [LayeredDialogSessions.allowEvents].
     *
     * - The [view]'s [onAttachedToWindow][View.onAttachedToWindow] and
     *   [onDetachedFromWindow][View.onDetachedFromWindow] methods must call
     *   through to the like named methods of the returned [LayeredDialogSessions]
     *   ([onAttachedToWindow], [onDetachedFromWindow]).
     *
     * - The [view]'s [onSaveInstanceState][View.onSaveInstanceState] and
     *   [onRestoreInstanceState][View.onRestoreInstanceState] methods must call
     *   through to the like named methods of the returned [LayeredDialogSessions]
     *   ([onSaveInstanceState], [onRestoreInstanceState]).
     */
    public fun forView(
      view: View,
      superDispatchTouchEvent: (MotionEvent) -> Unit
    ): LayeredDialogSessions {
      val boundsRect = Rect()
      if (view.isAttachedToWindow) view.getGlobalVisibleRect(boundsRect)
      val bounds = MutableStateFlow(Rect(boundsRect))

      return LayeredDialogSessions(
        context = view.context,
        bounds = bounds,
        cancelEvents = {
          // Note similar code in DialogSession.

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
