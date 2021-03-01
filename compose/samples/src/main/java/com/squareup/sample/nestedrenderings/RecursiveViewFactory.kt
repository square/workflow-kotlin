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

import androidx.compose.foundation.layout.Arrangement.SpaceEvenly
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.squareup.sample.R
import com.squareup.sample.nestedrenderings.RecursiveWorkflow.Rendering
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.composedViewFactory
import com.squareup.workflow1.ui.compose.tooling.preview

/**
 * Ambient of [Color] to use as the background color for a [RecursiveViewFactory].
 */
val LocalBackgroundColor = compositionLocalOf<Color> { error("No background color specified") }

/**
 * A `ViewFactory` that renders [RecursiveWorkflow.Rendering]s.
 */
@OptIn(WorkflowUiExperimentalApi::class)
val RecursiveViewFactory = composedViewFactory<Rendering> { rendering, viewEnvironment ->
  // Every child should be drawn with a slightly-darker background color.
  val color = LocalBackgroundColor.current
  val childColor = remember(color) {
    color.copy(alpha = .9f)
      .compositeOver(Color.Black)
  }

  Card(backgroundColor = color) {
    Column(
      Modifier.padding(dimensionResource(R.dimen.recursive_padding))
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

@OptIn(WorkflowUiExperimentalApi::class)
@Composable private fun Children(
  children: List<Any>,
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
        modifier = Modifier.weight(1f, true)
          .padding(dimensionResource(R.dimen.recursive_padding))
      )
    }
  }
}

@Composable private fun Buttons(
  onAdd: () -> Unit,
  onReset: () -> Unit
) {
  // TODO re-create FlowRow so the buttons will wrap when the parent is too narrow.
  // FlowRow was removed from the standard library
  Row {
    Button(onClick = onAdd) {
      Text("Add Child")
    }
    Button(onClick = onReset) {
      Text("Reset")
    }
  }
}
