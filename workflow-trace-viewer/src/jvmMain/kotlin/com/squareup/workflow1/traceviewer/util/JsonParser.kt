package com.squareup.workflow1.traceviewer.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.model.addChild
import com.squareup.workflow1.traceviewer.model.replaceChild
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

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
internal suspend fun parseFileTrace(
  file: PlatformFile,
): ParseResult {
  val jsonString = file.readString()
  val workflowAdapter = createMoshiAdapter<List<Node>>()
  val parsedRenderPasses = try {
    workflowAdapter.fromJson(jsonString) ?: return ParseResult.Failure(
      IllegalArgumentException("Provided trace file is empty or malformed.")
    )
  } catch (e: Exception) {
    return ParseResult.Failure(e)
  }

  val parsedFrames = parsedRenderPasses.map { renderPass -> getFrameFromRenderPass(renderPass) }
  val frameTrees = mutableListOf<Node>()
  parsedFrames.fold(parsedFrames[0]) { tree, frame ->
    val mergedTree = mergeFrameIntoMainTree(frame, tree)
    frameTrees.add(mergedTree)
    mergedTree
  }
  return ParseResult.Success(parsedFrames, frameTrees, parsedRenderPasses)
}

// internal fun parseLiveTrace(
//   adapter: JsonAdapter<List<Node>>,
//   renderPass: String,
// ): ParseResult {
//   val parsedRenderPasses = try {
//     adapter.fromJson(renderPass) ?: return ParseResult.Failure(
//       IllegalArgumentException("Provided trace file is empty or malformed.")
//     )
//   } catch (e: Exception) {
//     return ParseResult.Failure(e)
//   }
// }

/**
 * Creates a Moshi adapter for parsing the JSON trace file.
 */
internal inline fun <reified T> createMoshiAdapter(): JsonAdapter<List<T>> {
  val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()
  val workflowList = Types.newParameterizedType(List::class.java, typeOf<T>().javaType)
  val adapter: JsonAdapter<List<T>> = moshi.adapter(workflowList)
  return adapter
}

/**
 * We take an unparsed render pass and build up a tree structure from it to form a Frame.
 *
 * @return Node the root node of the tree for that specific frame.
 */
internal fun getFrameFromRenderPass(renderPass: List<Node>): Node {
  val childrenByParent: Map<String, List<Node>> = renderPass.groupBy { it.parentId }
  val root = childrenByParent[ROOT_ID]?.single()
  return buildTree(root!!, childrenByParent)
}

/**
 * Recursively builds a tree using each node's children.
 */
internal fun buildTree(node: Node, childrenByParent: Map<String, List<Node>>): Node {
  val children = (childrenByParent[node.id] ?: emptyList())
    .map { buildTree(it, childrenByParent) }
  return Node(
    name = node.name,
    id = node.id,
    parent = node.parent,
    parentId = node.parentId,
    props = node.props,
    state = node.state,
    children = LinkedHashMap(children.associateBy { it.id }),
  )
}

/**
 * Every new frame starts with the same roots as the main tree, so we can fold each frame into the
 * current tree, add all the missing children or replace any new ones, and then store the newly
 * merged tree.
 *
 * @return Node the newly formed tree with the frame merged into it.
 */
internal fun mergeFrameIntoMainTree(
  frame: Node,
  main: Node
): Node {
  require(frame.id == main.id)

  val updatedNode = frame.copy(children = main.children)

  return frame.children.values.fold(updatedNode) { mergedTree, frameChild ->
    val mainTreeChild = mergedTree.children[frameChild.id]
    if (mainTreeChild != null) {
      mergedTree.replaceChild(mergeFrameIntoMainTree(frameChild, mainTreeChild))
    } else {
      mergedTree.addChild(frameChild)
    }
  }
}

internal sealed interface ParseResult {
  class Success(val trace: List<Node>, val trees: List<Node>, affectedNodes: List<List<Node>>) : ParseResult {
    val affectedNodes = affectedNodes.map { it.toSet() }
  }
  class Failure(val error: Throwable) : ParseResult
}
