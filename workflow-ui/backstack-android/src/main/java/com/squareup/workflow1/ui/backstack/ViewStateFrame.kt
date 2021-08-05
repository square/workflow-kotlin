package com.squareup.workflow1.ui.backstack

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray

/**
 * Used by [ViewStateCache] to record the [viewState] and
 * [SavedStateRegistry][androidx.savedstate.SavedStateRegistry] [`stateRegistryBundle`][Bundle] data
 * for the view identified by [key], which is expected to match the `toString()` of a
 * [com.squareup.workflow1.ui.Compatible.compatibilityKey].
 */
internal data class ViewStateFrame(
  val key: String,
  val viewState: SparseArray<Parcelable>?,
  val stateRegistryBundle: Bundle
) : Parcelable {

  override fun describeContents(): Int = 0

  override fun writeToParcel(
    parcel: Parcel,
    flags: Int
  ) {
    parcel.writeString(key)
    @Suppress("UNCHECKED_CAST")
    parcel.writeSparseArray(viewState as SparseArray<Any>?)
    parcel.writeBundle(stateRegistryBundle)
  }

  companion object CREATOR : Creator<ViewStateFrame> {
    override fun createFromParcel(parcel: Parcel): ViewStateFrame {
      val classLoader = ViewStateFrame::class.java.classLoader
      val key = parcel.readString()!!
      val viewState = parcel.readSparseArray<Parcelable>(classLoader)
      val stateRegistryBundle = parcel.readBundle(classLoader)!!

      return ViewStateFrame(key, viewState, stateRegistryBundle)
    }

    override fun newArray(size: Int): Array<ViewStateFrame?> = arrayOfNulls(size)
  }
}
