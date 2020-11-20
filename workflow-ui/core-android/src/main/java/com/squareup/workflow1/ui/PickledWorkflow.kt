package com.squareup.workflow1.ui

import android.os.Parcel
import android.os.Parcelable
import com.squareup.workflow1.TreeSnapshot
import okio.ByteString.Companion.toByteString

internal class PickledWorkflow(internal val snapshot: TreeSnapshot) : Parcelable {
  override fun describeContents(): Int = 0

  override fun writeToParcel(
    dest: Parcel,
    flags: Int
  ) = dest.writeByteArray(
      snapshot.toByteString()
          .toByteArray()
  )

  companion object CREATOR : Parcelable.Creator<PickledWorkflow> {
    override fun createFromParcel(parcel: Parcel): PickledWorkflow {
      val bytes = parcel.createByteArray()!!
          .toByteString()
      return PickledWorkflow(TreeSnapshot.parse(bytes))
    }

    override fun newArray(size: Int): Array<PickledWorkflow?> = arrayOfNulls(size)
  }
}
