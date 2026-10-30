package com.squareup.workflow1

import com.squareup.workflow1.TreeSnapshot.Companion.forRootOnly
import com.squareup.workflow1.TreeSnapshot.Companion.parse
import com.squareup.workflow1.TreeSnapshot.Format.LEGACY
import com.squareup.workflow1.internal.WorkflowNodeId
import kotlin.LazyThreadSafetyMode.NONE
import okio.Buffer
import okio.ByteString

/**
 * Aggregate of all the snapshots of a tree of workflows.
 *
 * Can be serialized with [toByteString] and deserialized with [parse].
 *
 * For tests, you can get a [TreeSnapshot] from a [RenderingAndSnapshot] or by creating one for your
 * root workflow only by calling [forRootOnly].
 *
 * Each runtime writes its own [Format], and a runtime ignores any `TreeSnapshot` that isn't in the
 * format it reads, initializing the workflow tree as if no snapshot had been provided. Bytes that
 * can't be parsed are treated the same way. Workflows' own snapshot bytes are passed through to
 * them unchanged, so a workflow is still responsible for validating what it wrote in
 * `snapshotState`.
 *
 * @param childTreeSnapshots A function that will lazily compute the child snapshots for this tree
 *   when invoked. It will be cached in the [childTreeSnapshots] property.
 * @param format The [Format] this snapshot was written in. Defaults to [LEGACY], which is the
 *   format [forRootOnly] uses.
 * @constructor
 */
