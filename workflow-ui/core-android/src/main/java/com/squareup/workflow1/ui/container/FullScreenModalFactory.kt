package com.squareup.workflow1.ui.container

import android.content.Context
import androidx.activity.ComponentDialog
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Default [OverlayDialogFactory] for the standard [FullScreenModal] rendering class.
 * Nothing more than a direct call to [ComponentDialog.setContent].
 *
 * To provide a custom binding for [FullScreenModal], see [OverlayDialogFactoryFinder].
 */
@WorkflowUiExperimentalApi
internal class FullScreenModalFactory<C : Screen>() : OverlayDialogFactory<FullScreenModal<C>> {
  override val type = FullScreenModal::class

  override fun buildDialog(
    initialRendering: FullScreenModal<C>,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<FullScreenModal<C>> =
    ComponentDialog(context).setContent(initialRendering, initialEnvironment)
}
