@file:Suppress("SameParameterValue")
@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui.compose.tooling

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.ui.AsScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.composeScreenViewFactory

/**
 * A [ScreenViewFactory] that will be used any time a [PreviewScreenViewFactoryFinder]
 * is asked to show a rendering. It displays a placeholder graphic and the rendering's
 * `toString()` result.
 */
internal fun placeholderScreenViewFactory(modifier: Modifier): ScreenViewFactory<Screen> =
  composeScreenViewFactory { rendering, _ ->
    BoxWithConstraints {
      BasicText(
        modifier = modifier
          .clipToBounds()
          .drawBehind {
            drawIntoCanvas { canvas ->
              canvas.withSaveLayer(size.toRect(), Paint().apply { alpha = .2f }) {
                canvas.drawRect(size.toRect(), Paint().apply { color = Color.Gray })
                drawCrossHatch(
                  color = Color.Red,
                  strokeWidth = 2.dp,
                  spaceWidth = 8.dp,
                )
              }
            }
          }
          .padding(8.dp),
        text = (rendering as? AsScreen<*>)?.content?.toString() ?: rendering.toString(),
        style = TextStyle(
          textAlign = TextAlign.Center,
          color = Color.White,
          shadow = Shadow(blurRadius = 5f, color = Color.Black)
        )
      )
    }
  }

private fun DrawScope.drawCrossHatch(
  color: Color,
  strokeWidth: Dp,
  spaceWidth: Dp,
) {
  drawHatch(color, strokeWidth, spaceWidth)
  // Draw again but flipped horizontally.
  scale(scaleX = -1f, scaleY = 1f) {
    drawHatch(color, strokeWidth, spaceWidth)
  }
}

private fun DrawScope.drawHatch(
  color: Color,
  strokeWidth: Dp,
  spaceWidth: Dp,
) {
  val strokeWidthPx = strokeWidth.toPx()
  val spaceWidthPx = spaceWidth.roundToPx()

  // Lower-left half.
  val ySegments = 0..size.height.toInt() step (spaceWidthPx + strokeWidthPx.toInt())
  ySegments.forEach { yStart ->
    drawLine(
      start = Offset(0f, yStart.toFloat()),
      // This will draw past the bounds in many cases, but it's simpler to just let clipping handle
      // that.
      end = Offset(size.height - yStart, size.height),
      color = color,
      strokeWidth = strokeWidthPx
    )
  }

  // Upper-right half.
  val xSegments = 0..size.width.toInt() step (spaceWidthPx + strokeWidthPx.toInt())
  xSegments.forEach { xStart ->
    drawLine(
      start = Offset(xStart.toFloat(), 0f),
      end = Offset(size.width, size.width - xStart),
      color = color,
      strokeWidth = strokeWidthPx
    )
  }
}
