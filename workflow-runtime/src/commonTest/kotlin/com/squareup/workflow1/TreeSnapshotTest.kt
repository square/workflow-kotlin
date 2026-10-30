package com.squareup.workflow1

import com.squareup.workflow1.TreeSnapshot.Format.COMPOSE
import com.squareup.workflow1.TreeSnapshot.Format.LEGACY
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.id
import kotlin.random.Random
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
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
  fun parse_bytes_shorter_than_magic_returns_empty_snapshot() {
    assertEmpty(TreeSnapshot.parse(Buffer().writeShort(42).readByteString()))
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

  /**
   * Only the magic number is validated. Corrupt bytes after a valid magic number fail the same way
   * they did before formats were introduced.
   */
  @Test
  fun parse_corrupt_bytes_after_valid_magic_throws() {
    val negativeRootLength = Buffer().writeInt(LEGACY.magic).writeInt(-1).readByteString()
    assertFails { TreeSnapshot.parse(negativeRootLength) }

    val truncatedRoot = Buffer().writeInt(LEGACY.magic).writeInt(42).readByteString()
    assertFails { TreeSnapshot.parse(truncatedRoot) }
  }

  @Test
  fun parse_corrupt_child_list_after_valid_magic_throws_lazily() {
    val rootSnapshot = Snapshot.of("roo")
    val childSnapshots =
      mapOf(WorkflowNodeId(Workflow1) to TreeSnapshot.forRootOnly(Snapshot.of("one")))
    val bytes = TreeSnapshot(rootSnapshot, { childSnapshots }).toByteString()
    // Cut off in the middle of the child entry: after the magic, root, child count, and part of
    // the child id.
    val truncated = bytes.substring(0, 4 + 4 + rootSnapshot.bytes.size + 4 + 2)

    // The root is still parsed, since parsing is lazy below the root.
    val treeSnapshot = TreeSnapshot.parse(truncated)
    assertEquals(rootSnapshot.bytes, treeSnapshot.workflowSnapshot?.bytes)

    assertFails { treeSnapshot.childTreeSnapshots }
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
