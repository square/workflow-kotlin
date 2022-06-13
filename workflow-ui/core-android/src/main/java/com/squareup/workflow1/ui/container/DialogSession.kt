package com.squareup.workflow1.ui.container

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator

/**
 * Used by [LayeredDialogSessions] to manage lifecycle and view persistence concerns for an
 * [OverlayDialogHolder], as well as enforcing modal behavior.
 */
@WorkflowUiExperimentalApi
internal class DialogSession(
  index: Int,
  holder: OverlayDialogHolder<Overlay>
) {
  // Note similar code in LayeredDialogs
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
   * Wrap the given dialog holder to maintain [allowEvents] on each update.
   */
  val holder: OverlayDialogHolder<Overlay> = OverlayDialogHolder(
    holder.environment, holder.dialog
  ) { overlay, environment ->
    allowEvents = !environment[CoveredByModal]
    holder.show(overlay, environment)
  }

  val savedStateRegistryKey = Compatible.keyFor(holder.showing, index.toString())

  fun showDialog(
    parentLifecycleOwner: LifecycleOwner,
    stateRegistryAggregator: WorkflowSavedStateRegistryAggregator
  ) {
    val dialog = holder.dialog

    dialog.window?.let { window ->
      val realWindowCallback = window.callback
      window.callback = object : Window.Callback by realWindowCallback {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
          return !allowEvents || realWindowCallback.dispatchTouchEvent(event)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
          return !allowEvents || realWindowCallback.dispatchKeyEvent(event)
        }
      }
    }

    dialog.show()
    dialog.window?.decorView?.also { decorView ->
      // Implementations of buildDialog may set their own WorkflowLifecycleOwner on the
      // content view, so to avoid interfering with them we also set it here. When the views
      // are attached, this will become the parent lifecycle of the one from buildDialog if
      // any, and so we can use our lifecycle to destroy-on-detach the dialog hierarchy.
      WorkflowLifecycleOwner.installOn(
        decorView,
        findParentLifecycle = { parentLifecycleOwner.lifecycle }
      )
      // Ensure that each dialog has its own ViewTreeSavedStateRegistryOwner,
      // so views in each dialog layer don't clash with other layers.
      stateRegistryAggregator.installChildRegistryOwnerOn(
        view = decorView,
        key = savedStateRegistryKey
      )

      decorView.doOnAttach {
        val lifecycle = parentLifecycleOwner.lifecycle
        val onDestroy = object : DefaultLifecycleObserver {
          override fun onDestroy(owner: LifecycleOwner) {
            dismiss()
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

  fun dismiss() {
    // The dialog's views are about to be detached, and when that happens we want to transition
    // the dialog view's lifecycle to a terminal state even though the parent is probably still
    // alive.
    with(holder.dialog) {
      window?.decorView?.let(WorkflowLifecycleOwner::get)?.destroyOnDetach()
      dismiss()
    }
  }

  internal fun save(): KeyAndBundle? {
    val saved = holder.dialog.window?.saveHierarchyState() ?: return null
    return KeyAndBundle(Compatible.keyFor(holder.showing), saved)
  }

  internal fun restore(keyAndBundle: KeyAndBundle) {
    if (Compatible.keyFor(holder.showing) == keyAndBundle.compatibilityKey) {
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
