package com.squareup.workflow1

import com.squareup.workflow1.TreeSnapshot.Format.COMPOSE
import com.squareup.workflow1.TreeSnapshot.Format.LEGACY
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.id
import kotlin.random.Random
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

@OptIn(ExperimentalStdlibApi::class)
internal class TreeSnapshotTest {

  @Test
  fun overrides_equals() {
    val snapshot1 =
      TreeSnapshot(
        workflowSnapshot = Snapshot.of("foo"),
        childTreeSnapshots = {
          mapOf(Workflow1.id("bar") to TreeSnapshot.forRootOnly(Snapshot.of("baz")))
        },
      )
    val snapshot2 =
      TreeSnapshot(
        workflowSnapshot = Snapshot.of("foo"),
        childTreeSnapshots = {
          mapOf(Workflow1.id("bar") to TreeSnapshot.forRootOnly(Snapshot.of("baz")))
        },
      )
    assertEquals(snapshot1, snapshot2)
  }

  @Test
  fun serialize_and_deserialize() {
    val rootSnapshot = Snapshot.of("roo")
    val id1 = WorkflowNodeId(Workflow1)
    val id2 = WorkflowNodeId(Workflow2)
    val id3 = WorkflowNodeId(Workflow2, name = "b")
    val childSnapshots =
      mapOf(
        id1 to TreeSnapshot.forRootOnly(Snapshot.of("one")),
        id2 to TreeSnapshot.forRootOnly(Snapshot.of("two")),
        id3 to TreeSnapshot.forRootOnly(Snapshot.of("three")),
      )

    val bytes = TreeSnapshot(rootSnapshot, { childSnapshots }).toByteString()
    val treeSnapshot = TreeSnapshot.parse(bytes)

    assertEquals(rootSnapshot.bytes, treeSnapshot.workflowSnapshot?.bytes)
    assertTrue(id1 in treeSnapshot.childTreeSnapshots)
    assertTrue(id2 in treeSnapshot.childTreeSnapshots)
    assertTrue(id3 in treeSnapshot.childTreeSnapshots)

    assertEquals(
      "one",
      treeSnapshot.childTreeSnapshots.getValue(id1).workflowSnapshot!!.bytes.utf8(),
    )
    assertEquals(
      "two",
      treeSnapshot.childTreeSnapshots.getValue(id2).workflowSnapshot!!.bytes.utf8(),
    )
    assertEquals(
      "three",
      treeSnapshot.childTreeSnapshots.getValue(id3).workflowSnapshot!!.bytes.utf8(),
    )
  }

  @Test
  fun serialize_handles_single_unsnapshottable_identifier() {
    val rootSnapshot = Snapshot.of("roo")
    val id = WorkflowNodeId(UnsnapshottableWorkflow1)
    val childSnapshots = mapOf(id to TreeSnapshot.forRootOnly(Snapshot.of("one")))

    val bytes = TreeSnapshot(rootSnapshot, { childSnapshots }).toByteString()
    val treeSnapshot = TreeSnapshot.parse(bytes)

    assertEquals(rootSnapshot.bytes, treeSnapshot.workflowSnapshot?.bytes)
    assertTrue(treeSnapshot.childTreeSnapshots.isEmpty())
  }

