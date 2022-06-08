package com.squareup.workflow1

import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.id
import okio.ByteString
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalStdlibApi::class)
internal class TreeSnapshotTest {

  @Test fun `overrides equals`() {
    val snapshot1 = TreeSnapshot(
      workflowSnapshot = Snapshot.of("foo"),
      childTreeSnapshots = {
        mapOf(Workflow1.id("bar") to TreeSnapshot.forRootOnly(Snapshot.of("baz")))
      }
    )
    val snapshot2 = TreeSnapshot(
      workflowSnapshot = Snapshot.of("foo"),
      childTreeSnapshots = {
        mapOf(Workflow1.id("bar") to TreeSnapshot.forRootOnly(Snapshot.of("baz")))
      }
    )
    assertEquals(snapshot1, snapshot2)
  }

  @Test fun `serialize and deserialize`() {
    val rootSnapshot = Snapshot.of("roo")
    val id1 = WorkflowNodeId(Workflow1)
    val id2 = WorkflowNodeId(Workflow2)
    val id3 = WorkflowNodeId(Workflow2, name = "b")
    val childSnapshots = mapOf(
      id1 to TreeSnapshot.forRootOnly(Snapshot.of("one")),
      id2 to TreeSnapshot.forRootOnly(Snapshot.of("two")),
      id3 to TreeSnapshot.forRootOnly(Snapshot.of("three"))
    )

    val bytes = TreeSnapshot(rootSnapshot) { childSnapshots }.toByteString()
    val treeSnapshot = TreeSnapshot.parse(bytes)

    assertEquals(rootSnapshot.bytes, treeSnapshot.workflowSnapshot?.bytes)
    assertTrue(id1 in treeSnapshot.childTreeSnapshots)
    assertTrue(id2 in treeSnapshot.childTreeSnapshots)
    assertTrue(id3 in treeSnapshot.childTreeSnapshots)

    assertEquals(
      "one", treeSnapshot.childTreeSnapshots.getValue(id1).workflowSnapshot!!.bytes.utf8()
    )
    assertEquals(
      "two", treeSnapshot.childTreeSnapshots.getValue(id2).workflowSnapshot!!.bytes.utf8()
    )
    assertEquals(
      "three", treeSnapshot.childTreeSnapshots.getValue(id3).workflowSnapshot!!.bytes.utf8()
    )
  }

  @Test fun `serialize handles single unsnapshottable identifier`() {
    val rootSnapshot = Snapshot.of("roo")
    val id = WorkflowNodeId(UnsnapshottableWorkflow1)
    val childSnapshots = mapOf(id to TreeSnapshot.forRootOnly(Snapshot.of("one")))

    val bytes = TreeSnapshot(rootSnapshot) { childSnapshots }.toByteString()
    val treeSnapshot = TreeSnapshot.parse(bytes)

    assertEquals(rootSnapshot.bytes, treeSnapshot.workflowSnapshot?.bytes)
    assertTrue(treeSnapshot.childTreeSnapshots.isEmpty())
  }

  @Test fun `serialize drops unsnapshottable identifiers`() {
    val rootSnapshot = Snapshot.of("roo")
    val id1 = WorkflowNodeId(Workflow1)
    val id2 = WorkflowNodeId(UnsnapshottableWorkflow1)
    val id3 = WorkflowNodeId(Workflow2, name = "b")
    val id4 = WorkflowNodeId(UnsnapshottableWorkflow2, name = "c")
    val childSnapshots = mapOf(
      id1 to TreeSnapshot.forRootOnly(Snapshot.of("one")),
      id2 to TreeSnapshot.forRootOnly(Snapshot.of("two")),
      id3 to TreeSnapshot.forRootOnly(Snapshot.of("three")),
      id4 to TreeSnapshot.forRootOnly(Snapshot.of("four"))
    )

    val bytes = TreeSnapshot(rootSnapshot) { childSnapshots }.toByteString()
    val treeSnapshot = TreeSnapshot.parse(bytes)

    assertEquals(rootSnapshot.bytes, treeSnapshot.workflowSnapshot?.bytes)
    assertTrue(id1 in treeSnapshot.childTreeSnapshots)
    assertTrue(id2 !in treeSnapshot.childTreeSnapshots)
    assertTrue(id3 in treeSnapshot.childTreeSnapshots)
    assertTrue(id4 !in treeSnapshot.childTreeSnapshots)

    assertEquals(
      "one", treeSnapshot.childTreeSnapshots.getValue(id1).workflowSnapshot!!.bytes.utf8()
    )
    assertEquals(
      "three", treeSnapshot.childTreeSnapshots.getValue(id3).workflowSnapshot!!.bytes.utf8()
    )
  }

  @Test fun `empty root is converted to null`() {
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
