package com.squareup.workflow1.ui

import android.app.Dialog
import android.content.Context

@WorkflowUiExperimentalApi
interface ModalRendering

@WorkflowUiExperimentalApi
fun <RenderingT: ModalRendering> RenderingT.buildDialog(
  initialViewEnvironment: ViewEnvironment,
  context: Context
): Dialog {
  val dialogFactory = initialViewEnvironment[ViewRegistry].getEntryFor(this::class)
  require(dialogFactory is DialogFactory<RenderingT>) {
    "A ${DialogFactory::class.java.name} should have been registered " +
        "to display a ${this::class}, instead found $dialogFactory."
  }

  return dialogFactory
      .buildDialog(
          this,
          initialViewEnvironment,
          context
      )
      .apply {
        check(this.getRendering<Any>() != null) {
          "Dialog.bindShowRendering should have been called for $this, typically by the " +
              "${DialogFactory::class.java.name} that created it."
        }
      }
}