  @Test
  fun serialize_drops_unsnapshottable_identifiers() {
    val rootSnapshot = Snapshot.of("roo")
    val id1 = WorkflowNodeId(Workflow1)
    val id2 = WorkflowNodeId(UnsnapshottableWorkflow1)
    val id3 = WorkflowNodeId(Workflow2, name = "b")
    val id4 = WorkflowNodeId(UnsnapshottableWorkflow2, name = "c")
    val childSnapshots =
      mapOf(
        id1 to TreeSnapshot.forRootOnly(Snapshot.of("one")),
        id2 to TreeSnapshot.forRootOnly(Snapshot.of("two")),
        id3 to TreeSnapshot.forRootOnly(Snapshot.of("three")),
        id4 to TreeSnapshot.forRootOnly(Snapshot.of("four")),
      )

    val bytes = TreeSnapshot(rootSnapshot, { childSnapshots }).toByteString()
    val treeSnapshot = TreeSnapshot.parse(bytes)

    assertEquals(rootSnapshot.bytes, treeSnapshot.workflowSnapshot?.bytes)
    assertTrue(id1 in treeSnapshot.childTreeSnapshots)
    assertTrue(id2 !in treeSnapshot.childTreeSnapshots)
    assertTrue(id3 in treeSnapshot.childTreeSnapshots)
    assertTrue(id4 !in treeSnapshot.childTreeSnapshots)

    assertEquals(
      "one",
      treeSnapshot.childTreeSnapshots.getValue(id1).workflowSnapshot!!.bytes.utf8(),
    )
    assertEquals(
      "three",
      treeSnapshot.childTreeSnapshots.getValue(id3).workflowSnapshot!!.bytes.utf8(),
    )
  }

  @Test
  fun equals_considers_format() {
    val legacy = TreeSnapshot(Snapshot.of("foo"), ::emptyMap, format = LEGACY)
    val compose = TreeSnapshot(Snapshot.of("foo"), ::emptyMap, format = COMPOSE)
    assertNotEquals(legacy, compose)
  }

  @Test
  fun format_defaults_to_legacy() {
    assertEquals(LEGACY, TreeSnapshot(Snapshot.of("foo"), ::emptyMap).format)
    assertEquals(LEGACY, TreeSnapshot.forRootOnly(Snapshot.of("foo")).format)
  }

  @Test
  fun format_round_trips() {
    val legacy = TreeSnapshot(Snapshot.of("foo"), ::emptyMap, format = LEGACY)
    val compose = TreeSnapshot(Snapshot.of("foo"), ::emptyMap, format = COMPOSE)

    assertEquals(legacy, TreeSnapshot.parse(legacy.toByteString()))
    assertEquals(compose, TreeSnapshot.parse(compose.toByteString()))
  }

  @Test
  fun takeIfFormat_filters_by_format() {
    val compose = TreeSnapshot(Snapshot.of("foo"), ::emptyMap, format = COMPOSE)
    assertSame(compose, compose.takeIfFormat(COMPOSE))
    assertNull(compose.takeIfFormat(LEGACY))
  }

  @Test
  fun parse_empty_bytes_returns_empty_snapshot() {
    assertEmpty(TreeSnapshot.parse(ByteString.EMPTY))
  }

  @Test
  fun parse_unknown_magic_returns_empty_snapshot() {
    // A snapshot written before formats were introduced starts with a non-negative root length.
    val preFormatBytes =
      Buffer()
        .apply {
          writeByteStringWithLength(Snapshot.of("roo").bytes)
          writeInt(0)
        }
        .readByteString()
    assertEmpty(TreeSnapshot.parse(preFormatBytes))

    assertEmpty(TreeSnapshot.parse(Random(0).nextBytes(64).toByteString()))
  }

  @Test
  fun parse_bad_root_length_returns_empty_snapshot() {
    val negativeLength = Buffer().writeInt(LEGACY.magic).writeInt(-1).readByteString()
    assertEmpty(TreeSnapshot.parse(negativeLength))

    val oversizedLength =
      Buffer().writeInt(LEGACY.magic).writeInt(Int.MAX_VALUE).writeInt(42).readByteString()
    assertEmpty(TreeSnapshot.parse(oversizedLength))
  }

  @Test
  fun parse_bad_child_count_returns_empty_snapshot() {
    fun bytesWithChildCount(count: Int) =
      Buffer()
        .apply {
          writeInt(LEGACY.magic)
          writeByteStringWithLength(Snapshot.of("roo").bytes)
          writeInt(count)
        }
        .readByteString()

    assertEmpty(TreeSnapshot.parse(bytesWithChildCount(-1)))
    assertEmpty(TreeSnapshot.parse(bytesWithChildCount(Int.MAX_VALUE)))
  }

