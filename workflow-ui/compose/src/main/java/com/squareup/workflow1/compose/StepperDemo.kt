package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastJoinToString
import com.squareup.workflow1.compose.DemoStep.ONE
import com.squareup.workflow1.compose.DemoStep.THREE
import com.squareup.workflow1.compose.DemoStep.TWO
import com.squareup.workflow1.compose.Screen.ScreenOne
import com.squareup.workflow1.compose.Screen.ScreenThree
import com.squareup.workflow1.compose.Screen.ScreenTwo

internal enum class DemoStep {
  ONE,
  TWO,
  THREE,
}

internal sealed interface Screen {
  val message: String

  data class ScreenOne(
    override val message: String,
    val onNextClicked: () -> Unit,
  ) : Screen

  data class ScreenTwo(
    override val message: String,
    val onNextClicked: () -> Unit,
    val onBack: () -> Unit,
  ) : Screen

  data class ScreenThree(
    override val message: String,
    val onBack: () -> Unit,
  ) : Screen
}

@Composable
internal fun StepperDemo() {
  var step by rememberSaveable { mutableStateOf(ONE) }
  println("step=$step")

  val stack: List<Screen> = stepper(advance = { step = it }) {
    val breadcrumbs = previousSteps.fastJoinToString(separator = " > ") { it.rendering.message }
    when (step) {
      ONE -> ScreenOne(
        message = "Step one",
        onNextClicked = { advance(TWO) },
      )

      TWO -> ScreenTwo(
        message = "Step two",
        onNextClicked = { advance(THREE) },
        onBack = { goBack() },
      )

      THREE -> ScreenThree(
        message = "Step three",
        onBack = { goBack() },
      )
    }
  }

  println("stack = ${stack.fastJoinToString()}")
}

@Composable
internal fun StepperInlineDemo() {
  var step by rememberSaveable { mutableStateOf(ONE) }
  println("step=$step")

  val stack: List<Screen> = stepper {
    val breadcrumbs = previousSteps.fastJoinToString(separator = " > ") { it.rendering.message }
    when (step) {
      ONE -> ScreenOne(
        message = "Step one",
        onNextClicked = { advance { step = TWO } },
      )

      TWO -> ScreenTwo(
        message = "Step two",
        onNextClicked = { advance { step = THREE } },
        onBack = { goBack() },
      )

      THREE -> ScreenThree(
        message = "Step three",
        onBack = { goBack() },
      )
    }
  }

  println("stack = ${stack.fastJoinToString()}")
}
