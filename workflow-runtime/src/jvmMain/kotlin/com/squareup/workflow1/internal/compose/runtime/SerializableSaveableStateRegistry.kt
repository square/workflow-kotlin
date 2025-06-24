package com.squareup.workflow1.internal.compose.runtime

import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeUtf8WithLength
import okio.BufferedSink
import okio.ByteString
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * A [SaveableStateRegistry] that can save and restore anything that is [Serializable].
 */
internal class SerializableSaveableStateRegistry private constructor(
  saveableStateRegistry: SaveableStateRegistry
) : SaveableStateRegistry by saveableStateRegistry {
  constructor(restoredValues: Map<String, List<Any?>>?) : this(
    SaveableStateRegistry(restoredValues, ::canBeSavedAsSerializable)
  )

  constructor(snapshot: Snapshot) : this(snapshot.bytes.toMap())

  fun toSnapshot(): Snapshot = Snapshot.write { sink ->
    performSave().writeTo(sink)
  }
}

/**
 * Checks that [value] can be stored as a [Serializable].
 */
private fun canBeSavedAsSerializable(value: Any): Boolean {
  if (value !is Serializable) return false

  // lambdas in Kotlin implement Serializable, but will crash if you really try to save them.
  // we check for both Function and Serializable (see kotlin.jvm.internal.Lambda) to support
  // custom user defined classes implementing Function interface.
  if (value is Function<*>) return false

  return true
}

private fun ByteString.toMap(): Map<String, List<Any?>>? {
  return parse { source ->
    val size = source.readInt()
    if (size == 0) return null

    val inputStream = ObjectInputStream(source.inputStream())
    buildMap(capacity = size) {
      repeat(size) {
        val key = source.readUtf8WithLength()

        @Suppress("UNCHECKED_CAST")
        val arrayList = inputStream.readObject() as ArrayList<Any?>
        put(key, arrayList)
      }
    }
  }
}

private fun Map<String, List<Any?>>.writeTo(sink: BufferedSink) {
  // sink.writeInt(values.size)
  // val outputStream = ObjectOutputStream(sink.outputStream())
  // values.forEach { (key, list) ->
  //   val arrayList = if (list is ArrayList<Any?>) list else ArrayList(list)
  //   sink.writeUtf8WithLength(key)
  //   outputStream.writeObject(arrayList)
  //   outputStream.flush()
  // }
}
