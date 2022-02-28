@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.sample.compose.nestedrenderings

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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.R
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.Rendering
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.composeScreenViewFactory
import com.squareup.workflow1.ui.compose.tooling.Preview

/**
 * Composition local of [Color] to use as the background color for a [RecursiveViewFactory].
 */
val LocalBackgroundColor = compositionLocalOf<Color> { error("No background color specified") }

/**
 * A `ViewFactory` that renders [RecursiveWorkflow.Rendering]s.
 */
@OptIn(WorkflowUiExperimentalApi::class)
val RecursiveViewFactory = composeScreenViewFactory<Rendering> { rendering, viewEnvironment ->
  // Every child should be drawn with a slightly-darker background color.
  val color = LocalBackgroundColor.current
  val childColor = remember(color) {
    color.copy(alpha = .9f)
      .compositeOver(Color.Black)
  }

  Card(backgroundColor = color) {
    Column(
      Modifier
        .padding(dimensionResource(R.dimen.recursive_padding))
        .fillMaxSize(),
      horizontalAlignment = CenterHorizontally
    ) {
      CompositionLocalProvider(LocalBackgroundColor provides childColor) {
        Children(
          rendering.children, viewEnvironment,
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

@OptIn(WorkflowUiExperimentalApi::class)
@Preview
@Composable fun RecursiveViewFactoryPreview() {
  CompositionLocalProvider(LocalBackgroundColor provides Color.Green) {
    RecursiveViewFactory.Preview(
      Rendering(
        children = listOf(
          StringRendering("foo"),
          Rendering(
            children = listOf(StringRendering("bar")),
            onAddChildClicked = {}, onResetClicked = {}
          )
        ),
        onAddChildClicked = {}, onResetClicked = {}
      ),
      placeholderModifier = Modifier.fillMaxSize()
    )
  }
}

@OptIn(WorkflowUiExperimentalApi::class)
@Composable private fun Children(
  children: List<Screen>,
  viewEnvironment: ViewEnvironment,
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
        viewEnvironment,
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
