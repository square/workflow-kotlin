package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

@WorkflowUiExperimentalApi
internal class LegacyFactoryForScreenType : ViewFactory<Screen> {
  override val type = Screen::class

  override fun buildView(
    initialRendering: Screen,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    val modernFactory = initialRendering.toViewFactory(initialViewEnvironment)
    val holder = modernFactory.buildView(
      initialRendering, initialViewEnvironment, contextForNewView, container
    )
    holder.view.bindShowRendering(initialRendering, initialViewEnvironment) { r, e ->
      holder.runner.showRendering(r, e)
    }
    return holder.view
  }
}
