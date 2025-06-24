package com.squareup.sample.dungeon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.squareup.sample.dungeon.DungeonAppWorkflow.Props
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.compose.ComposeScreen

class SimpleWorkflow : StatefulWorkflow<Props, String, Nothing, Screen>() {
  override fun initialState(
    props: Props,
    snapshot: Snapshot?
  ): String = snapshot?.bytes?.parse { it.readUtf8() } ?: "initial"

  override fun render(
    renderProps: Props,
    renderState: String,
    context: RenderContext<Props, String, Nothing>
  ): Screen {
    return MyScreen(
      text = renderState,
      onClick = context.eventHandler("onClick", remember = true) { state = state.reversed() }
    )
  }

  override fun snapshotState(state: String): Snapshot = Snapshot.of(state)
}

private data class MyScreen(
  val text: String,
  val onClick: () -> Unit,
) : ComposeScreen {
  @Composable override fun Content() {
    Modifier.pointerInput(Unit) {
      extendedTouchPadding
    }
    BasicText(
      text = text,
      color = { Color.White },
      modifier = Modifier
        .background(Color.White)
        .wrapContentSize()
        .padding(8.dp)
        .clickable { onClick() }
        .background(Color(red = 0f, green = 0f, blue = 0.9f))
        .padding(48.dp)
    )
  }
}
