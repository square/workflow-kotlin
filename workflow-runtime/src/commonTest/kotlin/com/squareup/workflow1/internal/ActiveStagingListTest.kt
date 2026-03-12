package com.squareup.workflow1.internal

import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.test.fail

internal class ActiveStagingListTest {

  @OptIn(WorkflowExperimentalRuntime::class)
  @Test
  fun identityIndexImplementation_prefers_scatter_when_multiple_backends_enabled() {
    val implementation = setOf(
      RuntimeConfigOptions.INDEXED_ACTIVE_STAGING_LISTS,
      RuntimeConfigOptions.SIMPLE_ARRAY_MAP_ACTIVE_STAGING_LIST_INDEXES,
      RuntimeConfigOptions.SCATTER_MAP_ACTIVE_STAGING_LIST_INDEXES,
    ).identityIndexImplementation()

    assertEquals(IdentityIndexImplementation.SCATTER_MAP, implementation)
  }

  @Test fun retainOrCreate_on_empty_list_creates_new_item() {
    val list = ActiveStagingList<Node>()

    list.retainOrCreate(predicate = { true }, create = { Node("foo") })

    assertEquals(listOf("foo"), list.staging())
    assertEquals(emptyList(), list.active())
  }

  @Test fun retainOrCreate_with_matching_predicate_moves_item() {
    val list = ActiveStagingList<Node>()
    list.retainOrCreate(predicate = { true }, create = { Node("foo") })
    list.commitStaging { }

    list.retainOrCreate(predicate = { it.data == "foo" }, create = { Node("bar") })

    assertEquals(listOf("foo"), list.staging())
    assertEquals(emptyList(), list.active())
  }

  @Test fun retainOrCreate_with_no_matching_predicate_creates_item() {
    val list = ActiveStagingList<Node>()
    list.retainOrCreate(predicate = { true }, create = { Node("foo") })
    list.commitStaging { }

    list.retainOrCreate(predicate = { it.data == "bar" }, create = { Node("bar") })

    assertEquals(listOf("bar"), list.staging())
    assertEquals(listOf("foo"), list.active())
  }

  @Test fun commitStaging_on_empty_lists() {
    val list = ActiveStagingList<Node>()

    list.commitStaging { }
  }

  @Test fun commitStaging_processes_inactive_items() {
    val discardedItems = mutableListOf<String>()
    val list = ActiveStagingList<Node>()
    list.retainOrCreate(predicate = { false }, create = { Node("foo") })
    list.retainOrCreate(predicate = { false }, create = { Node("bar") })
    list.commitStaging { discardedItems += it.data }
    assertEquals(emptyList<String>(), discardedItems)

    list.retainOrCreate(predicate = { it.data == "foo" }, create = { fail() })
    list.commitStaging { discardedItems += it.data }

    assertEquals(listOf("bar"), discardedItems)
  }

  @Test fun retainOrCreateByIdentity_with_matching_active_identity_moves_item() {
    val list = ActiveStagingList<Node>(identityOf = { it.data })
    list.retainOrCreateByIdentity(identity = "foo") { Node("foo") }
    list.commitStaging { }

    var createCalled = false
    val node = list.retainOrCreateByIdentity(identity = "foo") {
      createCalled = true
      Node("should-not-create")
    }

    assertFalse(createCalled)
    assertEquals("foo", node.data)
    assertEquals(listOf("foo"), list.staging())
    assertEquals(emptyList(), list.active())
  }

  @Test fun retainOrCreateByIdentity_throws_for_duplicate_staging_identity() {
    val list = ActiveStagingList<Node>(identityOf = { it.data })
    list.retainOrCreateByIdentity(identity = "foo") { Node("foo") }

    val error = assertFailsWith<IllegalArgumentException> {
      list.retainOrCreateByIdentity(identity = "foo") { Node("foo-2") }
    }

    assertEquals("Expected identities to be unique in staging: \"foo\"", error.message)
  }

