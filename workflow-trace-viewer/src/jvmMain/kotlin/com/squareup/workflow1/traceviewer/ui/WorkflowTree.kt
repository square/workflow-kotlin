package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squareup.moshi.JsonAdapter
import com.squareup.workflow1.traceviewer.TraceMode
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.util.ParseResult
import com.squareup.workflow1.traceviewer.util.createMoshiAdapter
import com.squareup.workflow1.traceviewer.util.getFrameFromRenderPass
import com.squareup.workflow1.traceviewer.util.mergeFrameIntoMainTree
import com.squareup.workflow1.traceviewer.util.parseFileTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Access point for drawing the main content of the app. It will load the trace for given files and
 * tabs. This will also all errors related to errors parsing a given trace JSON file.
 *
 * This handles either File or Live trace modes, and will parse equally
 */
@Composable
@Suppress("UNCHECKED_CAST")
internal fun RenderTrace(
  traceSource: TraceMode,
  frameInd: Int,
  onFileParse: (List<Node>) -> Unit,
  onNodeSelect: (Node, Node?) -> Unit,
  onNewFrame: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isLoading by remember(traceSource) { mutableStateOf(true) }
  var error by remember(traceSource) { mutableStateOf<Throwable?>(null) }
  val frames = remember { mutableStateListOf<Node>() }
  val fullTree = remember { mutableStateListOf<Node>() }
  val affectedNodes = remember { mutableStateListOf<Set<Node>>() }

  fun addToStates(frame: List<Node>, tree: List<Node>, affected:List<Set<Node>>) {
    frames.addAll(frame)
    fullTree.addAll(tree)
    affectedNodes.addAll(affected)
    isLoading = false
    onFileParse(frame)
  }

  LaunchedEffect(traceSource) {
    when (traceSource) {
      is TraceMode.File -> {
        // We guarantee the file is null since this composable can only be called when a file is selected
        val parseResult = parseFileTrace(traceSource.file!!)

        when (parseResult) {
          is ParseResult.Failure -> {
            error = parseResult.error
          }

          is ParseResult.Success -> {
            addToStates(
              frame = parseResult.trace,
              tree = parseResult.trees,
              affected = parseResult.affectedNodes
            )
          }
        }
      }

      is TraceMode.Live -> {
        val socket = traceSource.socket
        socket.beginListen(this)
        val adapter: JsonAdapter<List<Node>> = createMoshiAdapter<Node>()

        // Since channel implements ChannelIterator, we can for-loop through on the receiver end
        withContext(Dispatchers.IO) {
          for (renderPass in socket.renderPassChannel) {
            // get the parsedRenderPass
            val parsedRenderPass = try {
              adapter.fromJson(renderPass) ?: emptyList()
            } catch (e: Exception) {
              error = e
              continue
            }

            // build the Frame
            val parsedFrame = getFrameFromRenderPass(parsedRenderPass)

            // merge Frame into full tree
            val mergedTree = if (fullTree.isEmpty()) {
              parsedFrame
            } else {
              mergeFrameIntoMainTree(parsedFrame, fullTree.last())
            }

            withContext(Dispatchers.Default){
              addToStates(
                frame = listOf(parsedFrame),
                tree = listOf(mergedTree),
                affected = listOf(parsedRenderPass.toSet())
              )
              onNewFrame()
            }
          }
        }
      }
    }
  }


  if (error != null) {
    Text("Error parsing file: ${error?.message}")
    return
  }

  if (!isLoading) {
    val previousFrame = if (frameInd > 0) fullTree[frameInd - 1] else null
    DrawTree(fullTree[frameInd], previousFrame, affectedNodes[frameInd], onNodeSelect)
  }
}

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree
 * The Column holds a subtree of nodes, and the Row holds all the children of the current node
 */
@Composable
private fun DrawTree(
  node: Node,
  previousNode: Node?,
  affectedNodes: Set<Node>,
  onNodeSelect: (Node, Node?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .padding(5.dp)
      .border(1.dp, Color.Black)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val isAffected = affectedNodes.contains(node)
    DrawNode(node, previousNode, isAffected, onNodeSelect)

    // Draws the node's children recursively.
    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.Top
    ) {
      /*
        We pair up the current node's children with previous frame's children.
        In the edge case that the current frame has additional children compared to the previous
        frame, we replace with null and will check before next recursive call.
       */
      node.children.forEach { (index, childNode) ->
        val prevChildNode = previousNode?.children?.get(index)
        DrawTree(childNode, prevChildNode, affectedNodes, onNodeSelect)
      }
    }
  }
}

/**
 * A basic box that represents a workflow node
 */
@Composable
private fun DrawNode(
  node: Node,
  previousNode: Node?,
  isAffected: Boolean,
  onNodeSelect: (Node, Node?) -> Unit,
) {
  Box(
    modifier = Modifier
      .background(if (isAffected) Color.Green else Color.Transparent)
      .clickable {
        // Selecting a node will bubble back up to the main view to handle the selection
        onNodeSelect(node, previousNode)
      }
      .padding(10.dp)
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = node.name)
      Text(text = "ID: ${node.id}")
    }
  }
}
