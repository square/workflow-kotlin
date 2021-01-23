package com.squareup.workflow1.ui

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import android.view.View
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner

/**
 * Used by [ViewStateCache] to record the [viewState] data for the view identified
 * by [key], which is expected to match the `toString()` of a
 * [com.squareup.workflow1.ui.Compatible.compatibilityKey].
 */
@OptIn(WorkflowUiExperimentalApi::class)
public class ViewStateFrame private constructor(
  public val key: String,
  private var viewState: SparseArray<Parcelable>?,
  private var androidXBundle: Bundle?
) : Parcelable {
  public constructor(key: String) : this(key, null, null)

  /**
   * Acts as the [LifecycleOwner][androidx.lifecycle.LifecycleOwner], etc for the backstack frame.
   * This will initially be set by [restoreTo], and then nulled out again when the frame
   * is hidden (by [destroyOnDetach]), to guard against memory leaks.
   */
  private var savedStateController: WorkflowSavedStateRegistryController? = null

  public fun loadFrom(frame: ViewStateFrame) {
    require(key == frame.key) { "Expected frame's key to match: $key != ${frame.key}" }
    viewState = frame.viewState
    androidXBundle = frame.androidXBundle
  }

  /**
   * Initializes an [WorkflowSavedStateRegistryController] for this frame, installs it as the
   * [ViewTreeSavedStateRegistryOwner], and restores view hierarchy state.
   */
  public fun restoreTo(view: View) {
    check(savedStateController == null)

    val lifecycle = WorkflowLifecycleOwner.get(view)!!
    savedStateController = WorkflowSavedStateRegistryController(lifecycle).also { controller ->
      ViewTreeSavedStateRegistryOwner.set(view, controller)

      androidXBundle?.let(controller::performRestore)
      viewState?.let(view::restoreHierarchyState)
    }
  }

  public fun destroyOnDetach() {
    savedStateController?.destroyOnDetach()
    // Null it out to guard against memory leaks, since this frame instance will persist potentially
    // for a long time while the screen is hidden.
    savedStateController = null
  }

  /**
   * Saves the SavedStateRegistry, and if [view] is not null, also asks the view to save its own
   * state.
   */
  public fun performSave(view: View? = null) {
    androidXBundle = savedStateController!!.performSave()
    if (view != null) {
      viewState = SparseArray<Parcelable>().also {
        view.saveHierarchyState(it)
      }
    }
  }

  override fun describeContents(): Int = 0

  override fun writeToParcel(
    parcel: Parcel,
    flags: Int
  ) {
    parcel.writeString(key)
    @Suppress("UNCHECKED_CAST")
    parcel.writeSparseArray(viewState as SparseArray<Any>)
    parcel.writeBundle(androidXBundle)
  }

  public companion object CREATOR : Creator<ViewStateFrame> {
    override fun createFromParcel(parcel: Parcel): ViewStateFrame {
      val key = parcel.readString()!!
      val classLoader = ViewStateFrame::class.java.classLoader
      val viewState = parcel.readSparseArray<Parcelable>(classLoader)!!
      val androidXBundle = parcel.readBundle(classLoader)

      return ViewStateFrame(key, viewState, androidXBundle)
    }

    override fun newArray(size: Int): Array<ViewStateFrame?> = arrayOfNulls(size)
  }
}

/**
 * TODO write documentation
 */
@OptIn(WorkflowUiExperimentalApi::class)
private class WorkflowSavedStateRegistryController(
  lifecycleOwner: WorkflowLifecycleOwner,
) : SavedStateRegistryOwner, WorkflowLifecycleOwner by lifecycleOwner {

  private val savedStateController = SavedStateRegistryController.create(this)

  override fun getSavedStateRegistry(): SavedStateRegistry = savedStateController.savedStateRegistry

  fun performSave(): Bundle = Bundle().also(savedStateController::performSave)
  fun performRestore(bundle: Bundle) {
    savedStateController.performRestore(bundle)
  }
}
