package com.squareup.workflow1.ui.compose.tooling

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.composeScreenViewFactory

/**
 * Draws this [ScreenViewFactory] using a special preview [ScreenViewFactoryFinder].
 *
 * Use inside `@Preview` Composable functions.
 *
 * *Note: [rendering] must be the same type as this [ScreenViewFactory], even though the type
 * system does not enforce this constraint. This is due to a Compose compiler bug tracked
 * [here](https://issuetracker.google.com/issues/156527332).*
 *
 * @param modifier [Modifier] that will be applied to this [ScreenViewFactory].
 * @param placeholderModifier [Modifier] that will be applied to any nested renderings this factory
 * shows.
 * @param viewEnvironmentUpdater Function that configures the [ViewEnvironment] passed to this
 * factory.
 */
@WorkflowUiExperimentalApi
@Composable public fun <RenderingT : Screen> ScreenViewFactory<RenderingT>.Preview(
  rendering: RenderingT,
  modifier: Modifier = Modifier,
  placeholderModifier: Modifier = Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null
) {
  val previewEnvironment =
    rememberPreviewViewEnvironment(placeholderModifier, viewEnvironmentUpdater, mainFactory = this)
  WorkflowRendering(rendering, previewEnvironment, modifier)
}

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(showBackground = true)
@Composable private fun ViewFactoryPreviewPreview() {
  val factory = composeScreenViewFactory<Screen> { _, environment ->
    Column(
      verticalArrangement = spacedBy(8.dp),
      modifier = Modifier.padding(8.dp)
    ) {
      BasicText("Top text")
      WorkflowRendering(
        rendering = TextRendering(
          "Child rendering with very long text to suss out cross-hatch rendering edge cases",
        ),
        viewEnvironment = environment,
        modifier = Modifier
          .aspectRatio(1f)
          .padding(8.dp)
      )
      BasicText("Bottom text")
    }
  }

  factory.Preview(object : Screen {})
}

@OptIn(WorkflowUiExperimentalApi::class)
private data class TextRendering(val text: String) : Screen