public class TreeSnapshot
internal constructor(
  workflowSnapshot: Snapshot?,
  childTreeSnapshots: () -> Map<WorkflowNodeId, TreeSnapshot>,
  internal val format: Format = LEGACY,
) {
  /**
   * Identifies which runtime wrote a [TreeSnapshot], and therefore how its [workflowSnapshot] and
   * [childTreeSnapshots] should be interpreted. Serialized as a magic number at the start of
   * [toByteString].
   *
   * All magic numbers are negative so they can never be mistaken for the non-negative length prefix
   * that a snapshot written before formats were introduced starts with.
   */
  internal enum class Format(internal val magic: Int) {
    /**
     * Written by the traditional runtime: [workflowSnapshot] is the root workflow's own
     * `snapshotState`, and [childTreeSnapshots] contains the subtrees of its children.
     */
    LEGACY(magic = 0xC0DE_0001.toInt()),

    /**
     * Written by the compose runtime: [workflowSnapshot] is the serialized saveable state registry
     * for the entire tree, and [childTreeSnapshots] is always empty.
     */
    COMPOSE(magic = 0xC0DE_0002.toInt());

    internal companion object {
      fun forMagic(magic: Int): Format? = entries.firstOrNull { it.magic == magic }
    }
  }

  /**
   * The [Snapshot] for the root workflow, or null if that snapshot was empty or unspecified.
   * Computed lazily to avoid serializing the snapshot until necessary.
   */
  internal val workflowSnapshot: Snapshot? by
    lazy(NONE) { workflowSnapshot?.takeUnless { it.bytes.size == 0 } }

  /** The map of child snapshots by child [WorkflowNodeId]. */
  internal val childTreeSnapshots: Map<WorkflowNodeId, TreeSnapshot> by
    lazy(NONE, childTreeSnapshots)

  /**
   * Returns this snapshot if it was written in [format], else null. A runtime uses this to ignore
   * snapshots written by the other runtime.
   */
  internal fun takeIfFormat(format: Format): TreeSnapshot? = takeIf { it.format == format }

  /**
   * Writes this [Snapshot] and all its children into a [ByteString]. The snapshot can be restored
   * with [parse].
   *
   * Any children snapshots for workflows whose [WorkflowIdentifier]s are
   * [unsnapshottable][unsnapshottableIdentifier] will not be serialized.
   */
  public fun toByteString(): ByteString =
    Buffer().let { sink ->
      sink.writeInt(format.magic)
      sink.writeByteStringWithLength(workflowSnapshot?.bytes ?: ByteString.EMPTY)
      val childBytes: List<Pair<ByteString, ByteString>> =
        childTreeSnapshots.mapNotNull { (childId, childSnapshot) ->
          val childIdBytes = childId.toByteStringOrNull() ?: return@mapNotNull null
          val childSnapshotBytes =
            childSnapshot.toByteString().takeUnless { it.size == 0 } ?: return@mapNotNull null
          return@mapNotNull Pair(childIdBytes, childSnapshotBytes)
        }
      sink.writeInt(childBytes.size)
      childBytes.forEach { (childIdBytes, childSnapshotBytes) ->
        sink.writeByteStringWithLength(childIdBytes)
        sink.writeByteStringWithLength(childSnapshotBytes)
      }
      sink.readByteString()
    }

  public fun workflowSnapshotByteString(): ByteString? {
    return workflowSnapshot?.bytes
  }

  override fun equals(other: Any?): Boolean =
    when {
      other === this -> true
      other !is TreeSnapshot -> false
      else ->
        other.format == format &&
          other.workflowSnapshot == workflowSnapshot &&
          other.childTreeSnapshots == childTreeSnapshots
    }

  override fun hashCode(): Int {
    var result = format.hashCode()
    result = 31 * result + workflowSnapshot.hashCode()
    result = 31 * result + childTreeSnapshots.hashCode()
    return result
  }

  public companion object {
    /**
     * A snapshot with no root snapshot and no children. Restoring from it is the same as restoring
     * from no snapshot at all.
     */
    internal val EMPTY: TreeSnapshot = TreeSnapshot(workflowSnapshot = null, ::emptyMap)

    /**
     * Returns a [TreeSnapshot] that only contains a [Snapshot] for the root workflow, and no child
     * snapshots.
     */
    public fun forRootOnly(rootSnapshot: Snapshot?): TreeSnapshot =
      TreeSnapshot(rootSnapshot, ::emptyMap)

    /**
     * Parses a "root" snapshot and the list of child snapshots with associated [WorkflowNodeId]s
     * from a [ByteString] returned by [toByteString].
     *
     * Never returns an empty root snapshot: if the root snapshot is empty it will be null.
     *
     * This method never throws for invalid input. Persisted bytes can be corrupted, truncated, or
     * written by an incompatible version of this library, and in all those cases the runtime should
     * start from scratch rather than crash or restore part of the tree. The structure of the entire
     * tree is validated eagerly, and if any part of it can't be read (an unknown [Format], a bad
     * length prefix, an unparseable child id, a child in a different format from its parent, etc.)
     * this returns a snapshot with no root and no children. Individual workflows' snapshot bytes
     * are opaque to this method and are not validated.
     */
    public fun parse(bytes: ByteString): TreeSnapshot =
      try {
        parseOrThrow(bytes, expectedFormat = null)
      } catch (e: Exception) {
        EMPTY
      }

    /**
     * Parses [bytes] like [parse], but throws if any part of the tree is invalid. When
     * [expectedFormat] is non-null, the snapshot must be in that format: children are always
     * written in the same format as their parent.
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun parseOrThrow(bytes: ByteString, expectedFormat: Format?): TreeSnapshot =
      bytes.parse { source ->
        val magic = source.readInt()
        val format =
          requireNotNull(Format.forMagic(magic)) { "Unknown TreeSnapshot format: $magic" }
        require(expectedFormat == null || format == expectedFormat) {
          "Expected child TreeSnapshot in format $expectedFormat but was $format"
        }
        val rootSnapshot = source.readByteStringWithLength()
        val childSnapshotCount = source.readInt()
        // Every child entry takes at least 8 bytes (two length prefixes), so a count larger than
        // that is corrupt. Checking before allocating the map avoids a huge allocation.
        require(childSnapshotCount in 0..(source.buffer.size / 8)) {
          "Invalid child snapshot count: $childSnapshotCount"
        }
        val childTreeSnapshots =
          buildMap(childSnapshotCount) {
            for (i in 0 until childSnapshotCount) {
              val idBytes = source.readByteStringWithLength()
              val id = WorkflowNodeId.parse(idBytes)
              val childSnapshot = source.readByteStringWithLength()
              this[id] = parseOrThrow(childSnapshot, expectedFormat = format)
            }
          }
        TreeSnapshot(
          workflowSnapshot = Snapshot.of(rootSnapshot),
          childTreeSnapshots = { childTreeSnapshots },
          format = format,
        )
      }
  }
}
