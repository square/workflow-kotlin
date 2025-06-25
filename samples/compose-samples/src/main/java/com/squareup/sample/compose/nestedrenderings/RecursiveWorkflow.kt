package com.squareup.sample.compose.nestedrenderings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.squareup.sample.compose.databinding.LegacyViewBinding
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.LegacyRendering
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.Rendering
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.renderChild
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory

/**
 * A simple workflow that produces [Rendering]s of zero or more children.
 * The rendering provides event handlers for adding children and resetting child count to zero.
 *
 * Every other (odd) rendering in the [Rendering.children] will be wrapped with a [LegacyRendering]
 * to force it to go through the legacy view layer. This way this sample both demonstrates pass-
 * through Composable renderings as well as adapting in both directions.
 */
@OptIn(WorkflowExperimentalApi::class)
object RecursiveWorkflow : ComposeWorkflow<Unit, Nothing, Screen>() {

  /**
   * A rendering from a [RecursiveWorkflow].
   *
   * @param children A list of renderings to display as children of this rendering.
   * @param onAddChildClicked Adds a child to [children].
   * @param onResetClicked Resets [children] to an empty list.
   */
  data class Rendering(
    val children: List<Screen>,
    val onAddChildClicked: () -> Unit,
    val onResetClicked: () -> Unit
  ) : Screen

  /**
   * Wrapper around a [Rendering] that will be implemented using a legacy view.
   */
  data class LegacyRendering(
    val rendering: Screen
  ) : AndroidScreen<LegacyRendering> {
    override val viewFactory = ScreenViewFactory.fromViewBinding(
      LegacyViewBinding::inflate,
      ::LegacyRunner
    )
  }

  @Composable override fun produceRendering(
    props: Unit,
    emitOutput: (Nothing) -> Unit
  ): Screen {
    var children by rememberSaveable { mutableStateOf(0) }
    return Rendering(
      children = List(children) { i ->
        val child = renderChild(RecursiveWorkflow)
        if (i % 2 == 0) child else LegacyRendering(child)
      },
      onAddChildClicked = { children++ },
      onResetClicked = { children = 0 }
    )
  }
}
