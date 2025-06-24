package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.parse
import com.squareup.workflow1.readByteStringWithLength
import com.squareup.workflow1.readList
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeByteStringWithLength
import com.squareup.workflow1.writeList
import com.squareup.workflow1.writeUtf8WithLength
import okio.ByteString

internal fun createSaveableStateRegistryForTreeSnapshot(
  treeSnapshot: TreeSnapshot?
): SaveableStateRegistry {
  val snapshot = treeSnapshot?.workflowSnapshot
  return createSaveableStateRegistryForSnapshot(snapshot)
}

internal fun createSaveableStateRegistryForSnapshot(snapshot: Snapshot?): SaveableStateRegistry {
  val restoredValues = snapshotToRestoredValues(snapshot)
  return SaveableStateRegistry(
    restoredValues = restoredValues,
    canBeSaved = { it is Snapshot || (it is MutableState<*> && it.value is Snapshot) },
  )
}

internal fun savedValuesToSnapshot(savedValues: Map<String, List<Any?>>): Snapshot =
  Snapshot.write { sink ->
    sink.writeInt(savedValues.size)
    savedValues.entries.forEach { (key, snapshots) ->
      sink.writeUtf8WithLength(key)
      sink.writeList(snapshots) {
        when (it) {
          is Snapshot? -> {
            sink.writeByte(0)
            val snapshot = it
            val bytes = snapshot?.bytes ?: ByteString.EMPTY
            sink.writeByteStringWithLength(bytes)
          }

          is MutableState<*> -> {
            sink.writeByte(1)
            val snapshot = it.value as Snapshot?
            val bytes = snapshot?.bytes ?: ByteString.EMPTY
            sink.writeByteStringWithLength(bytes)
          }

          else ->
            error(
              "Expected saved state value to be a Snapshot or MutableState<Snapshot>, " +
                "but was $it"
            )
        }
      }
    }
  }

private fun snapshotToRestoredValues(snapshot: Snapshot?): Map<String, List<Any?>>? {
  if (snapshot == null) return null
  return buildMap {
    snapshot.bytes.parse { source ->
      val mapSize = source.readInt()
      repeat(mapSize) {
        val key = source.readUtf8WithLength()
        val snapshots: List<Any?> = source.readList {
          when (val valueTypeTag = source.readByte()) {
            0.toByte() -> {
              // Direct snapshot.
              val bytes = source.readByteStringWithLength()
              if (bytes.size == 0) null else Snapshot.of(bytes)
            }

            1.toByte() -> {
              // MutableState of snapshot.
              val bytes = source.readByteStringWithLength()
              val snapshot = if (bytes.size == 0) null else Snapshot.of(bytes)
              snapshot?.let(::mutableStateOf)
            }

            else -> error("Unknown tag: $valueTypeTag")
          }
        }
        if (snapshots.isNotEmpty()) {
          put(key, snapshots)
        }
      }
    }
  }
}
