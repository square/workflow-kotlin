package com.squareup.workflow1.ui

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.ViewStateFrame.AttachState.Attached
import com.squareup.workflow1.ui.ViewStateFrame.AttachState.Detached

/**
 * Used by containers like `BackStackContainer` and `ModalContainer` to record the [viewState] data
 * for the view identified by [key], which is expected to match the `toString()` of a
 * [com.squareup.workflow1.ui.Compatible.compatibilityKey].
 *
 * This class manages both the Android view state traditionally saved and restored through the
 * [View.onSaveInstanceState]/[View.onRestoreInstanceState], as well as the modern
 * [SavedStateRegistry] for the view exposed via [ViewTreeSavedStateRegistryOwner].
 *
 * A frame can either be [Detached] if it's simply holding the saved data for a hidden view, or
 * [Attached] when it's managing a visible view.
 */
@OptIn(WorkflowUiExperimentalApi::class)
public class ViewStateFrame(
  public val key: String,
  @VisibleForTesting(otherwise = PRIVATE)
  public var viewState: SparseArray<Parcelable>?,
  @VisibleForTesting(otherwise = PRIVATE)
  public var androidXBundle: Bundle?
) : Parcelable, SavedStateRegistryOwner, WorkflowLifecycleOwner {
  public constructor(key: String) : this(key, null, null)

  private sealed class AttachState {
    /**
     * The frame is hidden and not attached to any view.
     * In this state the frame is simply a [Parcelable] holder for state data to be restored later.
     */
    object Detached : AttachState()

    /**
     * The frame represents a screen that is actively being shown.
     * In this state the frame may be asked to restore and/or save the current view's state.
     */
    class Attached(
      val view: View,
      val lifecycleOwner: WorkflowLifecycleOwner,
      val savedStateController: SavedStateRegistryController
    ) : AttachState()
  }

  private var attachState: AttachState = Detached

  override fun getLifecycle(): Lifecycle =
    requireAttached().lifecycleOwner.lifecycle

  /**
   * Note that this method will throw if it's called from a view's OnAttachStateChangeListener's
   * onDetachedFromWindow method, since this frame will have already detached.
   */
  override fun getSavedStateRegistry(): SavedStateRegistry =
    requireAttached().savedStateController.savedStateRegistry

  /**
   * Sets up any tags on the [view] that need to point to state or lifecycle information managed
   * by this frame.
   *
   * Note that this method requires [WorkflowLifecycleOwner.installOn] to already have been called
   * for the view. It will read the [WorkflowLifecycleOwner] and then replace it with this
   * [ViewStateFrame].
   */
  public fun attach(view: View) {
    require(attachState is Detached) {
      "Expected attach to only be called once per view."
    }
    attachState = Attached(
      view = view,
      lifecycleOwner = requireNotNull(WorkflowLifecycleOwner.get(view)) {
        "Expected BackStackContainer screen view to have a WorkflowLifecycleOwner tag set."
      },
      savedStateController = SavedStateRegistryController.create(this)
    )

    lifecycle.addObserver(object : LifecycleEventObserver {
      override fun onStateChanged(
        source: LifecycleOwner,
        event: Event
      ) {
        if (event == ON_DESTROY) {
          changeToDetachedState()
        }
      }
    })

    ViewTreeSavedStateRegistryOwner.set(view, this)
    ViewTreeLifecycleOwner.set(view, this)
  }

  /**
   * Replaces this frame's state for the AndroidX [SavedStateRegistry] with that from [frame].
   *
   * This method can be called regardless of attached state.
   */
  public fun loadAndroidXStateRegistryFrom(frame: ViewStateFrame?) {
    require(frame == null || key == frame.key) {
      "Expected restored frame's key to match: $key != ${frame!!.key}"
    }

    // We don't need to set viewState since this method is only called when this frame is the
    // current frame, and in that case restoreHierarchyState will be called separately.
    androidXBundle = frame?.androidXBundle
  }

  /**
   * Invokes [SavedStateRegistryController.performRestore] on the [SavedStateRegistryController]
   * owned by this frame.
   *
   * This _must_ always be called:
   *  - before the attached view has a chance to call
   *    [SavedStateRegistry.consumeRestoredStateForKey].
   *  - before calling [performSave] or an exception will be thrown.
   *
   * It may only be called between [attach] and [destroyOnDetach].
   */
  public fun restoreAndroidXStateRegistry() {
    // Controller must _always_ be restored, even if there's no data, so that consume doesn't
    // throw.
    requireAttached().savedStateController
      .performRestore(androidXBundle ?: Bundle.EMPTY)
  }

  /**
   * Calls [View.restoreHierarchyState] on the [view] to restore it from the data owned by this
   * frame.
   *
   * This method should be called as soon as the view is created that it owns the state for, and it
   * _must_ be called before [performSave].
   */
  public fun restoreViewHierarchyState() {
    // When this method is called to restore a previously-hidden view, this will perform the
    // restore. If viewState is null, this call is happening as part of the entire activity restore,
    // and the Android framework will call restoreHierarchyState for us.
    viewState?.let(requireAttached().view::restoreHierarchyState)
  }

  /**
   * Saves the [SavedStateRegistry], and if [saveViewHierarchyState] is not true, also asks the
   * view this frame is attached to to save its own state.
   *
   * [restoreAndroidXStateRegistry] _must_ be called before this method.
   */
  public fun performSave(saveViewHierarchyState: Boolean) {
    requireAttached().apply {
      androidXBundle = Bundle().also(savedStateController::performSave)

      if (saveViewHierarchyState) {
        viewState = SparseArray<Parcelable>().also(view::saveHierarchyState)
      }
    }
  }

  override fun destroyOnDetach() {
    requireAttached().lifecycleOwner.destroyOnDetach()
  }

  private fun changeToDetachedState() {
    requireAttached().view.let { view ->
      ViewTreeLifecycleOwner.set(view, null)
      ViewTreeSavedStateRegistryOwner.set(view, null)
    }
    attachState = Detached
  }

  override fun describeContents(): Int = 0

  override fun writeToParcel(
    parcel: Parcel,
    flags: Int
  ) {
    parcel.writeString(key)
    // viewState may be null here if this is the current frame in a ViewStateCache, in which case
    // the view state isn't managed by this frame but by the regular view tree dispatch.
    @Suppress("UNCHECKED_CAST")
    parcel.writeSparseArray(viewState as SparseArray<Any>?)
    parcel.writeBundle(androidXBundle)
  }

  override fun toString(): String =
    "ViewStateFrame(key=$key, " +
      "attachState=$attachState, " +
      "lifecycle=${(attachState as? Attached)?.lifecycleOwner?.lifecycle?.currentState})"

  private fun requireAttached(): Attached =
    attachState as? Attached ?: error("Expected ViewStateFrame to be attached to view.")

  public companion object CREATOR : Creator<ViewStateFrame> {
    override fun createFromParcel(parcel: Parcel): ViewStateFrame {
      val key = parcel.readString()!!
      val classLoader = ViewStateFrame::class.java.classLoader
      val viewState = parcel.readSparseArray<Parcelable>(classLoader)
      val androidXBundle = parcel.readBundle(classLoader)

      return ViewStateFrame(key, viewState, androidXBundle)
    }

    override fun newArray(size: Int): Array<ViewStateFrame?> = arrayOfNulls(size)
  }
}
