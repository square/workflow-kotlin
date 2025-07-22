package com.squareup.workflow1.ui

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcel
import android.os.Parcelable
import com.squareup.workflow1.Snapshot
import okio.ByteString

/**
 * Wraps receiver in a [Snapshot] suitable for use with [com.squareup.workflow1.StatefulWorkflow].
 * Intended to allow use of `@Parcelize`.
 *
 * Read the [Parcelable] back with [toParcelable].
 */
public fun Parcelable.toSnapshot(): Snapshot {
  return Snapshot.write { bufferedSink ->
    val parcel = Parcel.obtain()
    parcel.writeParcelable(this, 0)
    val byteArray = parcel.marshall()
    bufferedSink.write(byteArray)
    parcel.recycle()
  }
}

/**
 * @return a [Parcelable] previously wrapped with [toSnapshot], or `null` if the receiver is empty.
 */
public inline fun <reified T : Parcelable> Snapshot.toParcelable(): T? {
  return bytes.takeIf { it.size > 0 }
    ?.toParcelable<T>()
}

public inline fun <reified T : Parcelable> ByteString.toParcelable(): T {
  val parcel = Parcel.obtain()
  val byteArray = toByteArray()
  parcel.unmarshall(byteArray, 0, byteArray.size)
  parcel.setDataPosition(0)
  val rtn = if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
    parcel.readParcelable<T>(Snapshot::class.java.classLoader, T::class.java)!!
  } else {
    @Suppress("DEPRECATION")
    parcel.readParcelable<T>(Snapshot::class.java.classLoader)!!
  }
  parcel.recycle()
  return rtn
}
