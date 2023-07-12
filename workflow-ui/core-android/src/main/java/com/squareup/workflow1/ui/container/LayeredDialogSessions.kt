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
import androidx.core.view.doOnAttach
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.lifecycleOwnerFromContext
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator
import com.squareup.workflow1.ui.container.DialogSession.KeyAndBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Does the bulk of the work of maintaining a set of [Dialog][android.app.Dialog]s
 * to reflect lists of [Overlay], constrained to cover only the bounds of a single
 * [View]. This is the work modeled by [BodyAndOverlaysScreen] and executed
 * by [BodyAndOverlaysContainer]. This class is public to allow implementation of
 * custom [Overlay]-based layouts if [BodyAndOverlaysContainer] is too restrictive.
 *
 * - Provides an [allowBodyEvents] field that reflects the presence or absence of Dialogs driven
 *   by [ModalOverlay]
 *
 * - Makes [OverlayArea] available in the [ViewEnvironment],
 *   and uses it to drive calls to [OverlayDialogHolder.onUpdateBounds].
 *
 * - Provides a [LifecycleOwner] per managed Dialog, and view persistence support,
 *   both for classic [View.onSaveInstanceState] and
 *   Jetpack [SavedStateRegistry][androidx.savedstate.SavedStateRegistry].
 *
 * Supports recursion (e.g. nesting a smaller [BodyAndOverlaysScreen] in the
 * [body][BodyAndOverlaysScreen.body] field of another) by sharing a single [DialogCollator]
 * instance across multiple instances of [LayeredDialogSessions]. See [DialogCollator] for
 * details.
 *
 * ## Lifecycle of a managed [Dialog][android.app.Dialog]
 *
 * When [update] is called with an [Overlay] that is not [Compatible] with an
 * existing Dialog, the appropriate [OverlayDialogFactory] instance is fetched from
 * the [ViewEnvironment]. That factory builds (but does not
 * [show][android.app.Dialog.show]) a new Dialog, wrapped in an [OverlayDialogHolder].
 * The holder in turn is held by a [DialogSession] instance. There is a 1:1:1 relationship
 * between the Dialog, the [OverlayDialogHolder] which can [update it][OverlayDialogHolder.show],
 * and the [DialogSession] that manages its [LifecycleOwner] and persistence.
 *
 * When a new [DialogSession] begins:
 *
 * - a [LifecycleOwner] is created as a child to the one provided
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
 * [Compatible] [Overlay] rendering.
 *
 * When [update] is called without a matching [Overlay], or the
 * [parent Lifecycle][getParentLifecycleOwner] ends, the [DialogSession] ends,
 * its [LifecycleOwner] is destroyed, and the Dialog is
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
   * My unique ID in the [DialogCollator] shared with any other [LayeredDialogSessions]
   * with a shared ancestor.
   */
  private val idInCollator = UUID.randomUUID()

  /**
   * Provides a new `SavedStateRegistryOwner` for each dialog,
   * which will save to the `SavedStateRegistryOwner` of this container view.
   */
  private val stateRegistryAggregator = WorkflowSavedStateRegistryAggregator()

  private var sessions: List<DialogSession> = emptyList()

  /**
   * Reflects whether [onAttachedToWindow] has been called yet, so that
   * we can avoid [showing][DialogSession.showNewDialog] `Dialog` windows
   * before the `Activity` window is ready.
   */
  private var attached = false

  /**
   * Set by an outermost instance only. If we are in a root [update] call before
   * [attached] is true, holds on to a list set of all [DialogSession]s, to be
   * [shown][DialogSession.showNewDialog] at attach time instead of immediately.
   * Necessary to ensure that view state is restored in the correct order --
   * the `Activity` `contentView` must be attached to its window before that of
   * any `Dialog`.
   *
   * Note that this is a superset of [sessions], also includes sessions built by
   * any nested [LayeredDialogSessions] instances during recursive [update]
   * calls.
   *
   * https://github.com/square/workflow-kotlin/issues/1001
   */
  private var deferredShow: List<DialogSession>? = null

  /**
   * While this is true, clients should ignore UI events targeting the
   * [body][BodyAndOverlaysScreen.body] view beneath the
   * [Dialog][android.app.Dialog] windows they manage. It reflects
   * that a [ModalOverlay] is (or soon will be) showing over the body.
   */
  public var allowBodyEvents: Boolean = true
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
   * can use [LifecycleOwner][androidx.lifecycle.LifecycleOwner] as
   * usual.
   *
   * @param updateBody function to be called before updating the dialogs, presumably
   * to update the view for the [body][BodyAndOverlaysScreen.body] beneath the dialogs.
   * The [ViewEnvironment] passed to the given function is enhanced with a [CoveredByModal]
   * as appropriate, to ensure proper behavior of [allowBodyEvents] of nested containers.
   */
  public fun update(
    overlays: List<Overlay>,
    viewEnvironment: ViewEnvironment,
    updateBody: (environment: ViewEnvironment) -> Unit
  ) {
    // Set up a ViewEnvironment with the single DialogCollator instance that handles
    // this entire view hierarchy. See that class for details.
    val envWithDialogManager = viewEnvironment.establishDialogCollator(
      id = idInCollator,
      existingSessions = sessions,
      onRootUpdateFinished = if (attached) {
        null
      } else {
        { deferredShow = it }
      }
    )
    val collator = envWithDialogManager[DialogCollator]

    val showingModals = overlays.any { it is ModalOverlay }
    allowBodyEvents = !showingModals

    updateBody(
      if (showingModals) envWithDialogManager + (CoveredByModal to true) else envWithDialogManager
    )

    val envPlusBounds = envWithDialogManager + OverlayArea(bounds)
    val updates = mutableListOf<DialogSessionUpdate>()
    overlays.forEach { overlay ->
      updates += DialogSessionUpdate(overlay) { oldSessions, covered ->
        val dialogEnv = if (covered) envPlusBounds + (CoveredByModal to true) else envPlusBounds
        val oldSessionOrNull = oldSessions.find(overlay)

        oldSessionOrNull
          // If there is already a matching session, it may have been hidden b/c out of order.
          ?.also { session -> session.show(overlay, dialogEnv) }
          // This is a new Overlay, create and show its Dialog.
          ?: overlay.toDialogFactory(dialogEnv)
            .buildDialog(overlay, dialogEnv, context)
            .let { holder ->
              holder.onUpdateBounds?.let { updateBounds ->
                holder.dialog.maintainBounds(holder.environment) { b -> updateBounds(b) }
              }

              DialogSession(stateRegistryAggregator, overlay, holder, getParentLifecycleOwner)
                .apply {
                  initNewDialog(dialogEnv)

                  // Only show the new dialog immediately if we are already attached to the
                  // Activity window.
                  if (attached) showNewDialog()
                }
            }
      }
    }
    collator.scheduleUpdates(idInCollator, updates) { updatedSessions ->
      (sessions - updatedSessions.toSet()).forEach { it.destroyDialog() }
      // Drop the state registries for any keys that no longer exist since the last save.
      // Or really, drop everything except the remaining ones.
      stateRegistryAggregator.pruneAllChildRegistryOwnersExcept(
        keysToKeep = updatedSessions.map { it.savedStateKey }
      )
      sessions = updatedSessions
    }
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
    attached = true
    stateRegistryAggregator.attachToParentRegistry(
      savedStateParentKey,
      WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext(view)
    )

    // If any dialogs were created before we were attached to the window, show them now.
    deferredShow?.let { it ->
      deferredShow = null
      it.forEach { it.showNewDialog() }
    }
  }

  /**
   * Must be called whenever the owning view is
   * [detached from a window][View.onDetachedFromWindow].
   * Must be matched with a call to [onAttachedToWindow].
   */
  public fun onDetachedFromWindow() {
    attached = false
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
     *   to honor [LayeredDialogSessions.allowBodyEvents].
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
      if (view.isAttachedToWindow) view.getScreenRect(boundsRect)
      val boundsStateFlow = MutableStateFlow(Rect(boundsRect))

      return LayeredDialogSessions(
        context = view.context,
        bounds = boundsStateFlow,
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
        checkNotNull(view.findViewTreeLifecycleOwner()) {
          "Expected a ViewTreeLifecycleOwner on $view"
        }
      }.also { dialogs ->
        fun closeAll() {
          dialogs.update(emptyList(), ViewEnvironment.EMPTY) {}
        }

        // We rely on the hosting View's WorkflowLifecycleOwner to tell us to tear things down.
        // WorkflowLifecycleOwner gets hooked up when the View is attached to its window.
        // But the Activity might finish before the hosting view is ever attached. And we have
        // lots of time to show Dialogs before then. They will leak.
        //
        // To guard against that we hang a default observer directly off of the Activity that
        // will close all Dialogs when it is destroyed; and we remove it as soon as the hosting
        // view is attached for the first time.
        val failsafe = object : DefaultLifecycleObserver {
          override fun onDestroy(owner: LifecycleOwner) = closeAll()
        }
        lifecycleOwnerFromContext(view.context).lifecycle.addObserver(failsafe)
        view.doOnAttach {
          lifecycleOwnerFromContext(it.context).lifecycle.removeObserver(failsafe)
        }

        // While the hosting view is attached, monitor its bounds and report them
        // through boundsStateFlow so that managed Dialogs can constrain themselves
        // accordingly.
        val attachStateChangeListener = object : OnAttachStateChangeListener {
          val boundsListener = OnGlobalLayoutListener {
            view.getScreenRect(boundsRect)
            if (boundsRect != boundsStateFlow.value) boundsStateFlow.value = Rect(boundsRect)
          }

          override fun onViewAttachedToWindow(v: View) {
            boundsListener.onGlobalLayout()
            v.viewTreeObserver.addOnGlobalLayoutListener(boundsListener)
          }

          override fun onViewDetachedFromWindow(v: View) {
            // Don't leak the dialogs if we're suddenly yanked out of view.
            // https://github.com/square/workflow-kotlin/issues/314
            closeAll()
            v.viewTreeObserver.removeOnGlobalLayoutListener(boundsListener)
            boundsStateFlow.value = Rect()
          }
        }

        view.addOnAttachStateChangeListener(attachStateChangeListener)
      }
    }
  }
}
