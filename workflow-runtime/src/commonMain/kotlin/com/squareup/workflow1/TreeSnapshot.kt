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
 * don't start with a known format are treated the same way; see [parse] for what is and isn't
 * validated.
 *
 * @param childTreeSnapshots A function that will lazily parse the child snapshots for this tree
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

  /**
   * The map of child snapshots by child [WorkflowNodeId]. Computed lazily so the entire snapshot
   * tree isn't parsed upfront.
   */
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
     * Never returns an empty root snapshot: if the root snapshot is empty it will be null. Child
     * snapshots, however, are always returned as-is. They must be recursively passed to this
     * function to continue parsing the tree.
     *
     * Note that this method is mostly lazy. It will parse the list of child [TreeSnapshot]s, but
     * will not recursively parse each of those.
     *
     * If [bytes] doesn't start with the magic number of a known [Format], e.g. because it was
     * written by an incompatible version of this library or is not a snapshot at all, this returns
     * a snapshot with no root and no children, so the runtime starts from scratch instead of
     * crashing. Only the magic number is checked: validating the rest of the tree would mean
     * reading all the bytes twice on startup, and workflows' own snapshot bytes can't be validated
     * up front anyway. If the magic number matches but the rest of the bytes are corrupt, parsing
     * fails the same way it always has, either here or when a child's snapshot is read during
     * restore.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public fun parse(bytes: ByteString): TreeSnapshot {
      if (bytes.size < 4) return EMPTY
      return bytes.parse { source ->
        val format = Format.forMagic(source.readInt()) ?: return@parse EMPTY
        val rootSnapshot = source.readByteStringWithLength()
        return TreeSnapshot(
          workflowSnapshot = Snapshot.of(rootSnapshot),
          format = format,
          childTreeSnapshots = {
            val childSnapshotCount = source.readInt()
            buildMap(childSnapshotCount) {
              for (i in 0 until childSnapshotCount) {
                val idBytes = source.readByteStringWithLength()
                val id = WorkflowNodeId.parse(idBytes)
                val childSnapshot = source.readByteStringWithLength()
                this[id] = parse(childSnapshot)
              }
            }
          },
        )
      }
    }
  }
}
