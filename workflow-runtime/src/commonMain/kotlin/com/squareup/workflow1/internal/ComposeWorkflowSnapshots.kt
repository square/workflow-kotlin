package com.squareup.workflow1.internal

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.readByteStringWithLength
import com.squareup.workflow1.readDouble
import com.squareup.workflow1.readFloat
import com.squareup.workflow1.readList
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeByteStringWithLength
import com.squareup.workflow1.writeDouble
import com.squareup.workflow1.writeFloat
import com.squareup.workflow1.writeList
import com.squareup.workflow1.writeUtf8WithLength
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString

internal fun saveSaveableStateRegistryToSnapshot(registry: SaveableStateRegistry): Snapshot =
  Snapshot.write { sink ->
    val values = registry.performSave()
    sink.writeInt(values.size)
    values.forEach { (key, valueList) ->
      sink.writeUtf8WithLength(key)
      sink.writeList(valueList) { value ->
        writeTaggedValue(value, sink)
      }
    }
  }

internal fun restoreSaveableStateRegistryFromSnapshot(snapshot: Snapshot?): SaveableStateRegistry {
  val restoredValues: Map<String, List<Any?>>? = snapshot?.bytes?.let { snapshotBytes ->
    val buffer = Buffer()
    buffer.write(snapshotBytes)
    val size = buffer.readInt()
    buildMap {
      repeat(size) {
        val key = buffer.readUtf8WithLength()
        val values = buffer.readList {
          readTaggedValue(buffer)
        }
        put(key, values)
      }
    }
  }

  return SaveableStateRegistry(
    restoredValues = restoredValues,
    canBeSaved = ::canBeSavedToBuffer
  )
}

private fun writeTaggedValue(
  value: Any?,
  sink: BufferedSink
) {
  // Write type tag followed by value.
  when (value) {
    null -> sink.writeByte(0)
    is Byte -> {
      sink.writeByte(1)
      sink.writeByte(value.toInt())
    }

    is Short -> {
      sink.writeByte(2)
      sink.writeShort(value.toInt())
    }

    is Int -> {
      sink.writeByte(3)
      sink.writeInt(value)
    }

    is Long -> {
      sink.writeByte(4)
      sink.writeLong(value)
    }

    is Float -> {
      sink.writeByte(5)
      sink.writeFloat(value)
    }

    is Double -> {
      sink.writeByte(6)
      sink.writeDouble(value)
    }

    is String -> {
      sink.writeByte(7)
      sink.writeUtf8WithLength(value)
    }

    is ByteString -> {
      sink.writeByte(8)
      sink.writeByteStringWithLength(value)
    }

    is List<*> -> {
      sink.writeByte(9)
      sink.writeList(value) {
        writeTaggedValue(it, this)
      }
    }

    is Map<*, *> -> {
      sink.writeByte(10)
      sink.writeInt(value.size)
      value.entries.forEach { (mapKey, mapValue) ->
        writeTaggedValue(mapKey, sink)
        writeTaggedValue(mapValue, sink)
      }
    }

    is Set<*> -> {
      sink.writeByte(11)
      sink.writeList(value.toList()) {
        writeTaggedValue(it, this)
      }
    }

    is Snapshot -> {
      sink.writeByte(12)
      sink.writeByteStringWithLength(value.bytes)
    }

    is TreeSnapshot -> {
      sink.writeByte(13)
      sink.writeByteStringWithLength(value.toByteString())
    }

    is MutableState<*> -> {
      sink.writeByte(14)
      writeTaggedValue(value.value, sink)
    }
  }
}

private fun readTaggedValue(
  source: BufferedSource
): Any? {
  // Read the type tag, then read the value.
  return when (val typeTag = source.readByte().toInt()) {
    0 -> null
    1 -> source.readByte()
    2 -> source.readShort()
    3 -> source.readInt()
    4 -> source.readLong()
    5 -> source.readFloat()
    6 -> source.readDouble()
    7 -> source.readUtf8WithLength()
    8 -> source.readByteStringWithLength()
    9 -> source.readList { readTaggedValue(this) }
    10 -> {
      val size = source.readInt()
      buildMap {
        repeat(size) {
          val key = readTaggedValue(source)
          val value = readTaggedValue(source)
          put(key, value)
        }
      }
    }

    11 -> source.readList { readTaggedValue(this) }.toSet()
    12 -> Snapshot.of(source.readByteStringWithLength())
    13 -> TreeSnapshot.parse(source.readByteStringWithLength())
    14 -> mutableStateOf(readTaggedValue(source))
    else -> error("Unknown type tag encountered while parsing snapshot: $typeTag")
  }
}

private fun canBeSavedToBuffer(value: Any?): Boolean {
  if (value == null) return true

  val isPrimitive = value is Byte ||
    value is Short ||
    value is Int ||
    value is Long ||
    value is Float ||
    value is Double ||
    value is String ||
    value is ByteString
  if (isPrimitive) return true

  val isCollectionOfCompatibleElements = when (value) {
    is List<*> -> value.all(::canBeSavedToBuffer)
    is Set<*> -> value.all(::canBeSavedToBuffer)
    is Map<*, *> -> value.all { (key, value) ->
      canBeSavedToBuffer(key) && canBeSavedToBuffer(value)
    }

    else -> false
  }
  if (isCollectionOfCompatibleElements) return true

  val isComposeState = value is MutableState<*>
  if (isComposeState) return true

  val isSnapshot = value is Snapshot || value is TreeSnapshot
  return isSnapshot
}
