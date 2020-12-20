package com.squareup.workflow1.ui.backstack

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import com.squareup.workflow1.ui.AndroidXGlue

/**
 * Used by [ViewStateCache] to record the [viewState] data for the view identified
 * by [key], which is expected to match the `toString()` of a
 * [com.squareup.workflow1.ui.Compatible.compatibilityKey].
 */
internal data class ViewStateFrame(
  val key: String,
  val viewState: SparseArray<Parcelable>,
  val androidXGlue: AndroidXGlue?
) : Parcelable {

  override fun describeContents(): Int = 0

  override fun writeToParcel(
    parcel: Parcel,
    flags: Int
  ) {
    parcel.writeString(key)
    @Suppress("UNCHECKED_CAST")
    parcel.writeSparseArray(viewState as SparseArray<Any>)
    androidXGlue?.writeToParcel(parcel)
  }

  companion object CREATOR : Creator<ViewStateFrame> {
    override fun createFromParcel(parcel: Parcel): ViewStateFrame {
      val key = parcel.readString()!!
      val viewState = parcel.readSparseArray<Parcelable>(ViewStateFrame::class.java.classLoader)!!
      val androidXGlue = AndroidXGlue.readFromParcel(parcel)

      return ViewStateFrame(key, viewState, androidXGlue)
    }

    override fun newArray(size: Int): Array<ViewStateFrame?> = arrayOfNulls(size)
  }
}
