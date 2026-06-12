package com.squareup.workflow1.traceviewer.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.zoomable

/**
 * This is the backdrop for the whole app. Since there can be hundreds of modules at a time, there
 * is not realistic way to fit everything on the screen at once. Having the liberty to pan across
 * the whole tree as well as zoom into specific subtrees means there's a lot more control when
 * analyzing the traces.
 *
 */
@Composable
internal fun SandboxBackground(
  zoomableState: ZoomableState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(zoomableState) {
    // Request focus to receive keyboard shortcuts.
    focusRequester.requestFocus()
  }
  Box(
    modifier
      .fillMaxSize()
      .focusRequester(focusRequester)
      .zoomable(zoomableState)
  ) {
    Box(
      modifier = Modifier
        .wrapContentSize(unbounded = true, align = AbsoluteAlignment.TopLeft)
        .onSizeChanged {
          // TODO(saket): Modifier.zoomable() should automatically use its child's size by default.
          zoomableState.setContentLocation(
            ZoomableContentLocation.unscaledAndTopLeftAligned(it.toSize())
          )
        }
    ) {
      content()
    }
  }
}
