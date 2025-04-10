package com.squareup.sample.compose.textinput

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.squareup.sample.compose.textinput.TextInputWorkflow.Rendering
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.compose.ScreenComposableFactory
import com.squareup.workflow1.ui.compose.asMutableTextFieldValueState
import com.squareup.workflow1.ui.compose.tooling.Preview

val TextInputComposableFactory = ScreenComposableFactory<Rendering> { rendering ->
  Column(
    modifier = Modifier
      .fillMaxSize()
      .wrapContentSize()
      .animateContentSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    var textFieldValue by rendering.textController.asMutableTextFieldValueState()

    Text(text = textFieldValue.text)
    OutlinedTextField(
      label = {},
      placeholder = { Text("Enter some text") },
      value = textFieldValue,
      onValueChange = { textFieldValue = it }
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = rendering.onSwapText) {
      Text("Swap")
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun TextInputViewFactoryPreview() {
  TextInputComposableFactory.Preview(
    Rendering(
      textController = TextController("Hello world"),
      onSwapText = {}
    )
  )
}
