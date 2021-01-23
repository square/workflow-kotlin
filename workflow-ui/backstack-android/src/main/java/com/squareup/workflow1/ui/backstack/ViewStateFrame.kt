package com.squareup.workflow1.ui.backstack

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import android.view.View
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner

/**
 * Used by [ViewStateCache] to record the [viewState] data for the view identified
 * by [key], which is expected to match the `toString()` of a
 * [com.squareup.workflow1.ui.Compatible.compatibilityKey].
 */
internal data class ViewStateFrame(
  val key: String,
  var viewState: SparseArray<Parcelable>? = null,
  private var androidXBundle: Bundle? = null
) : Parcelable {

  /**
   * Acts as the [LifecycleOwner][androidx.lifecycle.LifecycleOwner], etc for the backstack frame.
   * This will initially be set by [installViewTreeOwners], and then nulled out again when the frame
   * is hidden (by [destroyLifecycle]), to guard against memory leaks.
   */
  private var androidXController: AndroidXController? = null

  /**
   * Initializes an [AndroidXController] for this frame and sets it on the view as all the view
   * tree owners.
   */
  // TODO rename this since it's not just androidx-related anymore
  fun installViewTreeOwners(view: View) {
    check(androidXController == null)
    androidXController = AndroidXController(androidXBundle).also { controller ->
      ViewTreeLifecycleOwner.set(view, controller)
      ViewTreeSavedStateRegistryOwner.set(view, controller)

      // This mirrors the order of calls that AppCompatActivity, for example makes.
      // onRestoreInstanceState gets called only after onStart.
      controller.startLifecycle()
      viewState?.let { view.restoreHierarchyState(it) }
      controller.resumeLifecycle()
    }
  }

  fun destroyLifecycle() {
    androidXController?.destroyLifecycle()
    // Null it out to guard against memory leaks, since this frame instance will persist potentially
    // for a long time while the screen is hidden.
    androidXController = null
  }

  fun performSave(view: View) {
    viewState = SparseArray<Parcelable>().also {
      view.saveHierarchyState(it)
    }
    androidXBundle = androidXController!!.performSave()
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

  companion object CREATOR : Creator<ViewStateFrame> {
    override fun createFromParcel(parcel: Parcel): ViewStateFrame {
      val key = parcel.readString()!!
      val classLoader = ViewStateFrame::class.java.classLoader
      val viewState = parcel.readSparseArray<Parcelable>(classLoader)!!
      val androidXBundle = parcel.readBundle(classLoader)

      return ViewStateFrame(key, viewState)
    }

    override fun newArray(size: Int): Array<ViewStateFrame?> = arrayOfNulls(size)
  }
}
