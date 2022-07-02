package com.squareup.workflow1.internal

import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class InlineLinkedListTest {

  @Test fun forEach_empty_list() {
    val list = InlineLinkedList<StringElement>()
    var count = 0
    list.forEach { count++ }
    assertEquals(0, count)
  }

  @Test fun plusAssign_on_empty_list() {
    val list = InlineLinkedList<StringElement>()

    list += StringElement("foo")

    assertEquals(listOf("foo"), list.toList())
  }

  @Test fun removeFirst_on_empty_list() {
    val list = InlineLinkedList<StringElement>()

    list.removeFirst { true }

    assertEquals(emptyList(), list.toList())
  }

  @Test fun removeFirst_on_single_item_list() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")

    assertEquals("foo", list.removeFirst { it.value == "foo" }?.value)

    assertEquals(emptyList(), list.toList())
  }

  @Test fun removeFirst_head_on_list_with_2_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")

    assertEquals("foo", list.removeFirst { it.value == "foo" }?.value)

    assertEquals(listOf("bar"), list.toList())
  }

  @Test fun removeFirst_tail_on_list_with_2_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")

    assertEquals("bar", list.removeFirst { it.value == "bar" }?.value)

    assertEquals(listOf("foo"), list.toList())
  }

  @Test fun removeFirst_head_on_list_with_3_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list += StringElement("baz")

    assertEquals("foo", list.removeFirst { it.value == "foo" }?.value)

    assertEquals(listOf("bar", "baz"), list.toList())
  }

  @Test fun removeFirst_middle_on_list_with_3_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list += StringElement("baz")

    assertEquals("bar", list.removeFirst { it.value == "bar" }?.value)

    assertEquals(listOf("foo", "baz"), list.toList())
  }

  @Test fun removeFirst_tail_on_list_with_3_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list += StringElement("baz")

    assertEquals("baz", list.removeFirst { it.value == "baz" }?.value)

    assertEquals(listOf("foo", "bar"), list.toList())
  }

  @Test fun removeFirst_when_multiple_matches() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("foo")
    list += StringElement("bar")

    assertEquals("foo", list.removeFirst { it.value == "foo" }?.value)

    assertEquals(listOf("foo", "bar"), list.toList())
  }

  @Test fun removeFirst_when_no_matches() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")

    assertNull(list.removeFirst { it.value == "baz" })

    assertEquals(listOf("foo", "bar"), list.toList())
  }

  @Test fun plusAssign_on_non_empty_list() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")

    list += StringElement("bar")

    assertEquals(listOf("foo", "bar"), list.toList())
  }

  @Test fun plusAssign_after_remove_head_with_2_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list.removeFirst { it.value == "foo" }

    list += StringElement("buzz")

    assertEquals(listOf("bar", "buzz"), list.toList())
  }

  @Test fun plusAssign_after_remove_tail_with_2_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list.removeFirst { it.value == "bar" }

    list += StringElement("buzz")

    assertEquals(listOf("foo", "buzz"), list.toList())
  }

  @Test fun plusAssign_after_remove_head_with_3_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list += StringElement("baz")
    list.removeFirst { it.value == "foo" }

    list += StringElement("buzz")

    assertEquals(listOf("bar", "baz", "buzz"), list.toList())
  }

  @Test fun plusAssign_after_remove_middle_with_3_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list += StringElement("baz")
    list.removeFirst { it.value == "bar" }

    list += StringElement("buzz")

    assertEquals(listOf("foo", "baz", "buzz"), list.toList())
  }

  @Test fun plusAssign_after_remove_tail_with_3_items() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list += StringElement("baz")
    list.removeFirst { it.value == "baz" }

    list += StringElement("buzz")

    assertEquals(listOf("foo", "bar", "buzz"), list.toList())
  }

  @Test fun clear_empty_list() {
    val list = InlineLinkedList<StringElement>()
    list.clear()
    assertEquals(emptyList(), list.toList())
  }

  @Test fun clear_single_item_list() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list.clear()
    assertEquals(emptyList(), list.toList())
  }

  @Test fun clear_multi_item_list() {
    val list = InlineLinkedList<StringElement>()
    list += StringElement("foo")
    list += StringElement("bar")
    list.clear()
    assertEquals(emptyList(), list.toList())
  }

  private fun InlineLinkedList<StringElement>.toList(): List<String> {
    val items = mutableListOf<String>()
    forEach { items += it.value }
    return items
  }
}

private class StringElement(
  val value: String
) : InlineListNode<StringElement> {
  override var nextListNode: StringElement? = null
}
