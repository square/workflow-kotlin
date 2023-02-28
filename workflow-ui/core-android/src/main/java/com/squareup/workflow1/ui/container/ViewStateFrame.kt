package com.squareup.workflow1.ui.container

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray

/**
 * Used by [ViewStateCache] to record the [viewState] data for the view identified
 * by [key], which is expected to match the `toString()` of a
 * [com.squareup.workflow1.ui.Compatible.compatibilityKey].
 */
internal data class ViewStateFrame(
  val key: String,
  val viewState: SparseArray<Parcelable>
) : Parcelable {

  override fun describeContents(): Int = 0

  override fun writeToParcel(
    parcel: Parcel,
    flags: Int
  ) {
    parcel.writeString(key)
    @Suppress("UNCHECKED_CAST")
    parcel.writeSparseArray(viewState as SparseArray<Any>)
  }

  companion object CREATOR : Creator<ViewStateFrame> {
    override fun createFromParcel(parcel: Parcel): ViewStateFrame {
      val key = parcel.readString()!!
      val viewState = if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
        parcel.readSparseArray<Parcelable>(
          ViewStateFrame::class.java.classLoader,
          Parcelable::class.java
        )!!
      } else {
        @Suppress("DEPRECATION")
        parcel.readSparseArray<Parcelable>(ViewStateFrame::class.java.classLoader)!!
      }

      return ViewStateFrame(key, viewState)
    }

    override fun newArray(size: Int): Array<ViewStateFrame?> = arrayOfNulls(size)
  }
}
