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
package com.squareup.sample.textinput

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.squareup.sample.textinput.TextInputWorkflow.Rendering
import com.squareup.workflow.ui.compose.composedViewFactory
import com.squareup.workflow.ui.compose.tooling.preview

val TextInputViewFactory = composedViewFactory<Rendering> { rendering, _ ->
  Column(
    modifier = Modifier
      .fillMaxSize()
      .wrapContentSize()
      .animateContentSize(clip = false),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(text = rendering.text)
    OutlinedTextField(
      label = {},
      placeholder = { Text("Enter some text") },
      value = rendering.text,
      onValueChange = rendering.onTextChanged
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = rendering.onSwapText) {
      Text("Swap")
    }
  }
}

@Preview(showBackground = true)
@Composable private fun TextInputViewFactoryPreview() {
  TextInputViewFactory.preview(Rendering(
    text = "Hello world",
    onTextChanged = {},
    onSwapText = {}
  ))
}
