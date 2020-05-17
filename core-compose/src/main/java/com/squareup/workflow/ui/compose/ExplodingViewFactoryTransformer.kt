package com.squareup.workflow.ui.compose

import androidx.animation.AnimatedFloat
import androidx.animation.AnimationEndReason.TargetReached
import androidx.compose.Composable
import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.onCommit
import androidx.compose.setValue
import androidx.ui.animation.animatedFloat
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.drawLayer
import androidx.ui.unit.dp
import com.squareup.workflow.ui.ViewEnvironment
import kotlin.random.Random

/**
 * TODO write documentation
 */
class ExplodingViewFactoryTransformer(
  min: Int = -1,
  max: Int = 1
) : ViewFactoryTransformer {

  var min by mutableStateOf(min.toFloat())
  var max by mutableStateOf(max.toFloat())

  @Composable override fun modifyView(
    renderingDepth: Int,
    viewEnvironment: ViewEnvironment
  ): Modifier {
    val offsetXDp = animatedFloat(0f)
    val offsetYDp = animatedFloat(0f)

    onCommit(min, max) {
      offsetXDp.startPulseAnimation(min, max)
      offsetYDp.startPulseAnimation(min, max)
    }

    if (!offsetXDp.isRunning && !offsetYDp.isRunning) {
      return Modifier
    }

    val offsetXPx = with(DensityAmbient.current) { offsetXDp.value.dp.toPx() }
    val offsetYPx = with(DensityAmbient.current) { offsetYDp.value.dp.toPx() }
    return Modifier.drawLayer(translationX = offsetXPx.value, translationY = offsetYPx.value)
  }

  private fun AnimatedFloat.startPulseAnimation(
    min: Float,
    max: Float
  ) {
    fun animate() {
      val target = if (min == 0f && max == 0f) 0f else {
        Random.nextDouble(min.toDouble(), max.toDouble())
            .toFloat()
      }
      animateTo(target) { reason, _ ->
        if (reason == TargetReached) {
          if (min != 0f || max != 0f) animate()
        }
      }
    }
    animate()
  }
}
