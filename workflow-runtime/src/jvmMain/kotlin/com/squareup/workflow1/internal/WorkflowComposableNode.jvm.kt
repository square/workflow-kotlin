package com.squareup.workflow1.internal

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.readList
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeList
import com.squareup.workflow1.writeUtf8WithLength
import okio.Buffer

internal actual fun SaveableStateRegistry.toSnapshot(): Snapshot {
  performSave().let {
    println("OMG values: $it")
  }
  return Snapshot.write { sink ->
    val values = performSave()
    sink.writeList(values.toList()) { (key, values) ->
      writeUtf8WithLength(key)
      writeList(values) { value ->
        println("OMG writing value: $value")
      }
    }
  }
}

internal actual fun SaveableStateRegistry(snapshot: Snapshot?): SaveableStateRegistry {
  val values = snapshot?.let { parseValues(snapshot) }
  return SaveableStateRegistry(
    restoredValues = values,
    canBeSaved = { true }
  )
}

private fun parseValues(snapshot: Snapshot): Map<String, List<Any?>> {
  Buffer().apply {
    write(snapshot.bytes)
    val entries = readList {
      val key = readUtf8WithLength()
      val values = readList<Any?> {
        // TODO
      }
      key to values
    }
    return entries.toMap()
  }
}
