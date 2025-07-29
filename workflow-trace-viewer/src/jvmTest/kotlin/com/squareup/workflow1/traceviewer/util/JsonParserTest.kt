package com.squareup.workflow1.traceviewer.util

import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.util.parser.mergeFrameIntoMainTree
import java.util.LinkedHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonParserTest {

  @Test
  fun `test mergeFrameIntoMainTree with new children`() {
    // Create main tree with one child
    val mainChild = createNode("child1", "root", "1")
    val mainTree = createNode("root", "root", "0", "0", listOf(mainChild))

    // Create frame with a new child
    val frameChild1 = createNode("child1", "root", "1")
    val frameChild2 = createNode("child2", "root", "2")
    val frame = createNode("root", "root", "0", "0", listOf(frameChild1, frameChild2))

    // Merge frame into main tree
    val mergedTree = mergeFrameIntoMainTree(frame, mainTree)

    // Verify results
    assertEquals(2, mergedTree.children.size)
    assertTrue(mergedTree.children.containsKey("1"))
    assertTrue(mergedTree.children.containsKey("2"))
  }

  @Test
  fun `test mergeFrameIntoMainTree with nested children`() {
    // Create main tree with nested structure
    val nestedChild = createNode("nested1", "child1", "2")
    val mainChild = createNode("child1", "root", "1", "0", listOf(nestedChild))
    val mainTree = createNode("root", "root", "0", "0", listOf(mainChild))

    // Create frame with new nested child
    val frameNestedChild1 = createNode("nested1", "child1", "2")
    val frameNestedChild2 = createNode("nested2", "child1", "3")
    val frameChild = createNode("child1", "root", "1", "0", mutableListOf(frameNestedChild1, frameNestedChild2))
    val frame = createNode("root", "root", "0", "0", listOf(frameChild))

    // Merge frame into main tree
    val mergedTree = mergeFrameIntoMainTree(frame, mainTree)

    // Verify results
    assertEquals(1, mergedTree.children.size)
    val updatedChild = mergedTree.children["1"]!!
    assertEquals(2, updatedChild.children.size)
    assertTrue(updatedChild.children.containsKey("2"))
    assertTrue(updatedChild.children.containsKey("3"))
  }

  @Test
  fun `test mergeFrameIntoMainTree with empty main tree children`() {
    // Create empty main tree
    val mainTree = createNode("root", "root", "0")

    // Create frame with children
    val frameChild = createNode("child1", "root", "1")
    val frame = createNode("root", "root", "0", "0", listOf(frameChild))

    // Merge frame into main tree
    val mergedTree = mergeFrameIntoMainTree(frame, mainTree)

    // Verify results
    assertEquals(1, mergedTree.children.size)
    assertEquals("child1", mergedTree.children["1"]?.name)
  }

  @Test
  fun `test mergeFrameIntoMainTree with empty frame children`() {
    // Create main tree with children
    val mainChild = createNode("child1", "root", "1")
    val mainTree = createNode("root", "root", "0", "0", listOf(mainChild))

    // Create empty frame
    val frame = createNode("root", "root", "0")

    // Merge frame into main tree
    mergeFrameIntoMainTree(frame, mainTree)

    // Verify results
    assertEquals(1, mainTree.children.size)
    assertEquals("child1", mainTree.children["1"]?.name)
  }

  private fun createNode(
    name: String,
    parent: String,
    id: String,
    parentId: String = "0",
    children: List<Node> = emptyList()
  ): Node {
    return Node(
      name = name,
      id = id,
      parent = parent,
      parentId = parentId,
      props = "",
      state = "",
      rendering = "",
      children = LinkedHashMap<String, Node>().apply {
        children.forEach { put(it.id, it) }
      }
    )
  }
}
