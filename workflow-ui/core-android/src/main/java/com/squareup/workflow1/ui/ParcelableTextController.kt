package com.squareup.workflow1.ui

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

/**
 * [Parcelable] implementation of [TextController].
 */
public class ParcelableTextController private constructor(
  controllerImplementation: TextController
) : TextController by controllerImplementation, Parcelable {

  public constructor(initialValue: String = "") : this(TextController(initialValue))

  private constructor(parcel: Parcel) : this(parcel.readString() ?: "")

  override fun writeToParcel(
    out: Parcel,
    flags: Int
  ) {
    out.writeString(textValue)
  }

  override fun describeContents(): Int = 0

  public companion object CREATOR : Creator<ParcelableTextController> {

    override fun createFromParcel(source: Parcel): ParcelableTextController =
      ParcelableTextController(source)

    override fun newArray(size: Int): Array<ParcelableTextController?> = arrayOfNulls(size)
  }
}
