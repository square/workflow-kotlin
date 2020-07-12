/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow1.ui

import android.os.Parcel
import android.os.Parcelable
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StateSaver
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Wraps receiver in a [Snapshot] suitable for use with [com.squareup.workflow1.StatefulWorkflow].
 * Intended to allow use of `@Parcelize`.
 *
 * Read the [Parcelable] back with [toParcelable].
 */
fun Parcelable.toSnapshot(): Snapshot {
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
inline fun <reified T : Parcelable> Snapshot.toParcelable(): T? {
  return bytes.takeIf { it.size > 0 }
      ?.toParcelable<T>()
}

inline fun <reified T : Parcelable> ByteString.toParcelable(): T {
  val parcel = Parcel.obtain()
  val byteArray = toByteArray()
  parcel.unmarshall(byteArray, 0, byteArray.size)
  parcel.setDataPosition(0)
  val rtn = parcel.readParcelable<T>(Snapshot::class.java.classLoader)!!
  parcel.recycle()
  return rtn
}

class ParcelableSaver<T : Parcelable> : StateSaver<T> {
  override fun toByteString(value: T): ByteString {
    val parcel = Parcel.obtain()
    parcel.writeParcelable(value, 0)
    val byteArray = parcel.marshall()
    parcel.recycle()
    return byteArray.toByteString()
  }

  override fun fromByteString(bytes: ByteString): T {
    val parcel = Parcel.obtain()
    val byteArray = bytes.toByteArray()
    parcel.unmarshall(byteArray, 0, byteArray.size)
    parcel.setDataPosition(0)
    val rtn = parcel.readParcelable<T>(Snapshot::class.java.classLoader)!!
    parcel.recycle()
    return rtn
  }
}
