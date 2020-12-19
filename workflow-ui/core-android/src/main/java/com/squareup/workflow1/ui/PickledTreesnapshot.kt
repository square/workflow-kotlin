package com.squareup.workflow1.ui

import android.os.Parcel
import android.os.Parcelable
import com.squareup.workflow1.TreeSnapshot
import okio.ByteString.Companion.toByteString

/**
 * [Parcelable] that can wrap a [TreeSnapshot] for easy Android persistence.
 */
internal class PickledTreesnapshot(internal val snapshot: TreeSnapshot) : Parcelable {
  override fun describeContents(): Int = 0

  override fun writeToParcel(
    dest: Parcel,
    flags: Int
  ) = dest.writeByteArray(
      snapshot.toByteString()
          .toByteArray()
  )

  companion object CREATOR : Parcelable.Creator<PickledTreesnapshot> {
    override fun createFromParcel(parcel: Parcel): PickledTreesnapshot {
      val bytes = parcel.createByteArray()!!
          .toByteString()
      return PickledTreesnapshot(TreeSnapshot.parse(bytes))
    }

    override fun newArray(size: Int): Array<PickledTreesnapshot?> = arrayOfNulls(size)
  }
}
