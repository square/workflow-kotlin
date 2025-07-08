package com.squareup.workflow1.traceviewer.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.workflow1.traceviewer.model.Node
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString

/*
 The root workflow Node uses an ID of 0, and since we are filtering childrenByParent by the
 parentId, the root node has a parent of -1 ID. This is reflected seen inside android-register
 */
const val ROOT_ID: String = "-1"

/**
 * Parses a given file's JSON String into a list of [Node]s with Moshi adapters. Each of these nodes
 * count as the root of a tree which forms a Frame.
 *
 * @return A [ParseResult] representing result of parsing, either an error related to the
 * format of the JSON, or a success and a parsed trace.
 */
public suspend fun parseTrace(
  file: PlatformFile,
): ParseResult {
  return try {
    val jsonString = file.readString()
    val workflowAdapter = createMoshiAdapter()
    val parsedRenderPasses = workflowAdapter.fromJson(jsonString)

    var mainWorkflowTree: Node? = null
    val parsedFrame = mutableListOf<Node>()
    parsedRenderPasses?.forEach { renderPass ->
      val parsed = getFrameFromRenderPass(renderPass)
      if (mainWorkflowTree == null) {
        mainWorkflowTree = parsed
      } else {
        mergeFrameIntoMainTree(parsed, mainWorkflowTree!!)
      }
      parsedFrame.add(parsed)
    }
    /*
      this parsing method can never be called without a provided file, so we can assume that there
      will always be at least one render pass in the trace. If not, then Moshi would catch any
      malformed JSON and throw an error beforehand.
     */
    ParseResult.Success(parsedFrame, mainWorkflowTree!!)
  } catch (e: Exception) {
    ParseResult.Failure(e)
  }
}

/**
 * Creates a Moshi adapter for parsing the JSON trace file.
 */
private fun createMoshiAdapter(): JsonAdapter<List<List<Node>>> {
  val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()
  val workflowList = Types.newParameterizedType(
    List::class.java,
    Types.newParameterizedType(List::class.java, Node::class.java)
  )
  val adapter: JsonAdapter<List<List<Node>>> = moshi.adapter(workflowList)
  return adapter
}

/**
 * We take an unparsed render pass and build up a tree structure from it to form a Frame.
 *
 * @return Node the root node of the tree for that specific frame.
 */
private fun getFrameFromRenderPass(renderPass: List<Node>): Node {
  val childrenByParent: Map<String, List<Node>> = renderPass.groupBy { it.parentId }
  val root = childrenByParent[ROOT_ID]?.single()
  return buildTree(root!!, childrenByParent)
}

/**
 * Recursively builds a tree using each node's children.
 */
private fun buildTree(node: Node, childrenByParent: Map<String, List<Node>>): Node {
  val children = (childrenByParent[node.id] ?: emptyList())
  return Node(
    name = node.name,
    id = node.id,
    parent = node.parent,
    parentId = node.parentId,
    props = node.props,
    state = node.state,
    children = children.map { buildTree(it, childrenByParent) },
  )
}

/**
 * Every new frame starts with the same roots as the main tree, so we can do a simple traversal to
 * add any missing child nodes from the frame.
 */
private fun mergeFrameIntoMainTree(
  frame: Node,
  main: Node
) {
  val children = frame.children
  children.forEach { child ->
    if (child in main.children) {
      mergeFrameIntoMainTree(child, main.children.find { it.id == child.id }!!)
    } else {
      main.children.add(child)
    }
  }
}

sealed interface ParseResult {
  class Success(val trace: List<Node>?, val mainTree: Node) : ParseResult
  class Failure(val error: Throwable) : ParseResult
}
