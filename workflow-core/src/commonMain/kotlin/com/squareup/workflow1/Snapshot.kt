@file:Suppress("EXPERIMENTAL_API_USAGE")
@file:JvmName("Snapshots")

package com.squareup.workflow1

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * A lazy wrapper of [ByteString]. Allows [Workflow]s to capture their state frequently, without
 * worrying about performing unnecessary serialization work.
 */
public class Snapshot
private constructor(private val toByteString: () -> ByteString) {

  public companion object {
    @Deprecated("Use null instead.", ReplaceWith("null"))
    @JvmField
    public val EMPTY: Snapshot = of(ByteString.EMPTY)

    @JvmStatic
    public fun of(string: String): Snapshot =
      Snapshot { string.encodeUtf8() }

    @JvmStatic
    public fun of(byteString: ByteString): Snapshot =
      Snapshot { byteString }

    @JvmStatic
    public fun of(lazy: () -> ByteString): Snapshot =
      Snapshot(lazy)

    @JvmStatic
    public fun of(integer: Int): Snapshot {
      return Snapshot {
        with(Buffer()) {
          writeInt(integer)
          readByteString()
        }
      }
    }

    /** Create a snapshot by writing to a nice ergonomic [BufferedSink]. */
    @JvmStatic
    public fun write(lazy: (BufferedSink) -> Unit): Snapshot =
      of {
        Buffer().apply(lazy)
          .readByteString()
      }
  }

  @get:JvmName("bytes")
  public val bytes: ByteString by lazy { toByteString() }

  /**
   * Returns a `String` describing the [bytes] of this `Snapshot`.
   *
   * **This method forces serialization, calling it may be expensive.**
   */
  override fun toString(): String = "Snapshot($bytes)"

  /**
   * Compares `Snapshot`s by comparing their [bytes].
   *
   * **This method forces serialization, calling it may be expensive.**
   */
  override fun equals(other: Any?): Boolean =
    (other as? Snapshot)?.let { bytes == it.bytes } ?: false

  /**
   * Calculates hashcode using [bytes].
   *
   * **This method forces serialization, calling it may be expensive.**
   */
  override fun hashCode(): Int = bytes.hashCode()
}

public fun <T : Any> BufferedSink.writeNullable(
  obj: T?,
  writer: BufferedSink.(T) -> Unit
): BufferedSink = apply {
  writeBooleanAsInt(obj != null)
  obj?.let { writer(it) }
}

public fun <T : Any> BufferedSource.readNullable(reader: BufferedSource.() -> T): T? {
  return if (readBooleanFromInt()) reader() else null
}

public fun BufferedSink.writeBooleanAsInt(bool: Boolean): BufferedSink =
  writeInt(if (bool) 1 else 0)

public fun BufferedSource.readBooleanFromInt(): Boolean = readInt() == 1

public fun BufferedSink.writeUtf8WithLength(str: String): BufferedSink {
  return writeByteStringWithLength(str.encodeUtf8())
}

public fun BufferedSource.readUtf8WithLength(): String = readByteStringWithLength().utf8()

public fun BufferedSink.writeOptionalUtf8WithLength(str: String?): BufferedSink = apply {
  writeNullable(str) { writeUtf8WithLength(it) }
}

public fun BufferedSource.readOptionalUtf8WithLength(): String? {
  return readNullable { readUtf8WithLength() }
}

public fun BufferedSink.writeByteStringWithLength(bytes: ByteString): BufferedSink = apply {
  writeInt(bytes.size)
    .write(bytes)
}

public fun BufferedSource.readByteStringWithLength(): ByteString {
  val size = readInt()
  return readByteString(size.toLong())
}

public inline fun <T> BufferedSink.writeList(
  values: List<T>,
  writer: BufferedSink.(T) -> Unit
): BufferedSink = apply {
  writeInt(values.size)
  values.forEach { writer(it) }
}

public inline fun <T> BufferedSource.readList(
  reader: BufferedSource.() -> T
): List<T> = List(readInt()) { reader() }

/**
 * Runs `block` with a `BufferedSource` that will read from this `ByteString`.
 *
 * Lets you do stuff like:
 * ```
 *   myBlob.parse {
 *     MyValueObject(
 *       name = it.readUtf8WithLength(),
 *       age = it.readInt()
 *     )
 *   }
 * ```
 */
public inline fun <T> ByteString.parse(block: (BufferedSource) -> T): T =
  block(Buffer().write(this))
