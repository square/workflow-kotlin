package com.squareup.sample.compose.nestedrenderings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

/**
 * A simple workflow that produces [Rendering]s of zero or more children.
 * The rendering provides event handlers for adding children and resetting child count to zero.
 *
 * Every other (odd) rendering in the [Rendering.children] will be wrapped with a [LegacyRendering]
 * to force it to go through the legacy view layer. This way this sample both demonstrates pass-
 * through Composable renderings as well as adapting in both directions.
 */
@OptIn(WorkflowExperimentalApi::class)
object RecursiveWorkflow : ComposeWorkflow<Unit, Unit, Screen>() {

  /**
   * A rendering from a [RecursiveWorkflow].
   *
   * @param children A list of renderings to display as children of this rendering.
   * @param onAddChildClicked Adds a child to [children].
   * @param onResetClicked Resets [children] to an empty list.
   */
  data class Rendering(
    val children: List<Screen>,
    val flashTrigger: Int = 0,
    val flashTime: Duration = ZERO,
    val onSelfClicked: () -> Unit = {},
    val onAddChildClicked: () -> Unit = {},
    val onResetClicked: () -> Unit = {}
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

  @OptIn(ExperimentalStdlibApi::class)
  @Composable override fun produceRendering(
    props: Unit,
    emitOutput: (Unit) -> Unit
  ): Screen {
    var children by rememberSaveable { mutableStateOf(0) }
    var flashTrigger by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
      println("OMG coroutineScope dispatcher: ${coroutineScope.coroutineContext[CoroutineDispatcher]}")
      onDispose {}
    }

    LaunchedEffect(Unit) {
      println("OMG LaunchedEffect dispatcher: ${coroutineScope.coroutineContext[CoroutineDispatcher]}")
    }

    return Rendering(
      children = List(children) { i ->
        val child = renderChild(RecursiveWorkflow, onOutput = {
          // When a child is clicked, cascade the flash up.
          coroutineScope.launch {
            delay(0.1.seconds)
            flashTrigger++
            emitOutput(Unit)
          }
        })
        if (i % 2 == 0) child else LegacyRendering(child)
      },
      flashTrigger = flashTrigger,
      flashTime = 0.5.seconds,
      // Trigger a cascade of flashes when clicked.
      onSelfClicked = {
        flashTrigger++
        emitOutput(Unit)
      },
      onAddChildClicked = { children++ },
      onResetClicked = { children = 0 }
    )
  }
}
