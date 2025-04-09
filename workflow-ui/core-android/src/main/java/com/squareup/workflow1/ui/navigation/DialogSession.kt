package com.squareup.workflow1.ui.navigation

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window.Callback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.androidx.OnBackPressedDispatcherOwnerKey
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.onBackPressedDispatcherOwnerOrNull
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator

/**
 * Used by [LayeredDialogSessions] to manage lifecycle and view persistence concerns for an
 * [OverlayDialogHolder], as well as enforcing modal behavior. See [LayeredDialogSessions]
 * for a general overview of the lifecycle of a managed [Dialog][android.app.Dialog].
 */
internal class DialogSession(
  private val stateRegistryAggregator: WorkflowSavedStateRegistryAggregator,
  initialOverlay: Overlay,
  holder: OverlayDialogHolder<Overlay>,
  private val getParentLifecycleOwner: () -> LifecycleOwner
) {
  // Note similar code in LayeredDialogSessions
  private var allowEvents = true
    set(value) {
      val was = field
      field = value
      holder.dialog.window?.takeIf { value != was }?.let { window ->
        // https://stackoverflow.com/questions/2886407/dealing-with-rapid-tapping-on-buttons
        // If any motion events were enqueued on the main thread, cancel them.
        dispatchCancelEvent { window.superDispatchTouchEvent(it) }
        // When we cancel, have to warn things like RecyclerView that handle streams
        // of motion events and eventually dispatch input events (click, key pressed, etc.)
        // based on them.
        window.peekDecorView()?.cancelPendingInputEvents()
      }
    }

  /**
   * Nasty hack to fix https://github.com/square/workflow-kotlin/issues/863.
   * - [OverlayDialogHolder.canShow] relies on comparing the new [Overlay] to `Dialog.overlay`
   * - `Dialog.overlay` is written to the decor view
   * - That normally happens as a side effect of [OverlayDialogHolder.show]
   * - We have to call [OverlayDialogHolder.show] before `Dialog.show`, so that the `Dialog`
   *   is initialized before it is shown
   * - It is dangerous to call `decorView` before `Dialog.show`.
   *
   * Fix is that [OverlayDialogHolder.canShow] does not read `Dialog.overlay` if
   * peekDecorView is null. Which means we have to bootstrap it into place after
   * we call `Dialog.show`.
   *
   * We keep this nullable pointer to the very first [Overlay] so that we can put it
   * in place, and then drop the reference and avoid leaking.
   */
  private var initialOverlay: Overlay? = initialOverlay

  /**
   * Used to ensure [destroyDialog] is idempotent, because `ComponentDialog.dismiss()` is not.
   */
  private var destroyed = false

  /**
   * Wrap the given dialog holder to maintain [allowEvents] on each update.
   */
  private val holder: OverlayDialogHolder<Overlay> = OverlayDialogHolder(
    holder.environment,
    holder.dialog,
    holder.onUpdateBounds
  ) { overlay, environment ->
    allowEvents = !environment[CoveredByModal]
    holder.show(overlay, environment)
  }

  /**
   * Key used for view state persistence, both classic ([save]) and
   * newfangled ([stateRegistryAggregator]).
   */
  val savedStateKey = Compatible.keyFor(initialOverlay)

  /**
   * One time call to set up our brand new [OverlayDialogHolder] instance.
   * This will be followed by one time calls to [showNewDialog] and [destroyDialog].
   */
  fun initNewDialog(initialEnvironment: ViewEnvironment) {
    // Prime the pump, make the first call to OverlayDialogHolder.show to update
    // the newly created Dialog to reflect the first rendering. Note that below
    // in this method we also have to apply initialOverlay to the Dialog itself
    // _after_ it is shown for the first time. See kdoc on initialOverlay for sordid
    // details.
    holder.show(initialOverlay!!, initialEnvironment)

    val dialog = holder.dialog

    dialog.window?.let { window ->
      val realWindowCallback = window.callback
      window.callback = object : Callback by realWindowCallback {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
          return !allowEvents || realWindowCallback.dispatchTouchEvent(event)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
          // Consume all events if we've been told to do so.
          if (!allowEvents) return true

          // Allow the usual handling, including the usual call to Dialog.onBackPressed.
          return realWindowCallback.dispatchKeyEvent(event)
        }
      }
    }
  }

  /**
   * One time call to show the managed [Dialog][OverlayDialogHolder.dialog] for the first time.
   * Called between [initNewDialog] and [destroyDialog].
   *
   * See also [setVisible], used to dismiss and re-show an existing one.
   */
  fun showNewDialog() {
    val parentLifecycleOwner = getParentLifecycleOwner()

    val dialog = holder.dialog
    dialog.show()
    // Fix for https://github.com/square/workflow-kotlin/issues/863, can't set this
    // until after show() is called. See kdoc in initialOverlay.
    initialOverlay?.let {
      dialog.overlay = it
      initialOverlay = null
    }

    dialog.decorView.also { decorView ->
      // We are more defensive than usual about this to ease migration of existing apps
      // to ComponentDialog. Perhaps we will never enforce that rigorously. It really only
      // matters for ScreenOverlay, and that's enforced via ComponentDialog.setContent.
      // Note that onBackPressedDispatcherOwnerOrNull() searches through Context as well,
      // so 99% chance we'll hit the Activity before the stub.
      val onBack = (dialog as? OnBackPressedDispatcherOwner)
        ?: holder.environment.map[OnBackPressedDispatcherOwnerKey] as? OnBackPressedDispatcherOwner
        ?: decorView.onBackPressedDispatcherOwnerOrNull()
        ?: run {
          @Suppress("UNREACHABLE_CODE")
          object : OnBackPressedDispatcherOwner {
            override val lifecycle: Lifecycle
              get() = error("To support back press handling extend ComponentDialog: $dialog")

            override val onBackPressedDispatcher: OnBackPressedDispatcher =
              error("To support back press handling extend ComponentDialog: $dialog")
          }
        }

      // Implementations of buildDialog may set their own WorkflowLifecycleOwner on the
      // content view, so to avoid interfering with them we also set it here. When the views
      // are attached, this will become the parent lifecycle of the one from buildDialog if
      // any, and so we can use our lifecycle to destroy-on-detach the dialog hierarchy.
      // Note that this stomps the owners put in place by ComponentDialog.setContentView,
      // and that is not an accident. ComponentDialog's dismiss() call (actually its onStop())
      // destroys its lifecycleRegistry, and a new one is made on demand if the Dialog is
      // shown again. That would play hell with the setVisible scheme we use to preserve
      // z-order.
      WorkflowLifecycleOwner.installOn(
        decorView,
        onBack,
        findParentLifecycle = { parentLifecycleOwner.lifecycle }
      )
      // Ensure that each dialog has its own SavedStateRegistryOwner,
      // so views in each dialog layer don't clash with other layers.
      // We set force = true because we intentionally clobber the registry
      // put in place by ComponentDialog.setContentView as of 1.7.0.
      stateRegistryAggregator.installChildRegistryOwnerOn(
        view = decorView,
        key = savedStateKey,
        force = true
      )

      decorView.doOnAttach {
        val lifecycle = parentLifecycleOwner.lifecycle
        val onDestroy = object : DefaultLifecycleObserver {
          override fun onDestroy(owner: LifecycleOwner) {
            destroyDialog()
          }
        }

        // Android makes a lot of logcat noise if it has to close the window for us. :/
        // And no, we can't call ref.dismiss() directly from the doOnDetach lambda,
        // that's too late.
        // https://github.com/square/workflow/issues/51
        lifecycle.addObserver(onDestroy)

        // Note that we are careful not to make the doOnDetach call unless
        // we actually get attached. It is common for the dialog to be dismissed
        // before it is ever shown, so doOnDetach would never fire and we'd leak the
        // onDestroy lambda.
        decorView.doOnDetach { lifecycle.removeObserver(onDestroy) }
      }
    }
  }

  fun canShow(overlay: Overlay): Boolean = holder.canShow(overlay)

  fun show(
    overlay: Overlay,
    environment: ViewEnvironment
  ) {
    if (initialOverlay != null) {
      // Dialog hasn't been shown yet, keep the bootstrap hack fresh.
      initialOverlay = overlay
    }

    holder.show(overlay, environment)
  }

  /**
   * Used by [DialogCollator] to *temporarily* [dismiss][android.app.Dialog.dismiss] or
   * [show][android.app.Dialog.show] an existing [DialogSession] without triggering the
   * other side effects of [destroyDialog], as a tool to update its z-index.
   */
  fun setVisible(visible: Boolean) {
    if (visible) {
      holder.dialog.show()
    } else {
      holder.dialog.dismiss()
    }
  }

  /**
   * We are never going to use this `Dialog` again. Tear down our lifecycle hooks
   * and dismiss it.
   */
  fun destroyDialog(saveViewState: Boolean = false) {
    if (!destroyed) {
      destroyed = true
      with(holder.dialog) {
        if (isShowing && saveViewState) {
          stateRegistryAggregator.saveAndPruneChildRegistryOwner(savedStateKey)
        }
        // The dialog's views are about to be detached, and when that happens we want to transition
        // the dialog view's lifecycle to a terminal state even though the parent is probably still
        // alive.
        window?.decorView?.let(WorkflowLifecycleOwner::get)?.destroyOnDetach()
        dismiss()
      }
    }
  }

  internal fun save(): KeyAndBundle? {
    val saved = holder.dialog.window?.saveHierarchyState() ?: return null
    return KeyAndBundle(savedStateKey, saved)
  }

  internal fun restore(keyAndBundle: KeyAndBundle) {
    if (savedStateKey == keyAndBundle.compatibilityKey) {
      holder.dialog.window?.restoreHierarchyState(keyAndBundle.bundle)
    }
  }

  internal data class KeyAndBundle(
    internal val compatibilityKey: String,
    internal val bundle: Bundle
  ) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(
      parcel: Parcel,
      flags: Int
    ) {
      parcel.writeString(compatibilityKey)
      parcel.writeBundle(bundle)
    }

    companion object CREATOR : Creator<KeyAndBundle> {
      override fun createFromParcel(parcel: Parcel): KeyAndBundle {
        val key = parcel.readString()!!
        val bundle = parcel.readBundle(KeyAndBundle::class.java.classLoader)!!
        return KeyAndBundle(key, bundle)
      }

      override fun newArray(size: Int): Array<KeyAndBundle?> = arrayOfNulls(size)
    }
  }
}
