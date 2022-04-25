package com.squareup.workflow1.internal

import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

internal class ActiveStagingListTest {

  @Test fun `retainOrCreate on empty list creates new item`() {
    val list = ActiveStagingList<Node>()

    list.retainOrCreate(predicate = { true }, create = { Node("foo") })

    assertEquals(listOf("foo"), list.staging())
    assertEquals(emptyList(), list.active())
  }

  @Test fun `retainOrCreate with matching predicate moves item`() {
    val list = ActiveStagingList<Node>()
    list.retainOrCreate(predicate = { true }, create = { Node("foo") })
    list.commitStaging { /* Noop */ }

    list.retainOrCreate(predicate = { it.data == "foo" }, create = { Node("bar") })

    assertEquals(listOf("foo"), list.staging())
    assertEquals(emptyList(), list.active())
  }

  @Test fun `retainOrCreate with no matching predicate creates item`() {
    val list = ActiveStagingList<Node>()
    list.retainOrCreate(predicate = { true }, create = { Node("foo") })
    list.commitStaging { /* Noop */ }

    list.retainOrCreate(predicate = { it.data == "bar" }, create = { Node("bar") })

    assertEquals(listOf("bar"), list.staging())
    assertEquals(listOf("foo"), list.active())
  }

  @Test fun `commitStaging on empty lists`() {
    val list = ActiveStagingList<Node>()

    list.commitStaging { /* Noop */ }
  }

  @Test fun `commitStaging processes inactive items`() {
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
