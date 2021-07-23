package com.squareup.workflow1

import okio.BufferedSink
import okio.BufferedSource
import java.lang.Float.floatToRawIntBits
import java.lang.Float.intBitsToFloat

public fun BufferedSink.writeFloat(float: Float): BufferedSink = writeInt(floatToRawIntBits(float))

public fun BufferedSource.readFloat(): Float = intBitsToFloat(readInt())

public inline fun <reified T : Enum<T>> BufferedSource.readOptionalEnumByOrdinal(): T? {
  return readNullable { readEnumByOrdinal<T>() }
}

public fun <T : Enum<T>> BufferedSink.writeOptionalEnumByOrdinal(enumVal: T?): BufferedSink {
  return writeNullable(enumVal) { writeEnumByOrdinal(it) }
}

public inline fun <reified T : Enum<T>> BufferedSource.readEnumByOrdinal(): T {
  return T::class.java.enumConstants[readInt()]
}

public fun <T : Enum<T>> BufferedSink.writeEnumByOrdinal(enumVal: T): BufferedSink {
  return writeInt(enumVal.ordinal)
}
