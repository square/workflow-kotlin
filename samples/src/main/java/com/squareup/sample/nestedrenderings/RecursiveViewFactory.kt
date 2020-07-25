/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.sample.nestedrenderings

import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.ambientOf
import androidx.compose.remember
import androidx.ui.core.Alignment.Companion.CenterHorizontally
import androidx.ui.core.Modifier
import androidx.ui.foundation.Text
import androidx.ui.graphics.Color
import androidx.ui.graphics.compositeOver
import androidx.ui.layout.Arrangement.SpaceEvenly
import androidx.ui.layout.Column
import androidx.ui.layout.ExperimentalLayout
import androidx.ui.layout.FlowRow
import androidx.ui.layout.MainAxisAlignment
import androidx.ui.layout.SizeMode.Expand
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.padding
import androidx.ui.material.Button
import androidx.ui.material.Card
import androidx.ui.res.dimensionResource
import androidx.ui.tooling.preview.Preview
import com.squareup.sample.R
import com.squareup.sample.nestedrenderings.RecursiveWorkflow.Rendering
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.compose.composedViewFactory
import com.squareup.workflow.ui.compose.WorkflowRendering
import com.squareup.workflow.ui.compose.tooling.preview

/**
 * Ambient of [Color] to use as the background color for a [RecursiveViewFactory].
 */
val BackgroundColorAmbient = ambientOf<Color> { error("No background color specified") }

/**
 * A `ViewFactory` that renders [RecursiveWorkflow.Rendering]s.
 */
val RecursiveViewFactory = composedViewFactory<Rendering> { rendering, viewEnvironment ->
  // Every child should be drawn with a slightly-darker background color.
  val color = BackgroundColorAmbient.current
  val childColor = remember(color) {
    color.copy(alpha = .9f)
        .compositeOver(Color.Black)
  }

  Card(color = color) {
    Column(
        Modifier.padding(dimensionResource(R.dimen.recursive_padding))
            .fillMaxSize(),
        horizontalGravity = CenterHorizontally
    ) {
      Providers(BackgroundColorAmbient provides childColor) {
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

@Preview
@Composable fun RecursiveViewFactoryPreview() {
  Providers(BackgroundColorAmbient provides Color.Green) {
    RecursiveViewFactory.preview(
        Rendering(
            children = listOf(
                "foo",
                Rendering(
                    children = listOf("bar"),
                    onAddChildClicked = {}, onResetClicked = {}
                )
            ), onAddChildClicked = {}, onResetClicked = {}
        ),
        placeholderModifier = Modifier.fillMaxSize()
    )
  }
}

@Composable private fun Children(
  children: List<Any>,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier
) {
  Column(
      modifier = modifier,
      verticalArrangement = SpaceEvenly,
      horizontalGravity = CenterHorizontally
  ) {
    children.forEach { childRendering ->
      WorkflowRendering(
          childRendering,
          // Pass a weight so all children are partitioned evenly within the total column space.
          // Without the weight, each child is the full size of the parent.
          viewEnvironment,
          modifier = Modifier.weight(1f, true)
              .padding(dimensionResource(R.dimen.recursive_padding))
      )
    }
  }
}

@OptIn(ExperimentalLayout::class)
@Composable private fun Buttons(
  onAdd: () -> Unit,
  onReset: () -> Unit
) {
  // Use a FlowRow so the buttons will wrap when the parent is too narrow.
  FlowRow(
      mainAxisSize = Expand,
      mainAxisAlignment = MainAxisAlignment.SpaceEvenly,
      crossAxisSpacing = dimensionResource(R.dimen.recursive_padding)
  ) {
    Button(onClick = onAdd) {
      Text("Add Child")
    }
    Button(onClick = onReset) {
      Text("Reset")
    }
  }
}
