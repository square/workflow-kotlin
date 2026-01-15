@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.sample.compose.nestedrenderings

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement.SpaceEvenly
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.R
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.Rendering
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.compose.ScreenComposableFactory
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.tooling.Preview
import kotlin.time.DurationUnit.MILLISECONDS

/**
 * Composition local of [Color] to use as the background color for a [RecursiveComposableFactory].
 */
val LocalBackgroundColor = compositionLocalOf<Color> { error("No background color specified") }

/**
 * A `ViewFactory` that renders [RecursiveWorkflow.Rendering]s.
 */
val RecursiveComposableFactory = ScreenComposableFactory<Rendering> { rendering ->
  // Every child should be drawn with a slightly-darker background color.
  val color = LocalBackgroundColor.current
  val childColor = remember(color) {
    color.copy(alpha = .9f)
      .compositeOver(Color.Black)
  }

  var lastFlashedTrigger by remember { mutableIntStateOf(rendering.flashTrigger) }
  val flashAlpha = remember { Animatable(Color(0x00FFFFFF)) }

  // Flash the card white when asked.
  LaunchedEffect(rendering.flashTrigger) {
    if (rendering.flashTrigger != 0) {
      lastFlashedTrigger = rendering.flashTrigger
      flashAlpha.animateTo(
        Color(0x00FFFFFF),
        animationSpec = keyframes {
          Color.White at (rendering.flashTime / 7).toInt(MILLISECONDS) using FastOutLinearInEasing
          Color(0x00FFFFFF) at rendering.flashTime.toInt(MILLISECONDS) using LinearOutSlowInEasing
        }
      )
    }
  }

  Card(
    backgroundColor = flashAlpha.value.compositeOver(color),
    modifier = Modifier.pointerInput(rendering) {
      detectTapGestures(onPress = { rendering.onSelfClicked() })
    }
  ) {
    Column(
      Modifier
        .padding(dimensionResource(R.dimen.recursive_padding))
        .fillMaxSize(),
      horizontalAlignment = CenterHorizontally
    ) {
      CompositionLocalProvider(LocalBackgroundColor provides childColor) {
        Children(
          rendering.children,
          // Pass a weight so that the column fills all the space not occupied by the buttons.
          modifier = Modifier.weight(1f, fill = true)
        )
      }
      Buttons(
        onAdd = rendering.onAddChildClicked,
        onReset = rendering.onResetClicked
      )
    }
  }
}

@Preview
@Composable
fun RecursiveViewFactoryPreview() {
  CompositionLocalProvider(LocalBackgroundColor provides Color.Green) {
    RecursiveComposableFactory.Preview(
      Rendering(
        children = listOf(
          StringRendering("foo"),
          Rendering(
            children = listOf(StringRendering("bar")),
            flashTrigger = 0,
            onSelfClicked = {},
            onAddChildClicked = {},
            onResetClicked = {}
          )
        ),
        flashTrigger = 0,
        onSelfClicked = {},
        onAddChildClicked = {},
        onResetClicked = {}
      ),
      placeholderModifier = Modifier.fillMaxSize()
    )
  }
}

@Composable
private fun Children(
  children: List<Screen>,
  modifier: Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = SpaceEvenly,
    horizontalAlignment = CenterHorizontally
  ) {
    children.forEach { childRendering ->
      WorkflowRendering(
        childRendering,
        // Pass a weight so all children are partitioned evenly within the total column space.
        // Without the weight, each child is the full size of the parent.
        modifier = Modifier
          .weight(1f, fill = true)
          .fillMaxWidth()
          .padding(dimensionResource(R.dimen.recursive_padding))
      )
    }
  }
}

@Composable private fun Buttons(
  onAdd: () -> Unit,
  onReset: () -> Unit
) {
  Row {
    Button(onClick = onAdd) {
      Text("Add Child")
    }
    Button(onClick = onReset) {
      Text("Reset")
    }
  }
}
