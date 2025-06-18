package com.squareup.sample.compose.hellocompose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.workflow1.ui.compose.ComposeScreen
import com.squareup.workflow1.ui.compose.tooling.Preview

data class HelloComposeScreen(
  val message: String,
  val onClick: () -> Unit
) : ComposeScreen {
  @Composable override fun Content() {
    // It is best to keep this method as empty as possible to avoid
    // capturing state from stale ComposeScreen instances,
    // and to keep from interfering with Compose's stability checks.
    // https://developer.android.com/develop/ui/compose/performance/stability
    Hello(this)
  }
}

/**
 * @param modifier even though we use the default [Modifier] when calling
 * from [HelloComposeScreen.Content], a habit of accepting this param from the
 * Composable itself is handy for screenshot tests and previews.
 */
@Composable
private fun Hello(
  screen: HelloComposeScreen,
  modifier: Modifier = Modifier
) {
  Text(
    screen.message,
    modifier = modifier
      .clickable(onClick = screen.onClick)
      .fillMaxSize()
      .wrapContentSize(Alignment.Center)
  )
}

@Preview(heightDp = 150, showBackground = true)
@Composable
private fun HelloPreview() {
  HelloComposeScreen(
    "Hello!",
    onClick = {}
  ).Preview()
}