  @Test fun indexed_identity_membership_tracks_active_and_staging_across_commit() {
    val list = ActiveStagingList<Node>(identityOf = { it.data })

    list.retainOrCreateByIdentity(identity = "foo") { Node("foo") }
    assertTrue(list.containsStagingIdentity("foo"))
    assertFalse(list.containsActiveIdentity("foo"))

    list.commitStaging { }
    assertFalse(list.containsStagingIdentity("foo"))
    assertTrue(list.containsActiveIdentity("foo"))
  }

  @Test fun indexed_identity_removes_dropped_active_nodes_after_commit() {
    val list = ActiveStagingList<Node>(identityOf = { it.data })
    list.retainOrCreateByIdentity(identity = "foo") { Node("foo") }
    val originalBar = list.retainOrCreateByIdentity(identity = "bar") { Node("bar") }
    list.commitStaging { }

    val dropped = mutableListOf<String>()
    list.retainOrCreateByIdentity(identity = "foo") { fail("expected retain") }
    list.commitStaging { dropped += it.data }

    assertEquals(listOf("bar"), dropped)
    assertTrue(list.containsActiveIdentity("foo"))
    assertFalse(list.containsActiveIdentity("bar"))

    var created = false
    val bar = list.retainOrCreateByIdentity(identity = "bar") {
      created = true
      Node("bar")
    }

    assertTrue(created)
    assertEquals("bar", bar.data)
    assertNotSame(originalBar, bar)
  }

  @Test fun indexed_identity_scatter_backend_matches_stdlib_behavior() {
    val list = ActiveStagingList(
      identityOf = { node: Node -> node.data },
      identityIndexImplementation = IdentityIndexImplementation.SCATTER_MAP,
    )
    list.retainOrCreateByIdentity(identity = "foo") { Node("foo") }
    list.retainOrCreateByIdentity(identity = "bar") { Node("bar") }

    val duplicateError = assertFailsWith<IllegalArgumentException> {
      list.retainOrCreateByIdentity(identity = "foo") { Node("foo-2") }
    }
    assertEquals("Expected identities to be unique in staging: \"foo\"", duplicateError.message)

    list.commitStaging { }
    assertTrue(list.containsActiveIdentity("foo"))
    assertTrue(list.containsActiveIdentity("bar"))

    val dropped = mutableListOf<String>()
    list.retainOrCreateByIdentity(identity = "foo") { fail("expected retain") }
    list.commitStaging { dropped += it.data }

    assertEquals(listOf("bar"), dropped)
    assertTrue(list.containsActiveIdentity("foo"))
    assertFalse(list.containsActiveIdentity("bar"))
  }

  @Test fun indexed_identity_simple_array_map_backend_matches_stdlib_behavior() {
    val list = ActiveStagingList(
      identityOf = { node: Node -> node.data },
      identityIndexImplementation = IdentityIndexImplementation.SIMPLE_ARRAY_MAP,
    )
    list.retainOrCreateByIdentity(identity = "foo") { Node("foo") }
    list.retainOrCreateByIdentity(identity = "bar") { Node("bar") }

    val duplicateError = assertFailsWith<IllegalArgumentException> {
      list.retainOrCreateByIdentity(identity = "foo") { Node("foo-2") }
    }
    assertEquals("Expected identities to be unique in staging: \"foo\"", duplicateError.message)

    list.commitStaging { }
    assertTrue(list.containsActiveIdentity("foo"))
    assertTrue(list.containsActiveIdentity("bar"))

    val dropped = mutableListOf<String>()
    list.retainOrCreateByIdentity(identity = "foo") { fail("expected retain") }
    list.commitStaging { dropped += it.data }

    assertEquals(listOf("bar"), dropped)
    assertTrue(list.containsActiveIdentity("foo"))
    assertFalse(list.containsActiveIdentity("bar"))
  }

  private fun ActiveStagingList<Node>.active() =
    mutableListOf<String>()
      .also { collector -> forEachActive { collector += it.data } }
      .toList()

  private fun ActiveStagingList<Node>.staging() =
    mutableListOf<String>()
      .also { collector -> forEachStaging { collector += it.data } }
      .toList()

  private class Node(val data: String) : InlineListNode<Node> {
    override var nextListNode: Node? = null
  }
}
