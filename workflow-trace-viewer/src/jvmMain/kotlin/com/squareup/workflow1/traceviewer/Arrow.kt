package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.atan2

/**
 * Since we eventually would want the ability to click into each arrow between nodes and see what
 * state/props are being passed, having a custom composable for the arrow where an onArrowClick
 * could be set will be helpful.
 */
@Composable
public fun Arrow(
  start: Offset,
  end: Offset,
) {
  Box(
    modifier = Modifier
      .clickable { println("arrow clicked") }
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .size(100.dp,100.dp)
    ) {
      drawArrow(
        start = start,
        end = end,
        color = Color.Black,
        strokeWidth = 2f
      )
    }
  }
}

private fun DrawScope.drawArrow(
  start: Offset,
  end: Offset,
  color: Color,
  strokeWidth: Float
) {
  drawLine(
    color = color,
    start = start,
    end = end,
    strokeWidth = strokeWidth
  )

  val arrowHeadSize = 20f
  val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()).toFloat()
  val arrowPoint1 = Offset(
    x = end.x - arrowHeadSize * Math.cos(angle + Math.PI / 6).toFloat(),
    y = end.y - arrowHeadSize * Math.sin(angle + Math.PI / 6).toFloat()
  )
  val arrowPoint2 = Offset(
    x = end.x - arrowHeadSize * Math.cos(angle - Math.PI / 6).toFloat(),
    y = end.y - arrowHeadSize * Math.sin(angle - Math.PI / 6).toFloat()
  )

  drawLine(color, end, arrowPoint1, strokeWidth)
  drawLine(color, end, arrowPoint2, strokeWidth)
}
