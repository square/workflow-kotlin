package com.squareup.workflow1

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcel
import android.os.Parcelable
import okio.ByteString

/**
 * Wraps receiver in a [Snapshot] suitable for use with [StatefulWorkflow].
 * Intended to allow use of `@Parcelize`.
 *
 * Read the [Parcelable] back with [toParcelable].
 */
public fun Parcelable.toSnapshot(): Snapshot = Snapshot.write { bufferedSink ->
  val parcel = Parcel.obtain()
  parcel.writeParcelable(this, 0)
  val byteArray = parcel.marshall()
  bufferedSink.write(byteArray)
  parcel.recycle()
}

/**
 * Returns a [Parcelable] previously wrapped with [toSnapshot], or `null` if the receiver is empty.
 */
public inline fun <reified T : Parcelable> Snapshot.toParcelable(): T? =
  bytes.toParcelable<T>()

public inline fun <reified T : Parcelable> ByteString.toParcelable(): T? =
  toParcelable(T::class.java)

@PublishedApi
internal fun <T : Parcelable> ByteString.toParcelable(targetClass: Class<T>): T? {
  if (size == 0) return null

  val parcel = Parcel.obtain()
  val byteArray = toByteArray()
  parcel.unmarshall(byteArray, 0, byteArray.size)
  parcel.setDataPosition(0)
  val rtn = if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
    parcel.readParcelable(Snapshot::class.java.classLoader, targetClass)!!
  } else {
    @Suppress("DEPRECATION")
    parcel.readParcelable(Snapshot::class.java.classLoader)!!
  }
  parcel.recycle()
  return rtn
}