  @Test
  fun parse_truncated_child_list_returns_empty_snapshot() {
    val rootSnapshot = Snapshot.of("roo")
    val childSnapshots =
      mapOf(WorkflowNodeId(Workflow1) to TreeSnapshot.forRootOnly(Snapshot.of("one")))
    val bytes = TreeSnapshot(rootSnapshot, { childSnapshots }).toByteString()
    // Cut off in the middle of the child entry: after the magic, root, child count, and part of
    // the child id. The root is intact, but a partial restore would be surprising, so the whole
    // snapshot is dropped.
    val truncated = bytes.substring(0, 4 + 4 + rootSnapshot.bytes.size + 4 + 2)

    assertEmpty(TreeSnapshot.parse(truncated))
  }

  @Test
  fun parse_invalid_child_id_returns_empty_snapshot() {
    val bytes =
      Buffer()
        .apply {
          writeInt(LEGACY.magic)
          writeByteStringWithLength(Snapshot.of("roo").bytes)
          writeInt(1)
          writeByteStringWithLength(Snapshot.of("not an id").bytes)
          writeByteStringWithLength(TreeSnapshot.forRootOnly(Snapshot.of("one")).toByteString())
        }
        .readByteString()

    assertEmpty(TreeSnapshot.parse(bytes))
  }

  @Test
  fun parse_child_in_different_format_returns_empty_snapshot() {
    val child = TreeSnapshot(Snapshot.of("one"), ::emptyMap, format = COMPOSE)
    val bytes =
      Buffer()
        .apply {
          writeInt(LEGACY.magic)
          writeByteStringWithLength(Snapshot.of("roo").bytes)
          writeInt(1)
          writeByteStringWithLength(WorkflowNodeId(Workflow1).toByteStringOrNull()!!)
          writeByteStringWithLength(child.toByteString())
        }
        .readByteString()

    assertEmpty(TreeSnapshot.parse(bytes))
  }

  @Test
  fun parse_corrupt_grandchild_returns_empty_snapshot() {
    val grandchildBytes = Buffer().writeInt(LEGACY.magic).writeInt(-1).readByteString()
    val childBytes =
      Buffer()
        .apply {
          writeInt(LEGACY.magic)
          writeByteStringWithLength(Snapshot.of("one").bytes)
          writeInt(1)
          writeByteStringWithLength(WorkflowNodeId(Workflow2).toByteStringOrNull()!!)
          writeByteStringWithLength(grandchildBytes)
        }
        .readByteString()
    val bytes =
      Buffer()
        .apply {
          writeInt(LEGACY.magic)
          writeByteStringWithLength(Snapshot.of("roo").bytes)
          writeInt(1)
          writeByteStringWithLength(WorkflowNodeId(Workflow1).toByteStringOrNull()!!)
          writeByteStringWithLength(childBytes)
        }
        .readByteString()

    assertEmpty(TreeSnapshot.parse(bytes))
  }

  private fun assertEmpty(treeSnapshot: TreeSnapshot) {
    assertNull(treeSnapshot.workflowSnapshot)
    assertTrue(treeSnapshot.childTreeSnapshots.isEmpty())
  }

  @Test
  fun empty_root_is_converted_to_null() {
    val rootSnapshot = Snapshot.of(ByteString.EMPTY)
    val treeSnapshot = TreeSnapshot(rootSnapshot, ::emptyMap)

    assertNull(treeSnapshot.workflowSnapshot)
  }

  private object Workflow1 : Workflow<Unit, Nothing, Unit> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> = fail()
  }

  private object Workflow2 : Workflow<Unit, Nothing, Unit> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> = fail()
  }

  private object UnsnapshottableWorkflow1 : Workflow<Unit, Nothing, Unit>, ImpostorWorkflow {
    override val realIdentifier = unsnapshottableIdentifier(typeOf<String>())

    override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> = fail()
  }

  private object UnsnapshottableWorkflow2 : Workflow<Unit, Nothing, Unit>, ImpostorWorkflow {
    override val realIdentifier = unsnapshottableIdentifier(typeOf<String>())

    override fun asStatefulWorkflow(): StatefulWorkflow<Unit, *, Nothing, Unit> = fail()
  }
}
